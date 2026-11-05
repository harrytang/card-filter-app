package app.cardfilter.data

import app.cardfilter.data.db.AppDatabase
import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.TopicRepository
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlTopicRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : TopicRepository {
    private val topics get() = database.topicQueries
    private val cards get() = database.cardQueries
    private val cardAttributes get() = database.cardAttributeQueries
    private val cardImages get() = database.cardImageQueries

    override fun observeAll(): Flow<List<Topic>> =
        topics.selectAll(::toTopic).asFlow().mapToList(ioDispatcher)

    override fun observe(id: TopicId): Flow<Topic?> =
        topics.selectById(id.value, ::toTopic).asFlow().mapToOneOrNull(ioDispatcher)

    override suspend fun create(name: String): Topic = withContext(ioDispatcher) {
        database.transactionWithResult {
            topics.insert(name, now())
            Topic(TopicId(topics.lastInsertId().executeAsOne()), name)
        }
    }

    override suspend fun rename(id: TopicId, name: String) {
        withContext(ioDispatcher) { topics.rename(name, id.value) }
    }

    // Children are removed explicitly, deepest first: SQLite foreign keys are off by default on
    // some drivers, so ON DELETE CASCADE cannot be relied on.
    override suspend fun delete(id: TopicId) {
        withContext(ioDispatcher) {
            database.transaction {
                cardAttributes.deleteByTopic(id.value)
                cardImages.deleteByTopic(id.value)
                cards.deleteByTopic(id.value)
                topics.delete(id.value)
            }
        }
    }

    private fun toTopic(id: Long, name: String) = Topic(TopicId(id), name)
}
