package app.cardfilter.domain.repository

import app.cardfilter.domain.model.QuickFilter
import app.cardfilter.domain.model.QuickFilterId
import app.cardfilter.domain.model.TopicId
import kotlinx.coroutines.flow.Flow

interface QuickFilterRepository {
    /** The topic's filters in the order they were created. */
    fun observeByTopic(topicId: TopicId): Flow<List<QuickFilter>>

    suspend fun create(topicId: TopicId, name: String, include: String, exclude: String): QuickFilter

    suspend fun update(id: QuickFilterId, name: String, include: String, exclude: String)

    suspend fun delete(id: QuickFilterId)
}
