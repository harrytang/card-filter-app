package app.cardfilter.presentation.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.search.CardSearch
import app.cardfilter.domain.usecase.DeleteCard
import app.cardfilter.domain.usecase.GetCardImage
import app.cardfilter.domain.usecase.ObserveCards
import app.cardfilter.domain.usecase.ObserveTopic
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

data class CardListUiState(
    val topicName: String? = null,
    /** Every card of the topic; [visibleCards] is the part of it that matches [query]. */
    val cards: List<Card> = emptyList(),
    val visibleCards: List<Card> = emptyList(),
    val query: String = "",
    /** When true, [visibleCards] are the cards that do NOT contain any word of [query]. */
    val notMode: Boolean = false,
    val isLoading: Boolean = true,
    val cardPendingDelete: Card? = null,
    val failure: Boolean = false,
)

/**
 * Lists a topic's cards, narrows them by a search query and deletes them. Creating and editing
 * happen in [CardEditorViewModel].
 */
class CardListViewModel(
    topicId: TopicId,
    observeTopic: ObserveTopic,
    observeCards: ObserveCards,
    private val deleteCard: DeleteCard,
    private val getCardImage: GetCardImage,
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
            val searches = observeCards(topicId)
                .map(::CardSearch)
                .catch { _state.update { state -> state.copy(isLoading = false, failure = true) } }
            val queries = _state.map { it.query to it.notMode }.distinctUntilChanged()
            combine(searches, queries) { search, query -> search to query }
                .collect { (search, queryAndMode) ->
                    val (query, notMode) = queryAndMode
                    _state.update {
                        it.copy(
                            cards = search.cards,
                            visibleCards = search.filter(query, exclude = notMode),
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun onNotModeChange(notMode: Boolean) = _state.update { it.copy(notMode = notMode) }

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
