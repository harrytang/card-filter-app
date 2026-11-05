package app.cardfilter.domain.search

import app.cardfilter.domain.model.Card

/**
 * Searches one topic's [cards] with two queries at once: an include query and an exclude ("not")
 * query. A word matches a card when it is part of its title or of any attribute value (never an
 * attribute name), ignoring case, Vietnamese diacritics and the hiragana/katakana difference. A
 * word made only of digits matches a whole number only, so `7` finds `7 tháng` but not `17 tháng`.
 * Each card is folded once here, not on every keystroke.
 */
internal class CardSearch(val cards: List<Card>) {
    private val searchableText: List<List<FoldedText>> = cards.map { card ->
        (listOf(card.title) + card.attributes.map { it.value }).map { FoldedText(foldForSearch(it)) }
    }

    /**
     * The cards in their original order that match any word of [include] (every card when it has
     * no usable word) and match no word of [exclude].
     */
    fun filter(include: String, exclude: String = ""): List<Card> {
        val includeWords = queryWords(include)
        val excludeWords = queryWords(exclude)
        if (includeWords.isEmpty() && excludeWords.isEmpty()) return cards
        return cards.filterIndexed { index, _ ->
            val texts = searchableText[index]
            (includeWords.isEmpty() || includeWords.any { texts.matches(it) }) &&
                excludeWords.none { texts.matches(it) }
        }
    }
}

private class FoldedText(val text: String) {
    /** The whole numbers in [text]: each maximal run of digits. */
    val numbers: Set<String> by lazy { numberRuns(text) }

    fun matches(word: String): Boolean =
        if (word.all(Char::isDigit)) word in numbers else word in text
}

private fun List<FoldedText>.matches(word: String): Boolean = any { it.matches(word) }

private fun numberRuns(text: String): Set<String> {
    val runs = mutableSetOf<String>()
    val current = StringBuilder()
    fun endRun() {
        if (current.isNotEmpty()) runs += current.toString()
        current.clear()
    }
    for (char in text) {
        if (char.isDigit()) current.append(char) else endRun()
    }
    endRun()
    return runs
}

private const val WordSeparators = ",;、，；"

private fun Char.separatesWords(): Boolean = isWhitespace() || this in WordSeparators

/**
 * Splits on any Unicode whitespace (including U+3000 and U+00A0) and on commas and semicolons
 * (ASCII and CJK), folds, drops empties and repeats.
 */
internal fun queryWords(query: String): List<String> {
    val words = mutableListOf<String>()
    val current = StringBuilder()
    fun endWord() {
        if (current.isNotEmpty()) words += foldForSearch(current.toString())
        current.clear()
    }
    for (char in query) {
        if (char.separatesWords()) endWord() else current.append(char)
    }
    endWord()
    return words.filter { it.isNotEmpty() }.distinct()
}
