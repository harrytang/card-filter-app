package app.cardfilter.domain.search

import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.TopicId
import kotlin.test.Test
import kotlin.test.assertEquals

class CardSearchTest {
    private var nextId = 1L

    private fun card(title: String, vararg attributes: Pair<String, String>) = Card(
        id = CardId(nextId++),
        topicId = TopicId(1),
        title = title,
        attributes = attributes.map { Attribute(it.first, it.second) },
    )

    private fun titlesFor(query: String, vararg cards: Card): List<String> =
        CardSearch(cards.toList()).filter(query).map { it.title }

    @Test
    fun matchesTheTitle() {
        val cháo = card("Cháo cá")
        val other = card("Súp")

        assertEquals(listOf("Cháo cá"), titlesFor("cháo", cháo, other))
    }

    @Test
    fun matchesAnAttributeValue() {
        val withEgg = card("Bánh", "allergen" to "egg, milk")
        val without = card("Cháo", "allergen" to "none")

        assertEquals(listOf("Bánh"), titlesFor("egg", withEgg, without))
    }

    @Test
    fun neverMatchesAnAttributeName() {
        val named = card("Cháo", "thành phần" to "gạo")

        assertEquals(emptyList(), titlesFor("thanh phan", named))
        assertEquals(listOf("Cháo"), titlesFor("gao", named))
    }

    @Test
    fun ignoresCase() {
        val c = card("Cháo", "note" to "EGG")

        assertEquals(listOf("Cháo"), titlesFor("egg", c))
        assertEquals(listOf("Cháo"), titlesFor("CHÁO", c))
    }

    @Test
    fun ignoresVietnameseDiacriticsInBothDirections() {
        val c = card("Cháo", "thành phần" to "thịt bầm")
        val typedWithMarks = card("Cơm", "note" to "thit bam")

        assertEquals(listOf("Cháo", "Cơm"), titlesFor("thit bam", c, typedWithMarks))
        assertEquals(listOf("Cháo", "Cơm"), titlesFor("thịt bầm", c, typedWithMarks))
    }

    @Test
    fun matchesPartOfAWord() {
        val c = card("Cháo", "note" to "trứng gà")

        assertEquals(listOf("Cháo"), titlesFor("trung", c))
        assertEquals(listOf("Cháo"), titlesFor("ga", c))
    }

    @Test
    fun severalWordsMatchWhenAnyOneIsPresent() {
        val egg = card("A", "allergen" to "egg")
        val milk = card("B", "allergen" to "milk")
        val none = card("C", "allergen" to "wheat")

        assertEquals(listOf("A", "B"), titlesFor("egg milk", egg, milk, none))
    }

    @Test
    fun aCardContainingEveryWordIsListedOnce() {
        val both = card("A", "allergen" to "egg, milk")

        assertEquals(listOf("A"), titlesFor("egg milk", both))
    }

    @Test
    fun wordsAreSplitOnIdeographicAndNoBreakSpaces() {
        val egg = card("A", "allergen" to "egg")
        val milk = card("B", "allergen" to "milk")
        val none = card("C", "allergen" to "wheat")

        assertEquals(listOf("A", "B"), titlesFor("egg\u3000milk", egg, milk, none))
        assertEquals(listOf("A", "B"), titlesFor("egg\u00A0milk", egg, milk, none))
    }

    @Test
    fun blankQueryReturnsEveryCardInTheOriginalOrder() {
        val a = card("A")
        val b = card("B")
        val c = card("C")

        assertEquals(listOf("A", "B", "C"), titlesFor("", a, b, c))
        assertEquals(listOf("A", "B", "C"), titlesFor("   ", a, b, c))
        assertEquals(listOf("A", "B", "C"), titlesFor("\u3000 \u00A0", a, b, c))
    }

    @Test
    fun aWordThatFoldsToNothingIsIgnored() {
        val a = card("A")
        val b = card("B")

        // A lone accent would otherwise be an empty needle that matches every card.
        assertEquals(listOf("A", "B"), titlesFor("\u0301", a, b))
        assertEquals(listOf("A"), titlesFor("\u0301 a", a, b))
    }

    @Test
    fun noMatchReturnsAnEmptyList() {
        assertEquals(emptyList(), titlesFor("xyz", card("A", "k" to "v")))
    }

    @Test
    fun aCardWithoutAttributesIsFoundByItsTitleOnly() {
        val bare = card("Cháo")

        assertEquals(listOf("Cháo"), titlesFor("chao", bare))
        assertEquals(emptyList(), titlesFor("gao", bare))
    }

    @Test
    fun resultsKeepTheInputOrder() {
        val z = card("Z", "k" to "egg")
        val a = card("A", "k" to "egg")
        val m = card("M", "k" to "egg")

        assertEquals(listOf("Z", "A", "M"), titlesFor("egg", z, a, m))
    }

    @Test
    fun hiraganaAndKatakanaFindEachOther() {
        val katakana = card("A", "note" to "タマゴ")
        val hiragana = card("B", "note" to "たまご")

        assertEquals(listOf("A", "B"), titlesFor("たまご", katakana, hiragana))
        assertEquals(listOf("A", "B"), titlesFor("タマゴ", katakana, hiragana))
    }

    @Test
    fun voicedAndUnvoicedKanaAreDifferentWords() {
        val voiced = card("A", "note" to "がぎ")

        assertEquals(emptyList(), titlesFor("かき", voiced))
    }

    @Test
    fun kanjiIsNotFoundByItsReading() {
        val kanji = card("A", "note" to "鶏")

        assertEquals(emptyList(), titlesFor("とり", kanji))
        assertEquals(listOf("A"), titlesFor("鶏", kanji))
    }

    @Test
    fun searchesOnlyTheCardsItWasGiven() {
        val mine = card("mine", "k" to "egg")

        assertEquals(listOf("mine"), titlesFor("egg", mine))
        assertEquals(emptyList(), CardSearch(emptyList()).filter("egg"))
    }

    private fun titlesWithout(query: String, vararg cards: Card): List<String> =
        CardSearch(cards.toList()).filter("", query).map { it.title }

    @Test
    fun exclude_hidesCardsWhoseAttributeValueContainsTheWord() {
        val withEgg = card("Bánh", "allergen" to "egg, milk")
        val without = card("Cháo", "allergen" to "none")

        assertEquals(listOf("Cháo"), titlesWithout("egg", withEgg, without))
    }

    @Test
    fun exclude_hidesCardsWhoseTitleContainsTheWord() {
        val titled = card("Egg soup")
        val other = card("Súp")

        assertEquals(listOf("Súp"), titlesWithout("egg", titled, other))
    }

    @Test
    fun exclude_hidesCardsMatchingAnyWord() {
        val egg = card("A", "k" to "egg")
        val milk = card("B", "k" to "milk")
        val rice = card("C", "k" to "rice")

        assertEquals(listOf("C"), titlesWithout("egg milk", egg, milk, rice))
    }

    @Test
    fun exclude_ignoresCaseDiacriticsAndKanaType() {
        val thit = card("A", "k" to "thịt bầm")
        val tamago = card("B", "k" to "タマゴ")
        val rice = card("C", "k" to "rice")

        assertEquals(listOf("B", "C"), titlesWithout("THIT BAM", thit, tamago, rice))
        assertEquals(listOf("A", "C"), titlesWithout("たまご", thit, tamago, rice))
    }

    @Test
    fun exclude_neverMatchesAnAttributeName() {
        val named = card("A", "egg" to "none")

        assertEquals(listOf("A"), titlesWithout("egg", named))
    }

    @Test
    fun exclude_withNoUsableWordKeepsEveryCard() {
        val a = card("A", "k" to "egg")
        val b = card("B")

        assertEquals(listOf("A", "B"), titlesWithout("", a, b))
        assertEquals(listOf("A", "B"), titlesWithout("   ", a, b))
    }

    @Test
    fun exclude_isTheExactComplementOfInclude() {
        val cards = arrayOf(card("A", "k" to "egg"), card("B", "k" to "milk"), card("C"))

        val included = titlesFor("egg", *cards)
        val excluded = titlesWithout("egg", *cards)

        assertEquals(listOf("A", "B", "C"), (included + excluded).sorted())
        assertEquals(emptyList(), included.intersect(excluded.toSet()).toList())
    }

    private fun titlesFor(include: String, exclude: String, vararg cards: Card): List<String> =
        CardSearch(cards.toList()).filter(include, exclude).map { it.title }

    @Test
    fun commasSemicolonsAndIdeographicCommasSeparateWords() {
        val egg = card("A", "k" to "egg")
        val milk = card("B", "k" to "milk")
        val rice = card("C", "k" to "rice")

        assertEquals(listOf("A", "B"), titlesFor("egg, milk", egg, milk, rice))
        assertEquals(listOf("A", "B"), titlesFor("egg;milk", egg, milk, rice))
        assertEquals(listOf("A", "B"), titlesFor("egg、milk", egg, milk, rice))
        assertEquals(listOf("C"), titlesWithout("egg, milk", egg, milk, rice))
    }

    @Test
    fun notMilkWheat7_9_12_hidesTheSevenMonthCard() {
        val seven = card("Cháo", "tuổi" to "7 tháng")
        val eight = card("Súp", "tuổi" to "8 tháng")
        val milk = card("Bánh", "allergen" to "milk")

        assertEquals(listOf("Súp"), titlesWithout("milk, wheat, 7, 9, 12", seven, eight, milk))
    }

    @Test
    fun aNumberMatchesAWholeNumberOnly() {
        val seven = card("A", "tuổi" to "7 tháng")
        val seventeen = card("B", "tuổi" to "17 tháng")
        val seventy = card("C", "tuổi" to "70")
        val range = card("D", "tuổi" to "7-9 tháng")

        assertEquals(listOf("A", "D"), titlesFor("7", seven, seventeen, seventy, range))
        assertEquals(listOf("B", "C"), titlesWithout("7", seven, seventeen, seventy, range).filter { it != "D" })
    }

    @Test
    fun aTextWordStillMatchesAsASubstring() {
        val eggs = card("A", "k" to "eggs")

        assertEquals(listOf("A"), titlesFor("egg", eggs))
    }

    @Test
    fun includeAndExcludeCombine() {
        val sevenEgg = card("A", "tuổi" to "7 tháng", "allergen" to "egg")
        val sevenPlain = card("B", "tuổi" to "7 tháng", "allergen" to "none")
        val eightPlain = card("C", "tuổi" to "8 tháng", "allergen" to "none")

        assertEquals(listOf("B"), titlesFor("7", "egg", sevenEgg, sevenPlain, eightPlain))
    }

    @Test
    fun emptyIncludeWithAnExcludeWord_showsEverythingElse() {
        val egg = card("A", "k" to "egg")
        val rice = card("B", "k" to "rice")

        assertEquals(listOf("B"), titlesFor("", "egg", egg, rice))
    }

    @Test
    fun emptyExcludeLeavesNormalSearchUnchanged() {
        val egg = card("A", "k" to "egg")
        val rice = card("B", "k" to "rice")

        assertEquals(listOf("A"), titlesFor("egg", "", egg, rice))
        assertEquals(listOf("A"), titlesFor("egg", "  ,  ", egg, rice))
    }
}
