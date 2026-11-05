package app.cardfilter.presentation.cards

import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.CardRepository
import app.cardfilter.domain.usecase.AddCard
import app.cardfilter.domain.usecase.EditCard
import app.cardfilter.domain.usecase.FetchCardImage
import app.cardfilter.domain.usecase.GetCard
import app.cardfilter.domain.usecase.GetCardImage
import app.cardfilter.domain.validation.AttributeField
import app.cardfilter.domain.validation.TitleError
import app.cardfilter.testutil.FakeCardRepository
import app.cardfilter.testutil.FakeImageDownloader
import app.cardfilter.testutil.pngBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CardEditorViewModelTest {
    private val cards = FakeCardRepository()
    private val downloader = FakeImageDownloader()
    private val topicA = TopicId(1)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        cardId: CardId? = null,
        source: CardRepository = cards,
    ) = CardEditorViewModel(
        topicId = topicA,
        cardId = cardId,
        getCard = GetCard(source),
        addCard = AddCard(source, FetchCardImage(downloader)),
        editCard = EditCard(source),
        getCardImage = GetCardImage(source),
    )

    private suspend fun seedCard(): Card =
        cards.create(topicA, "Cháo", listOf(Attribute("thành phần", "thịt bầm"), Attribute("tuổi", "7 tháng")))

    private fun CardEditorViewModel.keyOfRow(index: Int) = state.value.rows[index].key

    @Test
    fun editingACardWithAnImage_exposesItWithoutMakingTheDraftDirty() = runTest {
        val card = cards.create(topicA, "Cháo", emptyList(), byteArrayOf(7, 7))
        val viewModel = viewModel(card.id)

        assertTrue(viewModel.state.value.hasImage)
        assertContentEquals(byteArrayOf(7, 7), viewModel.loadImage())
        assertFalse(viewModel.state.value.isDirty)
    }

    @Test
    fun editingACardWithoutAnImage_hasNoImage() = runTest {
        val card = seedCard()
        val viewModel = viewModel(card.id)

        assertFalse(viewModel.state.value.hasImage)
        assertNull(viewModel.loadImage())
    }

    @Test
    fun newCard_hasNoImage() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.hasImage)
        assertNull(viewModel.loadImage())
    }

    @Test
    fun savingAnEditedCard_keepsItsImage() = runTest {
        val card = cards.create(topicA, "Cháo", listOf(Attribute("tuổi", "7 tháng")), byteArrayOf(3, 3, 3))
        val viewModel = viewModel(card.id)

        viewModel.onTitleChanged("Cháo thịt bầm")
        viewModel.onSave()

        assertTrue(viewModel.state.value.isFinished)
        assertEquals("Cháo thịt bầm", cards.get(card.id)?.title)
        assertTrue(checkNotNull(cards.get(card.id)).hasImage)
        assertContentEquals(byteArrayOf(3, 3, 3), cards.getImage(card.id))
    }

    @Test
    fun loadImage_returnsNullWithoutRaisingFailureWhenTheRepositoryThrows() = runTest {
        val card = cards.create(topicA, "Cháo", emptyList(), byteArrayOf(1))
        val failing = object : CardRepository by cards {
            override suspend fun getImage(id: CardId): ByteArray? = error("disk read failed")
        }
        val viewModel = viewModel(card.id, source = failing)

        assertNull(viewModel.loadImage())
        assertFalse(viewModel.state.value.failure, "a missing image is a normal state, not an error")
    }

    @Test
    fun newCard_startsWithAnEmptyCleanDraft() {
        val state = viewModel().state.value

        assertEquals("", state.title)
        assertTrue(state.rows.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.isDirty)
    }

    @Test
    fun editingACard_loadsItsTitleAndAttributesAsACleanDraft() = runTest {
        val card = seedCard()

        val state = viewModel(card.id).state.value

        assertEquals("Cháo", state.title)
        assertEquals(listOf("thành phần" to "thịt bầm", "tuổi" to "7 tháng"), state.rows.map { it.name to it.value })
        assertEquals(state.rows.size, state.rows.map { it.key }.toSet().size, "row keys must be unique")
        assertFalse(state.isLoading)
        assertFalse(state.isDirty)
    }

    @Test
    fun editingAnUnknownCard_closesTheEditor() {
        val state = viewModel(CardId(99)).state.value

        assertTrue(state.isFinished)
        assertFalse(state.isLoading)
    }

    @Test
    fun aFailingLoad_marksLoadFailedAndRefusesToSaveOverTheCard() = runTest {
        val card = seedCard()
        val failing = object : CardRepository by cards {
            override suspend fun get(id: CardId): Card? = error("db unreadable")
        }
        val viewModel = viewModel(card.id, failing)
        viewModel.onTitleChanged("typed over nothing")

        viewModel.onSave()

        assertTrue(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isFinished)
        assertEquals(card, cards.get(card.id), "the stored card must be untouched")
    }

    @Test
    fun addAndRemoveRows_keepKeysUniqueAndStable() {
        val viewModel = viewModel()
        viewModel.onAddRow()
        viewModel.onAddRow()
        val first = viewModel.keyOfRow(0)
        val second = viewModel.keyOfRow(1)

        viewModel.onRemoveRow(first)
        viewModel.onAddRow()

        val keys = viewModel.state.value.rows.map { it.key }
        assertEquals(2, keys.size)
        assertEquals(second, keys.first())
        assertEquals(2, keys.toSet().size)
    }

    @Test
    fun editingFields_updatesThatRowOnly() {
        val viewModel = viewModel()
        viewModel.onAddRow()
        viewModel.onAddRow()
        val key = viewModel.keyOfRow(1)

        viewModel.onTitleChanged("Cháo")
        viewModel.onNameChanged(key, "tuổi")
        viewModel.onValueChanged(key, "7 tháng")

        val state = viewModel.state.value
        assertEquals("Cháo", state.title)
        assertEquals("" to "", state.rows[0].name to state.rows[0].value)
        assertEquals("tuổi" to "7 tháng", state.rows[1].name to state.rows[1].value)
    }

    @Test
    fun isDirty_followsWhetherTheDraftDiffersFromWhatWasLoaded() = runTest {
        val card = seedCard()
        val viewModel = viewModel(card.id)

        viewModel.onTitleChanged("Changed")
        assertTrue(viewModel.state.value.isDirty)

        viewModel.onTitleChanged("Cháo")
        assertFalse(viewModel.state.value.isDirty, "restoring the original text is not a change")

        viewModel.onAddRow()
        assertTrue(viewModel.state.value.isDirty)
        viewModel.onRemoveRow(viewModel.state.value.rows.last().key)
        assertFalse(viewModel.state.value.isDirty)
    }

    @Test
    fun savingANewCard_storesTrimmedTitleAndAttributesThenFinishes() = runTest {
        val viewModel = viewModel()
        viewModel.onTitleChanged("  Cháo thịt bầm ")
        viewModel.onAddRow()
        viewModel.onNameChanged(viewModel.keyOfRow(0), " thành phần ")
        viewModel.onValueChanged(viewModel.keyOfRow(0), " thịt bầm\ncà rốt ")

        viewModel.onSave()

        val saved = cards.observeByTopic(topicA).first().single()
        assertEquals("Cháo thịt bầm", saved.title)
        assertEquals(listOf(Attribute("thành phần", "thịt bầm\ncà rốt")), saved.attributes)
        assertTrue(viewModel.state.value.isFinished)
    }

    @Test
    fun aFullyBlankRow_isIgnoredWhenSaving() = runTest {
        val viewModel = viewModel()
        viewModel.onTitleChanged("Cháo")
        viewModel.onAddRow()

        viewModel.onSave()

        assertTrue(cards.observeByTopic(topicA).first().single().attributes.isEmpty())
        assertTrue(viewModel.state.value.isFinished)
    }

    @Test
    fun invalidRows_showPerRowErrorsAndSaveNothing() = runTest {
        val viewModel = viewModel()
        viewModel.onAddRow()
        viewModel.onAddRow()
        viewModel.onNameChanged(viewModel.keyOfRow(0), "tuổi") // value missing
        viewModel.onValueChanged(viewModel.keyOfRow(1), "thịt") // name missing

        viewModel.onSave()

        val state = viewModel.state.value
        assertEquals(TitleError.Blank, state.titleError)
        assertEquals(setOf(AttributeField.Value), state.rows[0].errors)
        assertEquals(setOf(AttributeField.Name), state.rows[1].errors)
        assertTrue(cards.observeByTopic(topicA).first().isEmpty())
        assertFalse(state.isFinished)
        assertFalse(state.isSaving)
    }

    @Test
    fun typing_clearsOnlyTheErrorOfTheFieldThatChanged() = runTest {
        val viewModel = viewModel()
        viewModel.onAddRow()
        viewModel.onNameChanged(viewModel.keyOfRow(0), "tuổi")
        viewModel.onSave() // title blank + value missing
        val key = viewModel.keyOfRow(0)
        assertEquals(setOf(AttributeField.Value), viewModel.state.value.rows[0].errors)

        viewModel.onValueChanged(key, "7")

        assertEquals(emptySet(), viewModel.state.value.rows[0].errors)
        assertEquals(TitleError.Blank, viewModel.state.value.titleError)

        viewModel.onTitleChanged("Cháo")
        assertNull(viewModel.state.value.titleError)
    }

    @Test
    fun savingAnEditedCard_replacesItsAttributes() = runTest {
        val card = seedCard()
        val viewModel = viewModel(card.id)
        viewModel.onRemoveRow(viewModel.keyOfRow(0))
        viewModel.onValueChanged(viewModel.keyOfRow(0), "8 tháng")

        viewModel.onSave()

        assertEquals(
            card.copy(attributes = listOf(Attribute("tuổi", "8 tháng"))),
            cards.get(card.id),
        )
        assertTrue(viewModel.state.value.isFinished)
    }

    @Test
    fun aFailingSave_setsFailureKeepsTheDraftAndAllowsRetry() = runTest {
        val failing = object : CardRepository by cards {
            override suspend fun create(
                topicId: TopicId,
                title: String,
                attributes: List<Attribute>,
                image: ByteArray?,
            ): Card = error("disk full")
        }
        val viewModel = viewModel(source = failing)
        viewModel.onTitleChanged("Cháo")

        viewModel.onSave()

        val state = viewModel.state.value
        assertTrue(state.failure)
        assertFalse(state.isSaving)
        assertFalse(state.isFinished)
        assertEquals("Cháo", state.title)

        viewModel.onFailureShown()
        assertFalse(viewModel.state.value.failure)
    }

    @Test
    fun typingAnImageUrl_makesTheDraftDirtyAndBackAsksBeforeDiscarding() {
        val viewModel = viewModel()

        viewModel.onImageUrlChanged("https://example.com/a.png")
        viewModel.onBackClick()

        assertEquals("https://example.com/a.png", viewModel.state.value.imageUrl)
        assertTrue(viewModel.state.value.isDirty)
        assertTrue(viewModel.state.value.showDiscardConfirm)
    }

    @Test
    fun clearingTheImageUrl_makesTheDraftCleanAgain() {
        val viewModel = viewModel()

        viewModel.onImageUrlChanged("https://example.com/a.png")
        viewModel.onImageUrlChanged("")

        assertFalse(viewModel.state.value.isDirty)
    }

    @Test
    fun savingANewCardWithAnImageUrl_downloadsItAndStoresTheImage() = runTest {
        val image = pngBytes()
        downloader.respond = { image }
        val viewModel = viewModel()
        viewModel.onTitleChanged("Cháo")
        viewModel.onImageUrlChanged("https://example.com/a.png")

        viewModel.onSave()

        val card = cards.observeByTopic(topicA).first().single()
        assertTrue(card.hasImage)
        assertContentEquals(image, cards.getImage(card.id))
        assertEquals(listOf("https://example.com/a.png"), downloader.requests)
        assertTrue(viewModel.state.value.isFinished)
    }

    @Test
    fun whileTheImageDownloads_theEditorIsSavingAndASecondSaveDoesNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        downloader.respond = {
            gate.await()
            pngBytes()
        }
        val viewModel = viewModel()
        viewModel.onTitleChanged("Cháo")
        viewModel.onImageUrlChanged("https://example.com/a.png")

        viewModel.onSave()
        assertTrue(viewModel.state.value.isSaving)
        viewModel.onSave()
        gate.complete(Unit)

        assertEquals(1, downloader.requests.size)
        assertEquals(1, cards.observeByTopic(topicA).first().size)
        assertTrue(viewModel.state.value.isFinished)
    }

    @Test
    fun aFailingDownload_stillSavesTheCardWithoutAnImageAndRaisesNoFailure() = runTest {
        downloader.respond = { error("offline") }
        val viewModel = viewModel()
        viewModel.onTitleChanged("Cháo")
        viewModel.onImageUrlChanged("https://example.com/a.png")

        viewModel.onSave()

        val card = cards.observeByTopic(topicA).first().single()
        assertFalse(card.hasImage)
        assertFalse(viewModel.state.value.failure)
        assertTrue(viewModel.state.value.isFinished)
    }

    @Test
    fun aRejectedForm_doesNotDownloadAndKeepsTheTypedUrl() = runTest {
        downloader.respond = { pngBytes() }
        val viewModel = viewModel()
        viewModel.onTitleChanged("  ")
        viewModel.onImageUrlChanged("https://example.com/a.png")

        viewModel.onSave()

        assertEquals(TitleError.Blank, viewModel.state.value.titleError)
        assertEquals("https://example.com/a.png", viewModel.state.value.imageUrl)
        assertTrue(downloader.requests.isEmpty())
        assertTrue(cards.observeByTopic(topicA).first().isEmpty())
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun editingAnExistingCard_ignoresAnImageUrlAndKeepsItsImage() = runTest {
        val card = cards.create(topicA, "Cháo", emptyList(), byteArrayOf(7, 7))
        downloader.respond = { pngBytes() }
        val viewModel = viewModel(card.id)

        viewModel.onImageUrlChanged("https://example.com/other.png")
        assertEquals("", viewModel.state.value.imageUrl)
        assertFalse(viewModel.state.value.isDirty)

        viewModel.onTitleChanged("Cháo thịt bầm")
        viewModel.onSave()

        assertTrue(downloader.requests.isEmpty())
        assertTrue(cards.get(card.id)!!.hasImage)
        assertContentEquals(byteArrayOf(7, 7), cards.getImage(card.id))
    }

    @Test
    fun discardingWhileTheImageDownloads_savesNoCardEvenIfTheDownloadThenFinishes() = runTest {
        val gate = CompletableDeferred<Unit>()
        downloader.respond = {
            gate.await()
            pngBytes()
        }
        val viewModel = viewModel()
        viewModel.onTitleChanged("Cháo")
        viewModel.onImageUrlChanged("https://example.com/a.png")

        viewModel.onSave()
        viewModel.onBackClick()
        viewModel.onDiscardConfirmed()
        // The ViewModel is not cleared here (on Android it can outlive the screen), so the download
        // itself must have been cancelled by the discard.
        gate.complete(Unit)

        assertTrue(cards.observeByTopic(topicA).first().isEmpty())
        assertTrue(viewModel.state.value.isFinished)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun tappingSaveTwiceWhileSaving_createsOnlyOneCard() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = object : CardRepository by cards {
            override suspend fun create(
                topicId: TopicId,
                title: String,
                attributes: List<Attribute>,
                image: ByteArray?,
            ): Card {
                gate.await()
                return cards.create(topicId, title, attributes, image)
            }
        }
        val viewModel = viewModel(source = slow)
        viewModel.onTitleChanged("Cháo")

        viewModel.onSave()
        viewModel.onSave()
        gate.complete(Unit)

        assertEquals(1, cards.observeByTopic(topicA).first().size)
    }

    @Test
    fun back_onACleanDraftLeavesImmediately() {
        val viewModel = viewModel()

        viewModel.onBackClick()

        assertTrue(viewModel.state.value.isFinished)
    }

    @Test
    fun back_onADirtyDraftAsksBeforeDiscarding() {
        val viewModel = viewModel()
        viewModel.onTitleChanged("typed")

        viewModel.onBackClick()
        assertTrue(viewModel.state.value.showDiscardConfirm)
        assertFalse(viewModel.state.value.isFinished)

        viewModel.onDiscardCancelled()
        assertFalse(viewModel.state.value.showDiscardConfirm)
        assertEquals("typed", viewModel.state.value.title)

        viewModel.onBackClick()
        viewModel.onDiscardConfirmed()
        assertTrue(viewModel.state.value.isFinished)
    }
}
