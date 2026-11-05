package app.cardfilter.domain.search

private const val KanaOffset = 0x60

private const val FirstCombiningMark = '\u0300'
private const val LastCombiningMark = '\u036F'
private const val DWithStroke = '\u0111'
private const val FirstHiragana = '\u3041'
private const val LastHiragana = '\u3096'
private const val HiraganaIterationMark = '\u309D'
private const val HiraganaVoicedIterationMark = '\u309E'

/** Unicode canonical decomposition (NFD): `ế` becomes `e` plus two combining marks. */
internal expect fun decompose(text: String): String

/**
 * Reduces [text] to the form search compares in: lower case, Vietnamese diacritics removed,
 * hiragana turned into katakana. Kanji and everything else is left alone.
 *
 * Only the Latin combining marks (U+0300-U+036F) are dropped. The kana voicing marks
 * (U+3099, U+309A) survive on purpose, otherwise `か` would match `が`.
 */
internal fun foldForSearch(text: String): String {
    val decomposed = decompose(text.lowercase())
    return buildString(decomposed.length) {
        for (char in decomposed) {
            when (char) {
                in FirstCombiningMark..LastCombiningMark -> Unit
                DWithStroke -> append('d')
                in FirstHiragana..LastHiragana,
                in HiraganaIterationMark..HiraganaVoicedIterationMark,
                -> append(char + KanaOffset)
                else -> append(char)
            }
        }
    }
}
