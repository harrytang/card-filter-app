package app.cardfilter.domain.search

import app.cardfilter.domain.model.Card

/**
 * Searches one topic's [cards]. A card matches when any word of the query is part of its title or
 * of any attribute value (never an attribute name), ignoring case, Vietnamese diacritics and the
 * hiragana/katakana difference. Each card is folded once here, not on every keystroke.
 * With `exclude`, the exact complement is returned: the cards that match none of the words.
 */
internal class CardSearch(val cards: List<Card>) {
    private val searchableText: List<List<String>> = cards.map { card ->
        (listOf(card.title) + card.attributes.map { it.value }).map(::foldForSearch)
    }

    /**
     * The matching cards (or, when [exclude], the non-matching ones) in their original order;
     * every card when the query has no usable word.
     */
    fun filter(query: String, exclude: Boolean = false): List<Card> {
        val words = queryWords(query)
        if (words.isEmpty()) return cards
        return cards.filterIndexed { index, _ ->
            val matches = words.any { word -> searchableText[index].any { text -> word in text } }
            matches != exclude
        }
    }
}

/** Splits on any Unicode whitespace (including U+3000 and U+00A0), folds, drops empties and repeats. */
internal fun queryWords(query: String): List<String> {
    val words = mutableListOf<String>()
    val current = StringBuilder()
    fun endWord() {
        if (current.isNotEmpty()) words += foldForSearch(current.toString())
        current.clear()
    }
    for (char in query) {
        if (char.isWhitespace()) endWord() else current.append(char)
    }
    endWord()
    return words.filter { it.isNotEmpty() }.distinct()
}
