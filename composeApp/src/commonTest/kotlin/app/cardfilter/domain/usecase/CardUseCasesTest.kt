package app.cardfilter.domain.usecase

import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.image.MaxImageBytes
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.validation.AttributeError
import app.cardfilter.domain.validation.AttributeField
import app.cardfilter.domain.validation.CardSaveOutcome
import app.cardfilter.domain.validation.TitleError
import app.cardfilter.testutil.FakeCardRepository
import app.cardfilter.testutil.FakeImageDownloader
import app.cardfilter.testutil.pngBytes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardUseCasesTest {
    private val repository = FakeCardRepository()
    private val observeCards = ObserveCards(repository)
    private val getCard = GetCard(repository)
    private val getCardImage = GetCardImage(repository)
    private val downloader = FakeImageDownloader()
    private val fetchCardImage = FetchCardImage(downloader)
    private val addCard = AddCard(repository, fetchCardImage)
    private val editCard = EditCard(repository)
    private val deleteCard = DeleteCard(repository)
    private val topicA = TopicId(10)
    private val topicB = TopicId(20)

    @Test
    fun getCardImage_returnsTheStoredBytes() = runTest {
        val card = repository.create(topicA, "Cháo", emptyList(), byteArrayOf(1, 2, 3))

        assertContentEquals(byteArrayOf(1, 2, 3), getCardImage(card.id))
    }

    @Test
    fun getCardImage_returnsNullForACardWithoutAnImage() = runTest {
        val card = repository.create(topicA, "Cháo", emptyList())

        assertNull(getCardImage(card.id))
    }

    @Test
    fun getCardImage_returnsNullForAnUnknownCard() = runTest {
        assertNull(getCardImage(CardId(99)))
    }

    @Test
    fun fetchCardImage_returnsTheDownloadedImage() = runTest {
        val image = pngBytes()
        downloader.respond = { image }

        assertContentEquals(image, fetchCardImage("https://example.com/a.png"))
        assertEquals(listOf("https://example.com/a.png"), downloader.requests)
        assertEquals(MaxImageBytes, downloader.lastMaxBytes)
    }

    @Test
    fun fetchCardImage_withNoUrl_returnsNullWithoutDownloading() = runTest {
        assertNull(fetchCardImage(null))
        assertTrue(downloader.requests.isEmpty())
    }

    @Test
    fun fetchCardImage_withABlankUrl_returnsNullWithoutDownloading() = runTest {
        assertNull(fetchCardImage("   "))
        assertTrue(downloader.requests.isEmpty())
    }

    @Test
    fun fetchCardImage_withAnotherScheme_returnsNullWithoutDownloading() = runTest {
        listOf("ftp://example.com/a.png", "file:///tmp/a.png", "javascript:alert(1)", "not a url").forEach {
            assertNull(fetchCardImage(it), it)
        }
        assertTrue(downloader.requests.isEmpty())
    }

    @Test
    fun fetchCardImage_acceptsHttpAndHttpsInAnyCase() = runTest {
        downloader.respond = { pngBytes() }

        assertTrue(fetchCardImage("http://example.com/a.png") != null)
        assertTrue(fetchCardImage("HTTPS://example.com/a.png") != null)
    }

    @Test
    fun fetchCardImage_trimsTheUrlBeforeDownloading() = runTest {
        downloader.respond = { pngBytes() }

        fetchCardImage("  https://example.com/a.png \n")

        assertEquals(listOf("https://example.com/a.png"), downloader.requests)
    }

    @Test
    fun fetchCardImage_whenTheDownloadFails_returnsNull() = runTest {
        downloader.respond = { throw IllegalStateException("offline") }

        assertNull(fetchCardImage("https://example.com/a.png"))
    }

    @Test
    fun fetchCardImage_whenTheBytesAreNotAnImage_returnsNull() = runTest {
        downloader.respond = { "<html>404</html>".encodeToByteArray() }

        assertNull(fetchCardImage("https://example.com/a.png"))
    }

    @Test
    fun fetchCardImage_whenTheImageIsOverTheLimit_returnsNull() = runTest {
        downloader.respond = { pngBytes(MaxImageBytes + 1) }

        assertNull(fetchCardImage("https://example.com/a.png"))
    }

    @Test
    fun fetchCardImage_whenTheImageIsExactlyTheLimit_returnsIt() = runTest {
        val image = pngBytes(MaxImageBytes)
        downloader.respond = { image }

        assertContentEquals(image, fetchCardImage("https://example.com/a.png"))
    }

    @Test
    fun fetchCardImage_whenNothingIsDownloaded_returnsNull() = runTest {
        downloader.respond = { ByteArray(0) }

        assertNull(fetchCardImage("https://example.com/a.png"))
    }

    @Test
    fun fetchCardImage_doesNotSwallowCancellation() = runTest {
        downloader.respond = { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { fetchCardImage("https://example.com/a.png") }
    }

    @Test
    fun addCard_withAnImageUrl_storesTheDownloadedImage() = runTest {
        val image = pngBytes()
        downloader.respond = { image }

        val outcome = addCard(topicA, "Cháo", emptyList(), "https://example.com/a.png")

        val card = (outcome as CardSaveOutcome.Saved).value
        assertTrue(card.hasImage)
        assertContentEquals(image, repository.getImage(card.id))
        assertEquals(listOf("https://example.com/a.png"), downloader.requests)
    }

    @Test
    fun addCard_withoutAnImageUrl_savesWithoutDownloading() = runTest {
        val outcome = addCard(topicA, "Cháo", emptyList())

        assertFalse((outcome as CardSaveOutcome.Saved).value.hasImage)
        assertTrue(downloader.requests.isEmpty())
    }

    @Test
    fun addCard_whenTheFormIsRejected_doesNotDownloadAndSavesNothing() = runTest {
        downloader.respond = { pngBytes() }

        val outcome = addCard(topicA, " ", emptyList(), "https://example.com/a.png")

        assertTrue(outcome is CardSaveOutcome.Rejected)
        assertTrue(downloader.requests.isEmpty())
        assertTrue(observeCards(topicA).first().isEmpty())
    }

    @Test
    fun addCard_whenTheDownloadFails_stillSavesTheCardWithoutAnImage() = runTest {
        downloader.respond = { throw IllegalStateException("offline") }

        val outcome = addCard(topicA, "Cháo", listOf(Attribute("tuổi", "7 tháng")), "https://example.com/a.png")

        val card = (outcome as CardSaveOutcome.Saved).value
        assertFalse(card.hasImage)
        assertNull(repository.getImage(card.id))
        assertEquals(listOf(card), observeCards(topicA).first())
    }

    @Test
    fun addCard_whenTheBytesAreNotAnImage_savesTheCardWithoutAnImage() = runTest {
        downloader.respond = { "<html>404</html>".encodeToByteArray() }

        val outcome = addCard(topicA, "Cháo", emptyList(), "https://example.com/a.png")

        assertFalse((outcome as CardSaveOutcome.Saved).value.hasImage)
    }

    @Test
    fun addCard_whenCancelledDuringTheDownload_savesNothing() = runTest {
        downloader.respond = { throw CancellationException("left the screen") }

        assertFailsWith<CancellationException> {
            addCard(topicA, "Cháo", emptyList(), "https://example.com/a.png")
        }
        assertTrue(observeCards(topicA).first().isEmpty())
    }

    @Test
    fun addCard_trimsTitleBeforeSaving() = runTest {
        val outcome = addCard(topicA, "  Cháo thịt bầm ", emptyList())

        assertEquals(CardSaveOutcome.Saved(Card(CardId(1), topicA, "Cháo thịt bầm")), outcome)
    }

    @Test
    fun addCard_savesAttributesInOrderWithEndsTrimmed() = runTest {
        val outcome = addCard(
            topicA,
            "Cháo",
            listOf(Attribute(" thành phần ", " thịt bầm "), Attribute("tuổi", "7 tháng")),
        )

        val saved = outcome as CardSaveOutcome.Saved
        assertEquals(
            listOf(Attribute("thành phần", "thịt bầm"), Attribute("tuổi", "7 tháng")),
            saved.value.attributes,
        )
    }

    @Test
    fun addCard_keepsVietnameseJapaneseAndInnerNewlinesUnchanged() = runTest {
        val outcome = addCard(
            topicA,
            "ベビーフード",
            listOf(Attribute("アレルゲン", "卵　たまご\nthịt bầm")),
        )

        val saved = outcome as CardSaveOutcome.Saved
        assertEquals(listOf(Attribute("アレルゲン", "卵　たまご\nthịt bầm")), saved.value.attributes)
    }

    @Test
    fun addCard_allowsDuplicateAttributeNames() = runTest {
        val attributes = listOf(Attribute("thành phần", "thịt"), Attribute("thành phần", "cà rốt"))

        val outcome = addCard(topicA, "Cháo", attributes)

        assertEquals(attributes, (outcome as CardSaveOutcome.Saved).value.attributes)
    }

    @Test
    fun addCard_dropsRowsWhoseNameAndValueAreBothBlank() = runTest {
        val outcome = addCard(
            topicA,
            "Cháo",
            listOf(Attribute("", "  "), Attribute("tuổi", "7 tháng"), Attribute(" ", "")),
        )

        assertEquals(
            listOf(Attribute("tuổi", "7 tháng")),
            (outcome as CardSaveOutcome.Saved).value.attributes,
        )
    }

    @Test
    fun addCard_rejectsRowWithNameButNoValue() = runTest {
        val outcome = addCard(topicA, "Cháo", listOf(Attribute("tuổi", " ")))

        assertEquals(
            CardSaveOutcome.Rejected(null, listOf(AttributeError(0, AttributeField.Value))),
            outcome,
        )
    }

    @Test
    fun addCard_rejectsRowWithValueButNoName() = runTest {
        val outcome = addCard(topicA, "Cháo", listOf(Attribute(" ", "7 tháng")))

        assertEquals(
            CardSaveOutcome.Rejected(null, listOf(AttributeError(0, AttributeField.Name))),
            outcome,
        )
    }

    @Test
    fun addCard_reportsEveryBadRowByItsSubmittedIndex() = runTest {
        val outcome = addCard(
            topicA,
            "Cháo",
            listOf(
                Attribute("tuổi", "7 tháng"),
                Attribute("", ""), // dropped, but still occupies index 1
                Attribute("", "thịt"),
                Attribute("dị ứng", ""),
            ),
        )

        assertEquals(
            CardSaveOutcome.Rejected(
                null,
                listOf(AttributeError(2, AttributeField.Name), AttributeError(3, AttributeField.Value)),
            ),
            outcome,
        )
    }

    @Test
    fun addCard_rejectsBlankTitleAndSavesNothing() = runTest {
        val outcome = addCard(topicA, " \t ", emptyList())

        assertEquals(CardSaveOutcome.Rejected(TitleError.Blank, emptyList()), outcome)
        assertTrue(observeCards(topicA).first().isEmpty())
    }

    @Test
    fun addCard_reportsTitleAndRowErrorsTogether() = runTest {
        val outcome = addCard(topicA, "", listOf(Attribute("tuổi", "")))

        assertEquals(
            CardSaveOutcome.Rejected(TitleError.Blank, listOf(AttributeError(0, AttributeField.Value))),
            outcome,
        )
    }

    @Test
    fun addCard_withARejectedRowSavesNothing() = runTest {
        addCard(topicA, "Cháo", listOf(Attribute("tuổi", "7 tháng"), Attribute("", "thịt")))

        assertTrue(observeCards(topicA).first().isEmpty())
    }

    @Test
    fun observeCards_returnsOnlyCardsOfThatTopic() = runTest {
        addCard(topicA, "A1", emptyList())
        addCard(topicB, "B1", emptyList())
        addCard(topicA, "A2", emptyList())

        assertEquals(listOf("A1", "A2"), observeCards(topicA).first().map { it.title })
        assertEquals(listOf("B1"), observeCards(topicB).first().map { it.title })
    }

    @Test
    fun getCard_returnsTheCardOrNullWhenUnknown() = runTest {
        val saved = addCard(topicA, "Cháo", listOf(Attribute("tuổi", "7 tháng"))) as CardSaveOutcome.Saved

        assertEquals(saved.value, getCard(saved.value.id))
        assertNull(getCard(CardId(99)))
    }

    @Test
    fun editCard_trimsAndReplacesTitleAndAttributes() = runTest {
        val saved = addCard(topicA, "Old", listOf(Attribute("a", "1"), Attribute("b", "2"))) as CardSaveOutcome.Saved

        val outcome = editCard(saved.value.id, " たまご ", listOf(Attribute(" c ", " 3 ")))

        assertEquals(CardSaveOutcome.Saved(Unit), outcome)
        val card = observeCards(topicA).first().single()
        assertEquals("たまご", card.title)
        assertEquals(listOf(Attribute("c", "3")), card.attributes)
    }

    @Test
    fun editCard_canRemoveEveryAttribute() = runTest {
        val saved = addCard(topicA, "Cháo", listOf(Attribute("a", "1"))) as CardSaveOutcome.Saved

        editCard(saved.value.id, "Cháo", emptyList())

        assertTrue(observeCards(topicA).first().single().attributes.isEmpty())
    }

    @Test
    fun editCard_rejectsInvalidInputAndKeepsTheOldCard() = runTest {
        val saved = addCard(topicA, "Old", listOf(Attribute("a", "1"))) as CardSaveOutcome.Saved

        val outcome = editCard(saved.value.id, "  ", listOf(Attribute("b", "")))

        assertEquals(
            CardSaveOutcome.Rejected(TitleError.Blank, listOf(AttributeError(0, AttributeField.Value))),
            outcome,
        )
        assertEquals(saved.value, observeCards(topicA).first().single())
    }

    @Test
    fun deleteCard_removesOnlyThatCard() = runTest {
        val keep = addCard(topicA, "Keep", emptyList()) as CardSaveOutcome.Saved
        val drop = addCard(topicA, "Drop", emptyList()) as CardSaveOutcome.Saved

        deleteCard(drop.value.id)

        assertEquals(listOf(keep.value), observeCards(topicA).first())
    }
}
