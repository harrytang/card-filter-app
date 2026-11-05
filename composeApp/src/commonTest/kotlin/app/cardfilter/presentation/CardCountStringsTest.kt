package app.cardfilter.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class CardCountStringsTest {
    @Test
    fun withoutAFilter_itCountsAllCards() {
        assertEquals("12 cards", Strings.cardCount(shown = 12, total = 12))
        assertEquals("1 card", Strings.cardCount(shown = 1, total = 1))
    }

    @Test
    fun whileFiltering_itSaysHowManyOfTheTotalAreShown() {
        assertEquals("3 of 12 cards", Strings.cardCount(shown = 3, total = 12))
        assertEquals("0 of 12 cards", Strings.cardCount(shown = 0, total = 12))
        assertEquals("0 of 1 card", Strings.cardCount(shown = 0, total = 1))
    }
}
