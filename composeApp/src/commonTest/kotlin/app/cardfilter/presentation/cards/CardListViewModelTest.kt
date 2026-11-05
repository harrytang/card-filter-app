package app.cardfilter.presentation.cards

import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.CardRepository
import app.cardfilter.domain.repository.TopicRepository
import app.cardfilter.domain.usecase.DeleteCard
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
    fun notMode_showsTheCardsThatDoNotContainTheQuery() = runTest {
        cards.create(topicA, "Cháo", listOf(Attribute("allergen", "egg")))
        cards.create(topicA, "Súp", listOf(Attribute("allergen", "milk")))
        val viewModel = viewModel()
        viewModel.onQueryChange("egg")

        viewModel.onNotModeChange(true)

        val state = viewModel.state.value
        assertTrue(state.notMode)
        assertEquals(listOf("Súp"), state.visibleCards.map { it.title })
        assertEquals(2, state.cards.size)
    }

    @Test
    fun turningNotModeOff_restoresTheMatchingResults() = runTest {
        cards.create(topicA, "Cháo", listOf(Attribute("allergen", "egg")))
        cards.create(topicA, "Súp", listOf(Attribute("allergen", "milk")))
        val viewModel = viewModel()
        viewModel.onQueryChange("egg")
        viewModel.onNotModeChange(true)

        viewModel.onNotModeChange(false)

        assertEquals(listOf("Cháo"), viewModel.state.value.visibleCards.map { it.title })
    }

    @Test
    fun notMode_withAnEmptyQuery_showsEveryCard() = runTest {
        cards.create(topicA, "Cháo", listOf(Attribute("allergen", "egg")))
        cards.create(topicA, "Súp", listOf(Attribute("allergen", "milk")))
        val viewModel = viewModel()

        viewModel.onNotModeChange(true)

        assertEquals(2, viewModel.state.value.visibleCards.size)
    }

    @Test
    fun aCardCreatedInNotMode_appearsOnlyIfItLacksTheQuery() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("egg")
        viewModel.onNotModeChange(true)

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
}
