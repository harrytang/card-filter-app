package app.cardfilter.presentation.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cardfilter.domain.importing.ImportError
import app.cardfilter.domain.importing.ImportFailure
import app.cardfilter.domain.importing.ImportPlan
import app.cardfilter.domain.importing.ImportPlanResult
import app.cardfilter.domain.importing.checkImportSize
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.usecase.ImportCards
import app.cardfilter.domain.usecase.ImportSummary
import app.cardfilter.domain.usecase.PrepareCardImport
import app.cardfilter.presentation.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How many overwritten titles the confirmation lists before leaving the rest to the count. */
private const val ConfirmTitleLimit = 5

sealed interface CardImportState {
    data object Idle : CardImportState

    data object Reading : CardImportState

    /** The file is valid and would replace [overwriteCount] cards; nothing has been written. */
    data class Confirm(
        val createCount: Int,
        val overwriteCount: Int,
        val overwrittenTitles: List<String>,
    ) : CardImportState

    data class Importing(val done: Int, val total: Int) : CardImportState

    data class Done(val summary: ImportSummary) : CardImportState

    data class Failed(val error: ImportError) : CardImportState
}

/**
 * Walks one import from the chosen file to a result. The file is checked first and nothing is written
 * until every image has been fetched, so [onCancel] (or leaving) at any point before the end changes nothing.
 */
class CardImportViewModel(
    private val topicId: TopicId,
    private val prepareCardImport: PrepareCardImport,
    private val importCards: ImportCards,
) : ViewModel() {
    private val _state = MutableStateFlow<CardImportState>(CardImportState.Idle)
    val state: StateFlow<CardImportState> = _state.asStateFlow()

    private var job: Job? = null
    private var pendingPlan: ImportPlan? = null

    /** [read] returns the file's text; it may throw [ImportFailure] when it already knows why not. */
    fun onFileChosen(read: suspend () -> String) {
        if (_state.value != CardImportState.Idle) return
        _state.value = CardImportState.Reading

        job = viewModelScope.launch {
            val text = attempt { read() }.getOrElse { failure ->
                fail((failure as? ImportFailure)?.error ?: ImportError.Unreadable)
                return@launch
            }
            val prepared = attempt { prepareCardImport(topicId, text) }.getOrElse {
                fail(ImportError.Storage)
                return@launch
            }
            when (prepared) {
                is ImportPlanResult.Invalid -> fail(prepared.error)
                is ImportPlanResult.Planned -> {
                    val plan = prepared.plan
                    if (plan.overwriteCount == 0) {
                        run(plan)
                    } else {
                        pendingPlan = plan
                        _state.value = CardImportState.Confirm(
                            createCount = plan.createCount,
                            overwriteCount = plan.overwriteCount,
                            overwrittenTitles = plan.overwrittenTitles(ConfirmTitleLimit),
                        )
                    }
                }
            }
        }
    }

    /** Imports JSON the user pasted. It follows the same checks as a file, including the size limit. */
    fun onTextPasted(text: String) = onFileChosen {
        checkImportSize(text.encodeToByteArray().size.toLong())?.let { throw ImportFailure(it) }
        text
    }

    fun onConfirmOverwrite() {
        val plan = pendingPlan ?: return
        if (_state.value !is CardImportState.Confirm) return
        pendingPlan = null
        job = viewModelScope.launch { run(plan) }
    }

    /** Stops reading or importing, or declines the overwrite. Nothing has been written at any of those points. */
    fun onCancel() {
        when (_state.value) {
            CardImportState.Reading, is CardImportState.Importing, is CardImportState.Confirm -> {
                job?.cancel()
                pendingPlan = null
                _state.value = CardImportState.Idle
            }
            else -> Unit
        }
    }

    fun onDismissResult() {
        if (_state.value is CardImportState.Done || _state.value is CardImportState.Failed) {
            _state.value = CardImportState.Idle
        }
    }

    private suspend fun run(plan: ImportPlan) {
        _state.value = CardImportState.Importing(done = 0, total = plan.cards.size)
        attempt {
            importCards(topicId, plan) { done, total ->
                _state.update { if (it is CardImportState.Importing) CardImportState.Importing(done, total) else it }
            }
        }.fold(
            onSuccess = { _state.value = CardImportState.Done(it) },
            onFailure = { fail(ImportError.Storage) },
        )
    }

    private fun fail(error: ImportError) {
        _state.value = CardImportState.Failed(error)
    }
}
