package app.cardfilter.domain.usecase

import app.cardfilter.domain.model.QuickFilter
import app.cardfilter.domain.model.QuickFilterId
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.QuickFilterRepository
import app.cardfilter.domain.validation.SaveOutcome
import app.cardfilter.domain.validation.saveTrimmed
import kotlinx.coroutines.flow.Flow

class ObserveQuickFilters(private val repository: QuickFilterRepository) {
    operator fun invoke(topicId: TopicId): Flow<List<QuickFilter>> = repository.observeByTopic(topicId)
}

/** The name must not be blank; the name and both queries are trimmed, nothing else is changed. */
class SaveQuickFilter(private val repository: QuickFilterRepository) {
    suspend operator fun invoke(
        topicId: TopicId,
        name: String,
        include: String,
        exclude: String,
    ): SaveOutcome<QuickFilter> =
        saveTrimmed(name) { repository.create(topicId, it, include.trim(), exclude.trim()) }
}

class EditQuickFilter(private val repository: QuickFilterRepository) {
    suspend operator fun invoke(
        id: QuickFilterId,
        name: String,
        include: String,
        exclude: String,
    ): SaveOutcome<Unit> =
        saveTrimmed(name) { repository.update(id, it, include.trim(), exclude.trim()) }
}

class DeleteQuickFilter(private val repository: QuickFilterRepository) {
    suspend operator fun invoke(id: QuickFilterId) = repository.delete(id)
}
