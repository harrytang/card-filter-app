package app.cardfilter.domain.repository

import app.cardfilter.domain.importing.CardWrite
import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.TopicId
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun observeByTopic(topicId: TopicId): Flow<List<Card>>

    suspend fun get(id: CardId): Card?

    /** Stores [image] as given, if any; it is not checked here. */
    suspend fun create(
        topicId: TopicId,
        title: String,
        attributes: List<Attribute>,
        image: ByteArray? = null,
    ): Card

    /**
     * Stores all [writes] in [topicId] as one unit: if any of them fails, none is kept. Titles,
     * attributes and images are stored as given, not checked here. An overwrite keeps the card's id
     * and position and replaces its title and whole attribute list; it replaces the image only when
     * the write carries one.
     */
    suspend fun importCards(topicId: TopicId, writes: List<CardWrite>)

    /** The stored image bytes, or null when the card has none or does not exist. */
    suspend fun getImage(id: CardId): ByteArray?

    /** Replaces the title and the whole attribute list of the card. */
    suspend fun update(id: CardId, title: String, attributes: List<Attribute>)

    /** Marks or unmarks the card as a favorite; the title, attributes and image are untouched. */
    suspend fun setFavorite(id: CardId, favorite: Boolean)

    suspend fun delete(id: CardId)
}
