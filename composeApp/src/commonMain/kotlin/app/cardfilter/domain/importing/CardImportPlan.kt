package app.cardfilter.domain.importing

import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId

/** What an import will do with one entry, decided before anything is downloaded or written. */
sealed interface PlannedCard {
    val entry: ImportEntry

    data class New(override val entry: ImportEntry) : PlannedCard

    /** [entry] replaces the existing card [id], which keeps its place in the list. */
    data class Overwrite(val id: CardId, override val entry: ImportEntry) : PlannedCard
}

class ImportPlan(
    val cards: List<PlannedCard>,
    /** The titles the overwritten cards have now, in file order, for the confirmation. */
    private val replacedTitles: List<String>,
) {
    val createCount: Int get() = cards.count { it is PlannedCard.New }
    val overwriteCount: Int get() = replacedTitles.size

    fun overwrittenTitles(limit: Int): List<String> = replacedTitles.take(limit)
}

sealed interface ImportPlanResult {
    data class Planned(val plan: ImportPlan) : ImportPlanResult

    data class Invalid(val error: ImportError) : ImportPlanResult
}

/** One card to store. A null [image] on an [Overwrite] means "keep the image the card has". */
sealed interface CardWrite {
    val title: String
    val attributes: List<Attribute>
    val image: ByteArray?

    class Create(
        override val title: String,
        override val attributes: List<Attribute>,
        override val image: ByteArray?,
    ) : CardWrite

    class Overwrite(
        val id: CardId,
        override val title: String,
        override val attributes: List<Attribute>,
        override val image: ByteArray?,
    ) : CardWrite
}

/**
 * Matches the entries to the cards already in the topic. Two cards are the same when their titles are
 * equal after trimming and ignoring case (diacritics still count). Two entries with the same name in
 * the file are rejected rather than merged, so no entry is dropped silently. When the topic already
 * holds several cards with one title, the oldest is the one overwritten.
 */
fun planImport(existing: List<Card>, entries: List<ImportEntry>): ImportPlanResult {
    val oldestByTitle = existing
        .sortedBy { it.id.value }
        .distinctBy { it.title.matchKey() }
        .associateBy { it.title.matchKey() }

    val firstEntryByTitle = mutableMapOf<String, Int>()
    val replacedTitles = mutableListOf<String>()
    val cards = entries.mapIndexed { index, entry ->
        val key = entry.title.matchKey()
        firstEntryByTitle[key]?.let { first ->
            return ImportPlanResult.Invalid(ImportError.DuplicateName(entry = index + 1, firstEntry = first))
        }
        firstEntryByTitle[key] = index + 1

        val match = oldestByTitle[key]
        if (match == null) {
            PlannedCard.New(entry)
        } else {
            replacedTitles += match.title
            PlannedCard.Overwrite(match.id, entry)
        }
    }
    return ImportPlanResult.Planned(ImportPlan(cards, replacedTitles))
}

private fun String.matchKey() = trim().lowercase()
