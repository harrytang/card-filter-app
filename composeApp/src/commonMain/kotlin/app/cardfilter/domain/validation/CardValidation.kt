package app.cardfilter.domain.validation

import app.cardfilter.domain.model.Attribute

enum class AttributeField { Name, Value }

/** [index] is the position in the attributes exactly as submitted, blank rows included. */
data class AttributeError(val index: Int, val field: AttributeField)

sealed interface CardSaveOutcome<out T> {
    data class Saved<T>(val value: T) : CardSaveOutcome<T>

    data class Rejected(
        val titleError: TitleError?,
        val attributeErrors: List<AttributeError>,
    ) : CardSaveOutcome<Nothing>
}

internal data class ValidCard(val title: String, val attributes: List<Attribute>)

/**
 * Trims the title and every attribute name and value (only the ends: diacritics, kana, kanji and
 * inner newlines are kept as typed). A row with both name and value blank is dropped; a row with
 * only one of them blank is rejected, so half an attribute is never stored.
 */
internal fun validateCard(
    rawTitle: String,
    rawAttributes: List<Attribute>,
): CardSaveOutcome<ValidCard> {
    val title = rawTitle.trim()
    val trimmed = rawAttributes.map { Attribute(it.name.trim(), it.value.trim()) }

    val attributeErrors = trimmed.mapIndexedNotNull { index, attribute ->
        when {
            attribute.isBlank() -> null
            attribute.name.isEmpty() -> AttributeError(index, AttributeField.Name)
            attribute.value.isEmpty() -> AttributeError(index, AttributeField.Value)
            else -> null
        }
    }
    val titleError = if (title.isEmpty()) TitleError.Blank else null

    return if (titleError != null || attributeErrors.isNotEmpty()) {
        CardSaveOutcome.Rejected(titleError, attributeErrors)
    } else {
        CardSaveOutcome.Saved(ValidCard(title, trimmed.filterNot { it.isBlank() }))
    }
}

private fun Attribute.isBlank() = name.isEmpty() && value.isEmpty()

/** Validates, then runs [save] with the cleaned title and attributes, or reports what is wrong. */
internal suspend inline fun <T> saveCard(
    rawTitle: String,
    rawAttributes: List<Attribute>,
    save: suspend (title: String, attributes: List<Attribute>) -> T,
): CardSaveOutcome<T> = when (val validated = validateCard(rawTitle, rawAttributes)) {
    is CardSaveOutcome.Saved -> CardSaveOutcome.Saved(save(validated.value.title, validated.value.attributes))
    is CardSaveOutcome.Rejected -> validated
}
