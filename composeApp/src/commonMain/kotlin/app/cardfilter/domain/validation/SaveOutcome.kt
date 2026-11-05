package app.cardfilter.domain.validation

enum class TitleError { Blank }

sealed interface SaveOutcome<out T> {
    data class Saved<T>(val value: T) : SaveOutcome<T>

    data class Rejected(val reason: TitleError) : SaveOutcome<Nothing>
}

/**
 * Trims [raw] and runs [save] with the trimmed text, or rejects it when nothing is left.
 * Only whitespace is touched; the text itself (diacritics, kana, kanji) is kept as typed.
 */
internal suspend inline fun <T> saveTrimmed(
    raw: String,
    save: suspend (String) -> T,
): SaveOutcome<T> {
    val trimmed = raw.trim()
    return if (trimmed.isEmpty()) {
        SaveOutcome.Rejected(TitleError.Blank)
    } else {
        SaveOutcome.Saved(save(trimmed))
    }
}
