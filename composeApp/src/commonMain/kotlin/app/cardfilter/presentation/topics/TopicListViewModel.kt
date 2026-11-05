package app.cardfilter.presentation.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.usecase.CreateTopic
import app.cardfilter.domain.usecase.DeleteTopic
import app.cardfilter.domain.usecase.ObserveTopics
import app.cardfilter.domain.usecase.RenameTopic
import app.cardfilter.domain.validation.SaveOutcome
import app.cardfilter.domain.validation.TitleError
import app.cardfilter.presentation.attempt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TopicDialog {
    data object Create : TopicDialog

    data class Rename(val topic: Topic) : TopicDialog

    data class ConfirmDelete(val topic: Topic) : TopicDialog
}

data class TopicListUiState(
    val topics: List<Topic> = emptyList(),
    val isLoading: Boolean = true,
    val dialog: TopicDialog? = null,
    val titleError: TitleError? = null,
    val failure: Boolean = false,
)

class TopicListViewModel(
    observeTopics: ObserveTopics,
    private val createTopic: CreateTopic,
    private val renameTopic: RenameTopic,
    private val deleteTopic: DeleteTopic,
) : ViewModel() {
    private val _state = MutableStateFlow(TopicListUiState())
    val state: StateFlow<TopicListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeTopics()
                .catch { _state.update { state -> state.copy(isLoading = false, failure = true) } }
                .collect { topics -> _state.update { it.copy(topics = topics, isLoading = false) } }
        }
    }

    fun onAddClick() = openDialog(TopicDialog.Create)

    fun onRenameClick(topic: Topic) = openDialog(TopicDialog.Rename(topic))

    fun onDeleteClick(topic: Topic) = openDialog(TopicDialog.ConfirmDelete(topic))

    fun onInputChanged() = _state.update { it.copy(titleError = null) }

    fun onDialogDismiss() = _state.update { it.copy(dialog = null, titleError = null) }

    fun onFailureShown() = _state.update { it.copy(failure = false) }

    fun onConfirm(text: String) {
        when (val dialog = _state.value.dialog) {
            TopicDialog.Create -> save { createTopic(text) }
            is TopicDialog.Rename -> save { renameTopic(dialog.topic.id, text) }
            is TopicDialog.ConfirmDelete, null -> Unit
        }
    }

    fun onDeleteConfirmed() {
        val dialog = _state.value.dialog as? TopicDialog.ConfirmDelete ?: return
        viewModelScope.launch {
            attempt { deleteTopic(dialog.topic.id) }.fold(
                onSuccess = { onDialogDismiss() },
                onFailure = { _state.update { it.copy(failure = true) } },
            )
        }
    }

    private fun openDialog(dialog: TopicDialog) =
        _state.update { it.copy(dialog = dialog, titleError = null) }

    private fun save(block: suspend () -> SaveOutcome<*>) {
        viewModelScope.launch {
            attempt { block() }.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is SaveOutcome.Saved -> onDialogDismiss()
                        is SaveOutcome.Rejected -> _state.update { it.copy(titleError = outcome.reason) }
                    }
                },
                onFailure = { _state.update { it.copy(failure = true) } },
            )
        }
    }
}
