package app.cardfilter.presentation.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.QuickFilter
import app.cardfilter.domain.model.QuickFilterId
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.search.CardSearch
import app.cardfilter.domain.usecase.DeleteCard
import app.cardfilter.domain.usecase.DeleteQuickFilter
import app.cardfilter.domain.usecase.EditQuickFilter
import app.cardfilter.domain.usecase.GetCardImage
import app.cardfilter.domain.usecase.ObserveCards
import app.cardfilter.domain.usecase.ObserveQuickFilters
import app.cardfilter.domain.usecase.ObserveTopic
import app.cardfilter.domain.usecase.SetCardFavorite
import app.cardfilter.domain.search.favoritesFirst
import app.cardfilter.domain.usecase.SaveQuickFilter
import app.cardfilter.domain.validation.SaveOutcome
import app.cardfilter.domain.validation.TitleError
import app.cardfilter.presentation.attempt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface QuickFilterDialog {
    /** Creates a filter when [id] is null, otherwise edits that filter. */
    data class Editor(
        val id: QuickFilterId?,
        val name: String,
        val include: String,
        val exclude: String,
    ) : QuickFilterDialog

    data class ConfirmDelete(val filter: QuickFilter) : QuickFilterDialog
}

data class CardListUiState(
    val topicName: String? = null,
    /** Every card of the topic; [visibleCards] is the part of it that matches [query]. */
    val cards: List<Card> = emptyList(),
    val visibleCards: List<Card> = emptyList(),
    val query: String = "",
    /** [visibleCards] leave out the cards that contain any word of [notQuery]. */
    val notQuery: String = "",
    val quickFilters: List<QuickFilter> = emptyList(),
    /** Counts the times the queries were set by the app, so the search fields know to show them. */
    val queryRevision: Int = 0,
    val quickFilterDialog: QuickFilterDialog? = null,
    val nameError: TitleError? = null,
    val isLoading: Boolean = true,
    val cardPendingDelete: Card? = null,
    val failure: Boolean = false,
) {
    /** The quick filter whose two queries are exactly what the search fields hold now, if any. */
    val activeQuickFilterId: QuickFilterId? =
        if (query.isBlank() && notQuery.isBlank()) {
            null
        } else {
            quickFilters.firstOrNull { it.include == query && it.exclude == notQuery }?.id
        }
}

/**
 * Lists a topic's cards, narrows them by a search query and deletes them. Creating and editing
 * happen in [CardEditorViewModel].
 */
class CardListViewModel(
    private val topicId: TopicId,
    observeTopic: ObserveTopic,
    observeCards: ObserveCards,
    private val deleteCard: DeleteCard,
    private val getCardImage: GetCardImage,
    private val setCardFavorite: SetCardFavorite,
    observeQuickFilters: ObserveQuickFilters,
    private val saveQuickFilter: SaveQuickFilter,
    private val editQuickFilter: EditQuickFilter,
    private val deleteQuickFilter: DeleteQuickFilter,
) : ViewModel() {
    private val _state = MutableStateFlow(CardListUiState())
    val state: StateFlow<CardListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeTopic(topicId)
                .catch { _state.update { state -> state.copy(failure = true) } }
                .collect { topic -> _state.update { it.copy(topicName = topic?.name) } }
        }
        viewModelScope.launch {
            observeQuickFilters(topicId)
                .catch { _state.update { state -> state.copy(failure = true) } }
                .collect { filters -> _state.update { it.copy(quickFilters = filters) } }
        }
        viewModelScope.launch {
            val searches = observeCards(topicId)
                .map(::CardSearch)
                .catch { _state.update { state -> state.copy(isLoading = false, failure = true) } }
            val queries = _state.map { it.query to it.notQuery }.distinctUntilChanged()
            combine(searches, queries) { search, query -> search to query }
                .collect { (search, queryAndMode) ->
                    val (query, notQuery) = queryAndMode
                    _state.update {
                        it.copy(
                            cards = search.cards,
                            visibleCards = search.filter(query, notQuery).favoritesFirst(),
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun onNotQueryChange(notQuery: String) = _state.update { it.copy(notQuery = notQuery) }

    /** Applies the filter to both search fields, or clears both when it is the one already applied. */
    fun onQuickFilterClick(filter: QuickFilter) = _state.update {
        val isActive = it.activeQuickFilterId == filter.id
        it.copy(
            query = if (isActive) "" else filter.include,
            notQuery = if (isActive) "" else filter.exclude,
            queryRevision = it.queryRevision + 1,
        )
    }

    /** Opens the editor for a new filter, filled in with what the search fields hold now. */
    fun onAddQuickFilterClick() = _state.update {
        it.copy(
            quickFilterDialog = QuickFilterDialog.Editor(null, "", it.query, it.notQuery),
            nameError = null,
        )
    }

    fun onQuickFilterEditClick(filter: QuickFilter) = _state.update {
        it.copy(
            quickFilterDialog = QuickFilterDialog.Editor(filter.id, filter.name, filter.include, filter.exclude),
            nameError = null,
        )
    }

    fun onQuickFilterDeleteClick(filter: QuickFilter) = _state.update {
        it.copy(quickFilterDialog = QuickFilterDialog.ConfirmDelete(filter), nameError = null)
    }

    fun onQuickFilterNameChanged() = _state.update { it.copy(nameError = null) }

    fun onQuickFilterDialogDismiss() = _state.update { it.copy(quickFilterDialog = null, nameError = null) }

    fun onQuickFilterSave(name: String, include: String, exclude: String) {
        val editor = _state.value.quickFilterDialog as? QuickFilterDialog.Editor ?: return
        viewModelScope.launch {
            attempt {
                val id = editor.id
                if (id == null) {
                    saveQuickFilter(topicId, name, include, exclude)
                } else {
                    editQuickFilter(id, name, include, exclude)
                }
            }.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is SaveOutcome.Saved -> onQuickFilterDialogDismiss()
                        is SaveOutcome.Rejected -> _state.update { it.copy(nameError = outcome.reason) }
                    }
                },
                onFailure = { _state.update { it.copy(failure = true) } },
            )
        }
    }

    fun onQuickFilterDeleteConfirmed() {
        val dialog = _state.value.quickFilterDialog as? QuickFilterDialog.ConfirmDelete ?: return
        viewModelScope.launch {
            attempt { deleteQuickFilter(dialog.filter.id) }.fold(
                onSuccess = { onQuickFilterDialogDismiss() },
                onFailure = { _state.update { it.copy(failure = true) } },
            )
        }
    }

    /** Flips the heart; the list reorders itself when the stored card changes. */
    fun onFavoriteClick(card: Card) {
        viewModelScope.launch {
            attempt { setCardFavorite(card.id, !card.isFavorite) }
                .onFailure { _state.update { it.copy(failure = true) } }
        }
    }

    /** The card's image bytes, or null: a missing or unreadable image is a normal state, not a failure. */
    suspend fun loadImage(card: Card): ByteArray? = attempt { getCardImage(card.id) }.getOrNull()

    fun onDeleteClick(card: Card) = _state.update { it.copy(cardPendingDelete = card) }

    fun onDeleteDismiss() = _state.update { it.copy(cardPendingDelete = null) }

    fun onFailureShown() = _state.update { it.copy(failure = false) }

    fun onDeleteConfirmed() {
        val card = _state.value.cardPendingDelete ?: return
        viewModelScope.launch {
            attempt { deleteCard(card.id) }.fold(
                onSuccess = { onDeleteDismiss() },
                onFailure = { _state.update { it.copy(failure = true) } },
            )
        }
    }
}
