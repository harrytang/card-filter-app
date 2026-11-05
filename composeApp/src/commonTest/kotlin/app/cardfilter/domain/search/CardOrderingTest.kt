package app.cardfilter.domain.search

import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.TopicId
import kotlin.test.Test
import kotlin.test.assertEquals

class CardOrderingTest {
    private fun card(id: Long, title: String, favorite: Boolean = false) =
        Card(CardId(id), TopicId(1), title, isFavorite = favorite)

    @Test
    fun favoritesComeFirst_andKeepTheirOrderWithinEachGroup() {
        val cards = listOf(
            card(1, "a"),
            card(2, "b", favorite = true),
            card(3, "c"),
            card(4, "d", favorite = true),
        )

        assertEquals(listOf("b", "d", "a", "c"), cards.favoritesFirst().map { it.title })
    }

    @Test
    fun withNoFavorites_theOrderIsUnchanged() {
        val cards = listOf(card(1, "a"), card(2, "b"))

        assertEquals(cards, cards.favoritesFirst())
    }

    @Test
    fun anEmptyListStaysEmpty() {
        assertEquals(emptyList(), emptyList<Card>().favoritesFirst())
    }

    @Test
    fun filteredResultsAlsoPutFavoritesFirst() {
        val cards = listOf(
            card(1, "plain egg"),
            card(2, "other"),
            card(3, "fav egg", favorite = true),
        )

        val filtered = CardSearch(cards).filter("egg").favoritesFirst()

        assertEquals(listOf("fav egg", "plain egg"), filtered.map { it.title })
    }
}
