package app.cardfilter.domain.repository

import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.model.TopicId
import kotlinx.coroutines.flow.Flow

interface TopicRepository {
    fun observeAll(): Flow<List<Topic>>

    fun observe(id: TopicId): Flow<Topic?>

    suspend fun create(name: String): Topic

    suspend fun rename(id: TopicId, name: String)

    /** Deletes the topic together with every card it contains. */
    suspend fun delete(id: TopicId)
}
