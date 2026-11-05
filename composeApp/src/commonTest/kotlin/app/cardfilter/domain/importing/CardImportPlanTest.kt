package app.cardfilter.domain.importing

import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.TopicId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val topic = TopicId(1)

private fun existing(id: Long, title: String) = Card(CardId(id), topic, title)

private fun entry(title: String, imageUrl: String? = null, vararg attributes: Attribute) =
    ImportEntry(title, attributes.toList(), imageUrl)

private fun plan(existing: List<Card>, vararg entries: ImportEntry): ImportPlan =
    assertIs<ImportPlanResult.Planned>(planImport(existing, entries.toList())).plan

class CardImportPlanTest {
    @Test
    fun withNoExistingCards_everyEntryIsNew() {
        val result = plan(emptyList(), entry("A"), entry("B"))

        assertEquals(listOf(PlannedCard.New(entry("A")), PlannedCard.New(entry("B"))), result.cards)
        assertEquals(2, result.createCount)
        assertEquals(0, result.overwriteCount)
    }

    @Test
    fun anEntryWithAnExistingTitle_overwritesThatCard() {
        val result = plan(listOf(existing(7, "A")), entry("A"), entry("B"))

        assertEquals(
            listOf(PlannedCard.Overwrite(CardId(7), entry("A")), PlannedCard.New(entry("B"))),
            result.cards,
        )
        assertEquals(1, result.createCount)
        assertEquals(1, result.overwriteCount)
    }

    @Test
    fun matching_ignoresCaseAndSurroundingSpaces() {
        val result = plan(listOf(existing(3, "Wakodo a")), entry("WAKODO A"))

        assertEquals(PlannedCard.Overwrite(CardId(3), entry("WAKODO A")), result.cards.single())
    }

    @Test
    fun matching_ignoresCaseOfNonAsciiLetters() {
        val result = plan(listOf(existing(3, "Cá hồi")), entry("CÁ HỒI"))

        assertEquals(1, result.overwriteCount)
    }

    @Test
    fun matching_doesNotFoldDiacritics() {
        val result = plan(listOf(existing(3, "Cá")), entry("Ca"))

        assertEquals(PlannedCard.New(entry("Ca")), result.cards.single())
    }

    @Test
    fun withSeveralExistingCardsOfTheSameTitle_theOldestIsOverwritten() {
        val result = plan(listOf(existing(9, "A"), existing(4, "a"), existing(6, "A")), entry("A"))

        assertEquals(CardId(4), assertIs<PlannedCard.Overwrite>(result.cards.single()).id)
    }

    @Test
    fun anOverwrite_carriesTheAttributesAndImageUrlFromTheFile() {
        val fromFile = entry("A", "https://example.com/a.png", Attribute("month", "7"))

        val result = plan(listOf(existing(1, "A")), fromFile)

        assertEquals(fromFile, result.cards.single().entry)
    }

    @Test
    fun twoEntriesWithTheSameName_areRejectedNamingBoth() {
        val result = planImport(emptyList(), listOf(entry("A"), entry("B"), entry(" a ")))

        assertEquals(
            ImportPlanResult.Invalid(ImportError.DuplicateName(entry = 3, firstEntry = 1)),
            result,
        )
    }

    @Test
    fun theFirstInFileDuplicate_isTheOneReported() {
        val result = planImport(
            emptyList(),
            listOf(entry("A"), entry("B"), entry("B"), entry("A")),
        )

        assertEquals(
            ImportPlanResult.Invalid(ImportError.DuplicateName(entry = 3, firstEntry = 2)),
            result,
        )
    }

    @Test
    fun overwrittenTitles_areTheExistingTitlesCappedAtFive() {
        val cards = (1..7).map { existing(it.toLong(), "Card $it") }
        val entries = (1..7).map { entry("card $it") }

        val result = plan(cards, *entries.toTypedArray())

        assertEquals(7, result.overwriteCount)
        assertEquals(listOf("Card 1", "Card 2", "Card 3", "Card 4", "Card 5"), result.overwrittenTitles(limit = 5))
    }
}
