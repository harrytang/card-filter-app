package app.cardfilter.testutil

import app.cardfilter.domain.importing.CardWrite
import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.model.QuickFilter
import app.cardfilter.domain.model.QuickFilterId
import app.cardfilter.domain.repository.CardRepository
import app.cardfilter.domain.repository.QuickFilterRepository
import app.cardfilter.domain.repository.TopicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeTopicRepository : TopicRepository {
    private val topics = MutableStateFlow<List<Topic>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Topic>> = topics

    override fun observe(id: TopicId): Flow<Topic?> =
        topics.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun create(name: String): Topic {
        val topic = Topic(TopicId(nextId++), name)
        topics.update { it + topic }
        return topic
    }

    override suspend fun rename(id: TopicId, name: String) {
        topics.update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
    }

    override suspend fun delete(id: TopicId) {
        topics.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeCardRepository : CardRepository {
    private val cards = MutableStateFlow<List<Card>>(emptyList())
    private val images = mutableMapOf<CardId, ByteArray>()
    private var nextId = 1L

    override fun observeByTopic(topicId: TopicId): Flow<List<Card>> =
        cards.map { list -> list.filter { it.topicId == topicId } }

    override suspend fun get(id: CardId): Card? = cards.value.firstOrNull { it.id == id }

    override suspend fun getImage(id: CardId): ByteArray? = images[id]

    override suspend fun create(
        topicId: TopicId,
        title: String,
        attributes: List<Attribute>,
        image: ByteArray?,
    ): Card {
        val card = Card(CardId(nextId++), topicId, title, attributes, hasImage = image != null)
        if (image != null) images[card.id] = image
        cards.update { it + card }
        return card
    }

    /** Every [importCards] call, in order, so a test can tell "written once" from "never written". */
    val importCalls = mutableListOf<List<CardWrite>>()

    override suspend fun importCards(topicId: TopicId, writes: List<CardWrite>) {
        importCalls += writes
        writes.forEach { write ->
            when (write) {
                is CardWrite.Create -> create(topicId, write.title, write.attributes, write.image)
                is CardWrite.Overwrite -> {
                    update(write.id, write.title, write.attributes)
                    write.image?.let {
                        images[write.id] = it
                        cards.update { list -> list.map { c -> if (c.id == write.id) c.copy(hasImage = true) else c } }
                    }
                }
            }
        }
    }

    override suspend fun update(id: CardId, title: String, attributes: List<Attribute>) {
        cards.update { list ->
            list.map { if (it.id == id) it.copy(title = title, attributes = attributes) else it }
        }
    }

    override suspend fun setFavorite(id: CardId, favorite: Boolean) {
        cards.update { list -> list.map { if (it.id == id) it.copy(isFavorite = favorite) else it } }
    }

    override suspend fun delete(id: CardId) {
        images.remove(id)
        cards.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeQuickFilterRepository : QuickFilterRepository {
    private val filters = MutableStateFlow<List<QuickFilter>>(emptyList())
    private var nextId = 1L

    override fun observeByTopic(topicId: TopicId): Flow<List<QuickFilter>> =
        filters.map { list -> list.filter { it.topicId == topicId } }

    override suspend fun create(topicId: TopicId, name: String, include: String, exclude: String): QuickFilter {
        val filter = QuickFilter(QuickFilterId(nextId++), topicId, name, include, exclude)
        filters.update { it + filter }
        return filter
    }

    override suspend fun update(id: QuickFilterId, name: String, include: String, exclude: String) {
        filters.update { list ->
            list.map { if (it.id == id) it.copy(name = name, include = include, exclude = exclude) else it }
        }
    }

    override suspend fun delete(id: QuickFilterId) {
        filters.update { list -> list.filterNot { it.id == id } }
    }
}
