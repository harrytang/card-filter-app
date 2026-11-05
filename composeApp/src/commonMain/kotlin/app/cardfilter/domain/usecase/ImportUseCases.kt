package app.cardfilter.domain.usecase

import app.cardfilter.domain.importing.CardWrite
import app.cardfilter.domain.importing.ImportParse
import app.cardfilter.domain.importing.ImportPlan
import app.cardfilter.domain.importing.ImportPlanResult
import app.cardfilter.domain.importing.PlannedCard
import app.cardfilter.domain.importing.parseCardImport
import app.cardfilter.domain.importing.planImport
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.CardRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** How many images of one import download at once. */
private const val ImportDownloadConcurrency = 4

data class ImportSummary(
    val created: Int,
    val overwritten: Int,
    /** Entries that named an image the import could not store; their cards were saved without it. */
    val imagesFailed: Int,
)

/**
 * Reads and checks the file and works out what importing it would do to [topicId]'s cards. Nothing is
 * downloaded and nothing is written, so the result can be shown to the user before they agree.
 */
class PrepareCardImport(private val repository: CardRepository) {
    suspend operator fun invoke(topicId: TopicId, text: String): ImportPlanResult =
        when (val parsed = parseCardImport(text)) {
            is ImportParse.Invalid -> ImportPlanResult.Invalid(parsed.error)
            is ImportParse.Parsed -> planImport(repository.observeByTopic(topicId).first(), parsed.entries)
        }
}

/**
 * Carries out a [plan]: downloads every image, then writes all the cards in one repository call. A
 * failed image never fails the import (the card is saved without it, and an overwritten card keeps the
 * image it had), and cancelling before the write leaves the topic exactly as it was.
 */
class ImportCards(
    private val repository: CardRepository,
    private val fetchCardImage: FetchCardImage,
) {
    /** [onProgress] is called with `(done, total)`: once at the start and once as each entry is ready. */
    suspend operator fun invoke(
        topicId: TopicId,
        plan: ImportPlan,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportSummary {
        val total = plan.cards.size
        val permits = Semaphore(ImportDownloadConcurrency)
        val progress = Mutex()
        var done = 0
        onProgress(0, total)

        val images = coroutineScope {
            plan.cards.map { planned ->
                async {
                    val url = planned.entry.imageUrl
                    val image = if (url == null) null else permits.withPermit { fetchCardImage(url) }
                    progress.withLock { onProgress(++done, total) }
                    image
                }
            }.awaitAll()
        }

        val writes = plan.cards.zip(images) { planned, image ->
            val entry = planned.entry
            when (planned) {
                is PlannedCard.New -> CardWrite.Create(entry.title, entry.attributes, image)
                is PlannedCard.Overwrite -> CardWrite.Overwrite(planned.id, entry.title, entry.attributes, image)
            }
        }
        repository.importCards(topicId, writes)

        return ImportSummary(
            created = plan.createCount,
            overwritten = plan.overwriteCount,
            imagesFailed = plan.cards.zip(images).count { (planned, image) ->
                planned.entry.imageUrl != null && image == null
            },
        )
    }
}
