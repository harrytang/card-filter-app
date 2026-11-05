package app.cardfilter.data

import app.cardfilter.data.db.AppDatabase
import app.cardfilter.domain.importing.CardWrite
import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.CardRepository
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlCardRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : CardRepository {
    private val cards get() = database.cardQueries
    private val cardAttributes get() = database.cardAttributeQueries
    private val cardImages get() = database.cardImageQueries

    override fun observeByTopic(topicId: TopicId): Flow<List<Card>> =
        cards.selectWithAttributesByTopic(topicId.value, ::toRow)
            .asFlow()
            .mapToList(ioDispatcher)
            .map(::toCards)

    override suspend fun get(id: CardId): Card? = withContext(ioDispatcher) {
        toCards(cards.selectWithAttributesById(id.value, ::toRow).executeAsList()).singleOrNull()
    }

    override suspend fun getImage(id: CardId): ByteArray? = withContext(ioDispatcher) {
        cardImages.selectByCard(id.value).executeAsOneOrNull()
    }

    override suspend fun create(
        topicId: TopicId,
        title: String,
        attributes: List<Attribute>,
        image: ByteArray?,
    ): Card = withContext(ioDispatcher) {
        database.transactionWithResult {
            cards.insert(topicId.value, title, now())
            val id = CardId(cards.lastInsertId().executeAsOne())
            insertAttributes(id, attributes)
            if (image != null) cardImages.insert(id.value, image)
            Card(id, topicId, title, attributes, hasImage = image != null)
        }
    }

    override suspend fun importCards(topicId: TopicId, writes: List<CardWrite>) {
        withContext(ioDispatcher) {
            database.transaction {
                writes.forEach { write ->
                    when (write) {
                        is CardWrite.Create -> {
                            cards.insert(topicId.value, write.title, now())
                            val id = CardId(cards.lastInsertId().executeAsOne())
                            insertAttributes(id, write.attributes)
                            write.image?.let { cardImages.insert(id.value, it) }
                        }
                        is CardWrite.Overwrite -> {
                            cards.updateTitle(write.title, write.id.value)
                            cardAttributes.deleteByCard(write.id.value)
                            insertAttributes(write.id, write.attributes)
                            write.image?.let { cardImages.replace(write.id.value, it) }
                        }
                    }
                }
            }
        }
    }

    override suspend fun update(id: CardId, title: String, attributes: List<Attribute>) {
        withContext(ioDispatcher) {
            database.transaction {
                cards.updateTitle(title, id.value)
                cardAttributes.deleteByCard(id.value)
                insertAttributes(id, attributes)
            }
        }
    }

    // Attributes are removed explicitly: SQLite foreign keys are off by default on some drivers,
    // so ON DELETE CASCADE cannot be relied on.
    override suspend fun delete(id: CardId) {
        withContext(ioDispatcher) {
            database.transaction {
                cardAttributes.deleteByCard(id.value)
                cardImages.deleteByCard(id.value)
                cards.delete(id.value)
            }
        }
    }

    private fun insertAttributes(id: CardId, attributes: List<Attribute>) {
        attributes.forEach { cardAttributes.insert(id.value, it.name, it.value) }
    }

    /** A card joined with one of its attributes; [attribute] is null for a card with none. */
    private class Row(
        val id: CardId,
        val topicId: TopicId,
        val title: String,
        val attribute: Attribute?,
        val hasImage: Boolean,
    )

    private fun toRow(
        id: Long,
        topicId: Long,
        title: String,
        attributeName: String?,
        attributeValue: String?,
        hasImage: Boolean,
    ) = Row(
        id = CardId(id),
        topicId = TopicId(topicId),
        title = title,
        hasImage = hasImage,
        attribute = if (attributeName != null && attributeValue != null) {
            Attribute(attributeName, attributeValue)
        } else {
            null
        },
    )

    // Rows arrive ordered by card then attribute, and groupBy keeps first-seen order.
    private fun toCards(rows: List<Row>): List<Card> =
        rows.groupBy { it.id }.values.map { group ->
            val first = group.first()
            Card(first.id, first.topicId, first.title, group.mapNotNull { it.attribute }, first.hasImage)
        }
}
