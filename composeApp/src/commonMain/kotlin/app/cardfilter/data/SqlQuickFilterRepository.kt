package app.cardfilter.data

import app.cardfilter.data.db.AppDatabase
import app.cardfilter.domain.model.QuickFilter
import app.cardfilter.domain.model.QuickFilterId
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.QuickFilterRepository
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlQuickFilterRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : QuickFilterRepository {
    private val quickFilters get() = database.quickFilterQueries

    override fun observeByTopic(topicId: TopicId): Flow<List<QuickFilter>> =
        quickFilters.selectByTopic(topicId.value, ::toQuickFilter).asFlow().mapToList(ioDispatcher)

    override suspend fun create(
        topicId: TopicId,
        name: String,
        include: String,
        exclude: String,
    ): QuickFilter = withContext(ioDispatcher) {
        database.transactionWithResult {
            quickFilters.insert(topicId.value, name, include, exclude, now())
            QuickFilter(
                QuickFilterId(quickFilters.lastInsertId().executeAsOne()),
                topicId,
                name,
                include,
                exclude,
            )
        }
    }

    override suspend fun update(id: QuickFilterId, name: String, include: String, exclude: String) {
        withContext(ioDispatcher) { quickFilters.update(name, include, exclude, id.value) }
    }

    override suspend fun delete(id: QuickFilterId) {
        withContext(ioDispatcher) { quickFilters.delete(id.value) }
    }

    private fun toQuickFilter(
        id: Long,
        topicId: Long,
        name: String,
        include: String,
        exclude: String,
    ) = QuickFilter(QuickFilterId(id), TopicId(topicId), name, include, exclude)
}
