package app.cardfilter.domain.usecase

import app.cardfilter.domain.image.ImageDownloader
import app.cardfilter.domain.image.MaxImageBytes
import app.cardfilter.domain.image.looksLikeImage
import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.CardRepository
import app.cardfilter.domain.validation.CardSaveOutcome
import app.cardfilter.domain.validation.saveCard
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

class ObserveCards(private val repository: CardRepository) {
    operator fun invoke(topicId: TopicId): Flow<List<Card>> = repository.observeByTopic(topicId)
}

class GetCard(private val repository: CardRepository) {
    suspend operator fun invoke(id: CardId): Card? = repository.get(id)
}

class GetCardImage(private val repository: CardRepository) {
    suspend operator fun invoke(id: CardId): ByteArray? = repository.getImage(id)
}

/**
 * The image behind [url], or null when there is none to store: no URL, not an http(s) address, any
 * download failure, bytes that are not an image, or an image over [MaxImageBytes]. A missing image
 * is a normal outcome (the card is saved without it), so nothing here is reported as an error.
 */
class FetchCardImage(private val downloader: ImageDownloader) {
    suspend operator fun invoke(url: String?): ByteArray? {
        val address = url?.trim()
        if (address.isNullOrEmpty() || !address.isHttpUrl()) return null

        val bytes = try {
            downloader.download(address, MaxImageBytes)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return null
        }
        return bytes.takeIf { it.size in 1..MaxImageBytes && looksLikeImage(it) }
    }
}

private fun String.isHttpUrl() =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

class AddCard(
    private val repository: CardRepository,
    private val fetchCardImage: FetchCardImage,
) {
    /**
     * Saves the card, with the image behind [imageUrl] when it can be downloaded. The download
     * starts only after the form is valid, and finishes before the one database write.
     */
    suspend operator fun invoke(
        topicId: TopicId,
        title: String,
        attributes: List<Attribute>,
        imageUrl: String? = null,
    ): CardSaveOutcome<Card> =
        saveCard(title, attributes) { cleanTitle, cleanAttributes ->
            repository.create(topicId, cleanTitle, cleanAttributes, fetchCardImage(imageUrl))
        }
}

class EditCard(private val repository: CardRepository) {
    suspend operator fun invoke(
        id: CardId,
        title: String,
        attributes: List<Attribute>,
    ): CardSaveOutcome<Unit> =
        saveCard(title, attributes) { cleanTitle, cleanAttributes ->
            repository.update(id, cleanTitle, cleanAttributes)
        }
}

class DeleteCard(private val repository: CardRepository) {
    suspend operator fun invoke(id: CardId) = repository.delete(id)
}
