package app.cardfilter.presentation.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.usecase.AddCard
import app.cardfilter.domain.usecase.EditCard
import app.cardfilter.domain.usecase.GetCard
import app.cardfilter.domain.usecase.GetCardImage
import app.cardfilter.domain.validation.AttributeField
import app.cardfilter.domain.validation.CardSaveOutcome
import app.cardfilter.domain.validation.TitleError
import app.cardfilter.presentation.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable name/value pair. [key] identifies the row across edits and removals. */
data class AttributeRow(
    val key: Long,
    val name: String = "",
    val value: String = "",
    val errors: Set<AttributeField> = emptySet(),
)

data class CardEditorUiState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val title: String = "",
    /** Read-only: the card's image is shown above the form but never edited here (out of scope). */
    val hasImage: Boolean = false,
    /** Where to download the image of a new card from; always empty when editing (images are not edited). */
    val imageUrl: String = "",
    val rows: List<AttributeRow> = emptyList(),
    val titleError: TitleError? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val failure: Boolean = false,
    val isFinished: Boolean = false,
)

/** Edits one card as a draft: a new card when [cardId] is null, otherwise the stored card. */
class CardEditorViewModel(
    private val topicId: TopicId,
    private val cardId: CardId?,
    getCard: GetCard,
    private val addCard: AddCard,
    private val editCard: EditCard,
    private val getCardImage: GetCardImage,
) : ViewModel() {
    private val _state = MutableStateFlow(CardEditorUiState(isLoading = cardId != null))
    val state: StateFlow<CardEditorUiState> = _state.asStateFlow()

    private var nextKey = 0L
    private var saveJob: Job? = null
    private var baseline = Content(title = "", fields = emptyList(), imageUrl = "")

    private data class Content(val title: String, val fields: List<Pair<String, String>>, val imageUrl: String)

    private fun CardEditorUiState.content() = Content(title, rows.map { it.name to it.value }, imageUrl)

    init {
        if (cardId != null) {
            viewModelScope.launch {
                attempt { getCard(cardId) }.fold(
                    onSuccess = { card ->
                        if (card == null) {
                            onCardMissing()
                        } else {
                            onCardLoaded(card.title, card.attributes, card.hasImage)
                        }
                    },
                    onFailure = { _state.update { it.copy(isLoading = false, loadFailed = true) } },
                )
            }
        }
    }

    fun onTitleChanged(title: String) = edit { it.copy(title = title, titleError = null) }

    /** Only a new card takes an image URL: replacing the image of a stored card is out of scope. */
    fun onImageUrlChanged(url: String) {
        if (cardId == null) edit { it.copy(imageUrl = url) }
    }

    fun onAddRow() {
        val row = AttributeRow(key = nextKey++)
        edit { it.copy(rows = it.rows + row) }
    }

    fun onRemoveRow(key: Long) = edit { it.copy(rows = it.rows.filterNot { row -> row.key == key }) }

    fun onNameChanged(key: Long, name: String) = editRow(key) {
        it.copy(name = name, errors = it.errors - AttributeField.Name)
    }

    fun onValueChanged(key: Long, value: String) = editRow(key) {
        it.copy(value = value, errors = it.errors - AttributeField.Value)
    }

    fun onBackClick() = _state.update {
        if (it.isDirty) it.copy(showDiscardConfirm = true) else it.copy(isFinished = true)
    }

    fun onDiscardConfirmed() {
        // A save still waiting on the image download must not finish after the user threw the draft away.
        // Clearing the ViewModel would cancel it too, but that is not guaranteed to happen at once.
        saveJob?.cancel()
        _state.update { it.copy(showDiscardConfirm = false, isSaving = false, isFinished = true) }
    }

    fun onDiscardCancelled() = _state.update { it.copy(showDiscardConfirm = false) }

    fun onFailureShown() = _state.update { it.copy(failure = false) }

    fun onSave() {
        val draft = _state.value
        // A card that failed to load must not be saved: the blank draft would overwrite the stored one.
        if (draft.isLoading || draft.loadFailed || draft.isSaving) return
        _state.update { it.copy(isSaving = true) }

        val attributes = draft.rows.map { Attribute(it.name, it.value) }
        saveJob = viewModelScope.launch {
            attempt {
                if (cardId == null) {
                    addCard(topicId, draft.title, attributes, draft.imageUrl)
                } else {
                    editCard(cardId, draft.title, attributes)
                }
            }.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is CardSaveOutcome.Saved ->
                            _state.update { it.copy(isSaving = false, isDirty = false, isFinished = true) }
                        is CardSaveOutcome.Rejected -> _state.update { it.reject(outcome) }
                    }
                },
                onFailure = { _state.update { it.copy(isSaving = false, failure = true) } },
            )
        }
    }

    private fun CardEditorUiState.reject(outcome: CardSaveOutcome.Rejected) = copy(
        isSaving = false,
        titleError = outcome.titleError,
        rows = rows.mapIndexed { index, row ->
            row.copy(
                errors = outcome.attributeErrors
                    .filter { it.index == index }
                    .mapTo(mutableSetOf()) { it.field },
            )
        },
    )

    /** The card's image bytes, or null: a missing or unreadable image is a normal state, not a failure. */
    suspend fun loadImage(): ByteArray? =
        if (cardId == null || !_state.value.hasImage) null else attempt { getCardImage(cardId) }.getOrNull()

    private fun onCardLoaded(title: String, attributes: List<Attribute>, hasImage: Boolean) {
        val rows = attributes.map { AttributeRow(key = nextKey++, name = it.name, value = it.value) }
        val loaded = _state.value.copy(isLoading = false, title = title, hasImage = hasImage, rows = rows)
        baseline = loaded.content()
        _state.value = loaded
    }

    private fun onCardMissing() = _state.update { it.copy(isLoading = false, isFinished = true) }

    private fun editRow(key: Long, transform: (AttributeRow) -> AttributeRow) =
        edit { it.copy(rows = it.rows.map { row -> if (row.key == key) transform(row) else row }) }

    private fun edit(transform: (CardEditorUiState) -> CardEditorUiState) = _state.update { current ->
        val next = transform(current)
        next.copy(isDirty = next.content() != baseline)
    }
}
