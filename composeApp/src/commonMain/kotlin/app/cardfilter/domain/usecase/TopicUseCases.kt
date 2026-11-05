package app.cardfilter.domain.usecase

import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.TopicRepository
import app.cardfilter.domain.validation.SaveOutcome
import app.cardfilter.domain.validation.saveTrimmed
import kotlinx.coroutines.flow.Flow

class ObserveTopics(private val repository: TopicRepository) {
    operator fun invoke(): Flow<List<Topic>> = repository.observeAll()
}

class ObserveTopic(private val repository: TopicRepository) {
    operator fun invoke(id: TopicId): Flow<Topic?> = repository.observe(id)
}

class CreateTopic(private val repository: TopicRepository) {
    suspend operator fun invoke(name: String): SaveOutcome<Topic> =
        saveTrimmed(name) { repository.create(it) }
}

class RenameTopic(private val repository: TopicRepository) {
    suspend operator fun invoke(id: TopicId, name: String): SaveOutcome<Unit> =
        saveTrimmed(name) { repository.rename(id, it) }
}

class DeleteTopic(private val repository: TopicRepository) {
    suspend operator fun invoke(id: TopicId) = repository.delete(id)
}
