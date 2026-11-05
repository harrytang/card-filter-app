package app.cardfilter.presentation.cards

import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.CardRepository
import app.cardfilter.domain.repository.TopicRepository
import app.cardfilter.domain.usecase.DeleteCard
import app.cardfilter.domain.usecase.DeleteQuickFilter
import app.cardfilter.domain.usecase.EditQuickFilter
import app.cardfilter.domain.usecase.ObserveQuickFilters
import app.cardfilter.domain.usecase.SaveQuickFilter
import app.cardfilter.domain.validation.TitleError
import app.cardfilter.testutil.FakeQuickFilterRepository
import app.cardfilter.domain.usecase.GetCardImage
import app.cardfilter.domain.usecase.ObserveCards
import app.cardfilter.domain.usecase.ObserveTopic
import app.cardfilter.testutil.FakeCardRepository
import app.cardfilter.testutil.FakeTopicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CardListViewModelTest {
    private val topics = FakeTopicRepository()
    private val cards = FakeCardRepository()
    private val quickFilters = FakeQuickFilterRepository()
    private val topicA = TopicId(1)
    private val topicB = TopicId(2)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        topicId: TopicId = topicA,
        source: CardRepository = cards,
    ) = CardListViewModel(
        topicId = topicId,
        observeTopic = ObserveTopic(topics),
        observeCards = ObserveCards(source),
        deleteCard = DeleteCard(source),
        getCardImage = GetCardImage(source),
        observeQuickFilters = ObserveQuickFilters(quickFilters),
        saveQuickFilter = SaveQuickFilter(quickFilters),
        editQuickFilter = EditQuickFilter(quickFilters),
        deleteQuickFilter = DeleteQuickFilter(quickFilters),
    )

    @Test
    fun showsTheTopicNameAndOnlyThatTopicsCards() = runTest {
        val topic = topics.create("Pigeon")
        cards.create(topic.id, "mine", emptyList())
        cards.create(TopicId(topic.id.value + 1), "not mine", emptyList())

        val state = viewModel(topic.id).state.value

        assertEquals("Pigeon", state.topicName)
        assertEquals(listOf("mine"), state.cards.map { it.title })
        assertEquals(false, state.isLoading)
    }

    @Test
    fun cardsReportWhetherTheyHaveAnImage() = runTest {
        cards.create(topicA, "with", emptyList(), byteArrayOf(1))
        cards.create(topicA, "without", emptyList())

        assertEquals(listOf(true, false), viewModel().state.value.cards.map { it.hasImage })
    }

    @Test
    fun loadImage_returnsTheStoredBytes() = runTest {
        val card = cards.create(topicA, "Cháo", emptyList(), byteArrayOf(4, 5, 6))
        val viewModel = viewModel()

        assertContentEquals(byteArrayOf(4, 5, 6), viewModel.loadImage(card))
    }

    @Test
    fun loadImage_returnsNullForACardWithoutAnImage() = runTest {
        val card = cards.create(topicA, "Cháo", emptyList())

        assertNull(viewModel().loadImage(card))
    }

    @Test
    fun loadImage_returnsNullWithoutRaisingFailureWhenTheRepositoryThrows() = runTest {
        val card = cards.create(topicA, "Cháo", emptyList(), byteArrayOf(1))
        val failing = object : CardRepository by cards {
            override suspend fun getImage(id: CardId): ByteArray? = error("disk read failed")
        }
        val viewModel = viewModel(source = failing)

        assertNull(viewModel.loadImage(card))
        assertEquals(false, viewModel.state.value.failure, "a missing image is a normal state, not an error")
    }

    @Test
    fun cardsCarryTheirAttributesIntoTheState() = runTest {
        val attributes = listOf(Attribute("tuổi", "7 tháng"))
        cards.create(topicA, "Cháo", attributes)

        assertEquals(attributes, viewModel().state.value.cards.single().attributes)
    }

    @Test
    fun deletingACard_asksForConfirmationThenRemovesIt() = runTest {
        val card = cards.create(topicA, "Drop", emptyList())
        val viewModel = viewModel()

        viewModel.onDeleteClick(card)
        assertEquals(card, viewModel.state.value.cardPendingDelete)

        viewModel.onDeleteConfirmed()

        assertTrue(viewModel.state.value.cards.isEmpty())
        assertNull(viewModel.state.value.cardPendingDelete)
    }

    @Test
    fun dismissingTheDeleteConfirmation_keepsTheCard() = runTest {
        val card = cards.create(topicA, "Keep", emptyList())
        val viewModel = viewModel()
        viewModel.onDeleteClick(card)

        viewModel.onDeleteDismiss()

        assertNull(viewModel.state.value.cardPendingDelete)
        assertEquals(listOf(card), viewModel.state.value.cards)
    }

    @Test
    fun aFailingDelete_setsFailureAndKeepsTheConfirmationOpen() = runTest {
        val card = cards.create(topicA, "Keep", emptyList())
        val failing = object : CardRepository by cards {
            override suspend fun delete(id: CardId) {
                error("database locked")
            }
        }
        val viewModel = viewModel(source = failing)
        viewModel.onDeleteClick(card)

        viewModel.onDeleteConfirmed()

        assertTrue(viewModel.state.value.failure)
        assertEquals(card, viewModel.state.value.cardPendingDelete)
        assertEquals(listOf(card), viewModel.state.value.cards)

        viewModel.onFailureShown()
        assertEquals(false, viewModel.state.value.failure)
    }

    @Test
    fun confirmingWithoutAPendingDelete_doesNothing() = runTest {
        val card = cards.create(topicA, "Real", emptyList())
        val viewModel = viewModel()

        viewModel.onDeleteConfirmed()

        assertEquals(listOf(card), viewModel.state.value.cards)
    }

    @Test
    fun aFailingCardList_endsLoadingAndSetsFailure() {
        val failing = object : CardRepository by cards {
            override fun observeByTopic(topicId: TopicId): Flow<List<Card>> =
                flow { error("db unreadable") }
        }

        val state = viewModel(source = failing).state.value

        assertEquals(false, state.isLoading)
        assertTrue(state.failure)
    }

    @Test
    fun aFailingTopicLookup_setsFailure() {
        val failingTopics = object : TopicRepository by topics {
            override fun observe(id: TopicId): Flow<Topic?> = flow { error("db unreadable") }
        }
        val viewModel = CardListViewModel(
            topicId = topicA,
            observeTopic = ObserveTopic(failingTopics),
            observeCards = ObserveCards(cards),
            deleteCard = DeleteCard(cards),
            getCardImage = GetCardImage(cards),
            observeQuickFilters = ObserveQuickFilters(quickFilters),
            saveQuickFilter = SaveQuickFilter(quickFilters),
            editQuickFilter = EditQuickFilter(quickFilters),
            deleteQuickFilter = DeleteQuickFilter(quickFilters),
        )

        assertTrue(viewModel.state.value.failure)
    }

    @Test
    fun cardsStayScopedWhenAnotherTopicGainsCards() = runTest {
        val viewModel = viewModel(topicA)
        cards.create(topicB, "other topic", emptyList())

        assertTrue(viewModel.state.value.cards.isEmpty())
    }

    @Test
    fun withoutAQuery_everyCardIsVisible() = runTest {
        cards.create(topicA, "A", emptyList())
        cards.create(topicA, "B", emptyList())

        val state = viewModel().state.value

        assertEquals("", state.query)
        assertEquals(state.cards, state.visibleCards)
        assertEquals(2, state.visibleCards.size)
    }

    @Test
    fun aQueryNarrowsTheVisibleCards_whileCardsKeepsTheWholeTopic() = runTest {
        cards.create(topicA, "Cháo", listOf(Attribute("allergen", "egg")))
        cards.create(topicA, "Súp", listOf(Attribute("allergen", "milk")))
        val viewModel = viewModel()

        viewModel.onQueryChange("egg")

        val state = viewModel.state.value
        assertEquals("egg", state.query)
        assertEquals(listOf("Cháo"), state.visibleCards.map { it.title })
        assertEquals(listOf("Cháo", "Súp"), state.cards.map { it.title })
    }

    @Test
    fun clearingTheQuery_restoresEveryCard() = runTest {
        cards.create(topicA, "Cháo", listOf(Attribute("allergen", "egg")))
        cards.create(topicA, "Súp", listOf(Attribute("allergen", "milk")))
        val viewModel = viewModel()
        viewModel.onQueryChange("egg")

        viewModel.onQueryChange("")

        assertEquals(2, viewModel.state.value.visibleCards.size)
        assertEquals("", viewModel.state.value.query)
    }

    @Test
    fun theQueryIgnoresCaseDiacriticsAndKanaType() = runTest {
        cards.create(topicA, "Cháo", listOf(Attribute("thành phần", "thịt bầm")))
        cards.create(topicA, "Bánh", listOf(Attribute("note", "タマゴ")))
        val viewModel = viewModel()

        viewModel.onQueryChange("THIT BAM")
        assertEquals(listOf("Cháo"), viewModel.state.value.visibleCards.map { it.title })

        viewModel.onQueryChange("たまご")
        assertEquals(listOf("Bánh"), viewModel.state.value.visibleCards.map { it.title })
    }

    @Test
    fun theNotQueryHidesCardsThatContainIt_whileCardsKeepsTheWholeTopic() = runTest {
        cards.create(topicA, "Cháo", listOf(Attribute("allergen", "egg")))
        cards.create(topicA, "Súp", listOf(Attribute("allergen", "milk")))
        val viewModel = viewModel()

        viewModel.onNotQueryChange("egg")

        val state = viewModel.state.value
        assertEquals("egg", state.notQuery)
        assertEquals(listOf("Súp"), state.visibleCards.map { it.title })
        assertEquals(2, state.cards.size)
    }

    @Test
    fun includeAndNotQueriesApplyTogether() = runTest {
        cards.create(topicA, "A", listOf(Attribute("tuổi", "7 tháng"), Attribute("allergen", "egg")))
        cards.create(topicA, "B", listOf(Attribute("tuổi", "7 tháng"), Attribute("allergen", "none")))
        cards.create(topicA, "C", listOf(Attribute("tuổi", "8 tháng"), Attribute("allergen", "none")))
        val viewModel = viewModel()

        viewModel.onQueryChange("7")
        viewModel.onNotQueryChange("egg")

        assertEquals(listOf("B"), viewModel.state.value.visibleCards.map { it.title })
    }

    @Test
    fun clearingTheNotQuery_restoresTheMatchingResults() = runTest {
        cards.create(topicA, "Cháo", listOf(Attribute("allergen", "egg")))
        cards.create(topicA, "Súp", listOf(Attribute("allergen", "milk")))
        val viewModel = viewModel()
        viewModel.onNotQueryChange("egg")

        viewModel.onNotQueryChange("")

        assertEquals(2, viewModel.state.value.visibleCards.size)
    }

    @Test
    fun aCardCreatedWithANotQuery_appearsOnlyIfItLacksTheQuery() = runTest {
        val viewModel = viewModel()
        viewModel.onNotQueryChange("egg")

        cards.create(topicA, "Has", listOf(Attribute("allergen", "egg")))
        cards.create(topicA, "Lacks", listOf(Attribute("allergen", "milk")))

        assertEquals(listOf("Lacks"), viewModel.state.value.visibleCards.map { it.title })
    }

    @Test
    fun aCardCreatedWhileSearching_appearsOnlyIfItMatches() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("egg")

        cards.create(topicA, "Match", listOf(Attribute("allergen", "egg")))
        cards.create(topicA, "Other", listOf(Attribute("allergen", "milk")))

        assertEquals(listOf("Match"), viewModel.state.value.visibleCards.map { it.title })
        assertEquals(2, viewModel.state.value.cards.size)
    }

    @Test
    fun aCardEditedSoItNoLongerMatches_dropsOutOfTheResults() = runTest {
        val card = cards.create(topicA, "Cháo", listOf(Attribute("allergen", "egg")))
        val viewModel = viewModel()
        viewModel.onQueryChange("egg")
        assertEquals(1, viewModel.state.value.visibleCards.size)

        cards.update(card.id, "Cháo", listOf(Attribute("allergen", "rice")))

        assertTrue(viewModel.state.value.visibleCards.isEmpty())
        assertEquals(1, viewModel.state.value.cards.size)
    }

    @Test
    fun cardsOfAnotherTopicNeverAppear_evenWhenTheyMatch() = runTest {
        cards.create(topicA, "mine", listOf(Attribute("k", "egg")))
        cards.create(topicB, "theirs", listOf(Attribute("k", "egg")))
        val viewModel = viewModel(topicA)

        viewModel.onQueryChange("egg")

        assertEquals(listOf("mine"), viewModel.state.value.visibleCards.map { it.title })
    }

    @Test
    fun aCardPickedFromTheFilteredList_canBeDeleted() = runTest {
        cards.create(topicA, "Keep", listOf(Attribute("k", "milk")))
        val target = cards.create(topicA, "Drop", listOf(Attribute("k", "egg")))
        val viewModel = viewModel()
        viewModel.onQueryChange("egg")

        viewModel.onDeleteClick(viewModel.state.value.visibleCards.single())
        viewModel.onDeleteConfirmed()

        assertEquals(listOf("Keep"), viewModel.state.value.cards.map { it.title })
        assertTrue(viewModel.state.value.visibleCards.isEmpty())
        assertNull(cards.get(target.id))
    }

    @Test
    fun typingBeforeTheFirstCardsArrive_doesNotCrashAndIsRemembered() = runTest {
        val neverEmits = object : CardRepository by cards {
            override fun observeByTopic(topicId: TopicId): Flow<List<Card>> =
                flow { awaitCancellation() }
        }
        val viewModel = viewModel(source = neverEmits)

        viewModel.onQueryChange("egg")

        assertEquals(true, viewModel.state.value.isLoading)
        assertEquals("egg", viewModel.state.value.query)
    }

    @Test
    fun theQueryIsKeptWhenTheCardListChanges() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("egg")

        cards.create(topicA, "Other", listOf(Attribute("k", "milk")))

        assertEquals("egg", viewModel.state.value.query)
    }

    // ---- quick filters

    private suspend fun savedFilter(name: String, include: String, exclude: String, topic: TopicId = topicA) =
        quickFilters.create(topic, name, include, exclude)

    @Test
    fun showsOnlyThisTopicsQuickFilters() = runTest {
        savedFilter("mine", "7", "egg")
        savedFilter("theirs", "8", "milk", topicB)

        assertEquals(listOf("mine"), viewModel().state.value.quickFilters.map { it.name })
    }

    @Test
    fun tappingAQuickFilter_appliesItsIncludeAndNotQueriesTogether() = runTest {
        cards.create(topicA, "A", listOf(Attribute("tuổi", "7 tháng"), Attribute("allergen", "egg")))
        cards.create(topicA, "B", listOf(Attribute("tuổi", "7 tháng"), Attribute("allergen", "none")))
        cards.create(topicA, "C", listOf(Attribute("tuổi", "8 tháng"), Attribute("allergen", "none")))
        val filter = savedFilter("Safe 7m", "7", "egg")
        val viewModel = viewModel()

        viewModel.onQuickFilterClick(filter)

        val state = viewModel.state.value
        assertEquals("7", state.query)
        assertEquals("egg", state.notQuery)
        assertEquals(filter.id, state.activeQuickFilterId)
        assertEquals(listOf("B"), state.visibleCards.map { it.title })
    }

    @Test
    fun tappingAQuickFilterReplacesWhatWasTyped() = runTest {
        val filter = savedFilter("F", "7", "egg")
        val viewModel = viewModel()
        viewModel.onQueryChange("something else")
        viewModel.onNotQueryChange("other")

        viewModel.onQuickFilterClick(filter)

        assertEquals("7", viewModel.state.value.query)
        assertEquals("egg", viewModel.state.value.notQuery)
    }

    @Test
    fun tappingTheActiveQuickFilterAgain_clearsBothQueries() = runTest {
        cards.create(topicA, "A", listOf(Attribute("k", "egg")))
        cards.create(topicA, "B", listOf(Attribute("k", "rice")))
        val filter = savedFilter("F", "rice", "egg")
        val viewModel = viewModel()
        viewModel.onQuickFilterClick(filter)

        viewModel.onQuickFilterClick(filter)

        val state = viewModel.state.value
        assertEquals("", state.query)
        assertEquals("", state.notQuery)
        assertNull(state.activeQuickFilterId)
        assertEquals(2, state.visibleCards.size)
    }

    @Test
    fun editingAQueryByHand_deselectsTheQuickFilter() = runTest {
        val filter = savedFilter("F", "7", "egg")
        val viewModel = viewModel()
        viewModel.onQuickFilterClick(filter)

        viewModel.onNotQueryChange("egg, milk")

        assertNull(viewModel.state.value.activeQuickFilterId)
    }

    @Test
    fun noQuickFilterIsActiveWhileBothQueriesAreEmpty() = runTest {
        savedFilter("Empty", "", "")

        assertNull(viewModel().state.value.activeQuickFilterId)
    }

    @Test
    fun applyingAQuickFilter_bumpsTheQueryRevisionButTypingDoesNot() = runTest {
        val filter = savedFilter("F", "7", "")
        val viewModel = viewModel()
        val start = viewModel.state.value.queryRevision

        viewModel.onQueryChange("typed")
        assertEquals(start, viewModel.state.value.queryRevision)

        viewModel.onQuickFilterClick(filter)
        assertEquals(start + 1, viewModel.state.value.queryRevision)
    }

    @Test
    fun saveCurrentAsFilter_opensTheEditorPrefilledWithTheCurrentQueries() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("7")
        viewModel.onNotQueryChange("egg")

        viewModel.onAddQuickFilterClick()

        assertEquals(QuickFilterDialog.Editor(null, "", "7", "egg"), viewModel.state.value.quickFilterDialog)
    }

    @Test
    fun savingANewQuickFilter_storesItAndClosesTheEditor() = runTest {
        val viewModel = viewModel()
        viewModel.onAddQuickFilterClick()

        viewModel.onQuickFilterSave("  Safe 7m ", "7", "egg")

        val state = viewModel.state.value
        assertEquals(listOf("Safe 7m"), state.quickFilters.map { it.name })
        assertEquals("7" to "egg", state.quickFilters.single().let { it.include to it.exclude })
        assertNull(state.quickFilterDialog)
        assertNull(state.nameError)
    }

    @Test
    fun savingANewQuickFilter_doesNotChangeTheCurrentSearch() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("typed")
        viewModel.onAddQuickFilterClick()

        viewModel.onQuickFilterSave("F", "7", "egg")

        assertEquals("typed", viewModel.state.value.query)
    }

    @Test
    fun aBlankName_keepsTheEditorOpenWithAnError() = runTest {
        val viewModel = viewModel()
        viewModel.onAddQuickFilterClick()

        viewModel.onQuickFilterSave("   ", "7", "egg")

        val state = viewModel.state.value
        assertEquals(TitleError.Blank, state.nameError)
        assertTrue(state.quickFilters.isEmpty())
        assertTrue(state.quickFilterDialog is QuickFilterDialog.Editor)
    }

    @Test
    fun editingAQuickFilter_opensTheEditorWithItsValuesAndSavesTheChange() = runTest {
        val filter = savedFilter("Old", "7", "egg")
        val viewModel = viewModel()

        viewModel.onQuickFilterEditClick(filter)
        assertEquals(QuickFilterDialog.Editor(filter.id, "Old", "7", "egg"), viewModel.state.value.quickFilterDialog)

        viewModel.onQuickFilterSave("New", "8", "milk")

        val saved = viewModel.state.value.quickFilters.single()
        assertEquals(Triple("New", "8", "milk"), Triple(saved.name, saved.include, saved.exclude))
        assertNull(viewModel.state.value.quickFilterDialog)
    }

    @Test
    fun deletingAQuickFilter_asksFirstThenRemovesIt() = runTest {
        val filter = savedFilter("F", "7", "egg")
        val viewModel = viewModel()

        viewModel.onQuickFilterDeleteClick(filter)
        assertEquals(QuickFilterDialog.ConfirmDelete(filter), viewModel.state.value.quickFilterDialog)
        assertEquals(1, viewModel.state.value.quickFilters.size)

        viewModel.onQuickFilterDeleteConfirmed()

        assertTrue(viewModel.state.value.quickFilters.isEmpty())
        assertNull(viewModel.state.value.quickFilterDialog)
    }

    @Test
    fun dismissingTheQuickFilterDialog_closesItAndClearsTheError() = runTest {
        val viewModel = viewModel()
        viewModel.onAddQuickFilterClick()
        viewModel.onQuickFilterSave("", "7", "")

        viewModel.onQuickFilterDialogDismiss()

        assertNull(viewModel.state.value.quickFilterDialog)
        assertNull(viewModel.state.value.nameError)
    }
}
