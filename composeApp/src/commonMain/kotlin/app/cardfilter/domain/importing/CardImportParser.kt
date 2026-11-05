package app.cardfilter.domain.importing

import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.validation.CardSaveOutcome
import app.cardfilter.domain.validation.validateCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Bounds memory and total download time: every image of an import is held until the one write. */
const val MaxImportFileBytes = 1_000_000
const val MaxImportCards = 100

/** One card as the file describes it, already trimmed. [imageUrl] is null when the entry has none. */
data class ImportEntry(
    val title: String,
    val attributes: List<Attribute>,
    val imageUrl: String?,
)

/** The JSON keys the import reads; any other key is ignored. */
enum class ImportField(val key: String) {
    Name("name"),
    Image("image"),
    Ingredients("ingredients"),
    Month("month"),
}

/** Why an import stopped. Entry numbers are 1-based, as the user counts them in the file. */
sealed interface ImportError {
    data object NotJson : ImportError

    data object NotAnArray : ImportError

    data object Empty : ImportError

    data class TooMany(val count: Int) : ImportError

    data class EntryNotObject(val entry: Int) : ImportError

    data class MissingName(val entry: Int) : ImportError

    data class WrongType(val entry: Int, val field: ImportField) : ImportError

    /** [entry] has the same name as the earlier [firstEntry]. */
    data class DuplicateName(val entry: Int, val firstEntry: Int) : ImportError

    data object FileTooLarge : ImportError

    data object Unreadable : ImportError

    data object Storage : ImportError
}

/** Thrown by whatever reads the file when it already knows why the import cannot go on (e.g. too large). */
class ImportFailure(val error: ImportError) : Exception()

sealed interface ImportParse {
    data class Parsed(val entries: List<ImportEntry>) : ImportParse

    data class Invalid(val error: ImportError) : ImportParse
}

fun checkImportSize(byteCount: Long): ImportError? =
    if (byteCount > MaxImportFileBytes) ImportError.FileTooLarge else null

/**
 * Reads the whole file or reports the first problem, so a bad file never produces some of its cards.
 * Only `name` is required; `image`, `ingredients` and `month` are optional and a `null` or blank value
 * counts as absent. A present value of the wrong type is a problem rather than being guessed at.
 */
fun parseCardImport(text: String): ImportParse {
    val root = try {
        Json.parseToJsonElement(text.removePrefix("\uFEFF"))
    } catch (_: IllegalArgumentException) {
        return ImportParse.Invalid(ImportError.NotJson)
    }
    if (root !is JsonArray) return ImportParse.Invalid(ImportError.NotAnArray)
    if (root.isEmpty()) return ImportParse.Invalid(ImportError.Empty)
    if (root.size > MaxImportCards) return ImportParse.Invalid(ImportError.TooMany(root.size))

    val entries = root.mapIndexed { index, element ->
        when (val result = parseEntry(index + 1, element)) {
            is EntryResult.Ok -> result.entry
            is EntryResult.Bad -> return ImportParse.Invalid(result.error)
        }
    }
    return ImportParse.Parsed(entries)
}

private sealed interface EntryResult {
    data class Ok(val entry: ImportEntry) : EntryResult

    data class Bad(val error: ImportError) : EntryResult
}

private sealed interface FieldResult<out T> {
    data class Ok<T>(val value: T) : FieldResult<T>

    data object Wrong : FieldResult<Nothing>
}

private fun parseEntry(number: Int, element: JsonElement): EntryResult {
    if (element !is JsonObject) return EntryResult.Bad(ImportError.EntryNotObject(number))

    fun wrong(field: ImportField) = EntryResult.Bad(ImportError.WrongType(number, field))

    // The name is checked first so a nameless entry is always reported as that, whatever else is wrong.
    val name = (element[ImportField.Name.key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
    if (name.isBlank()) return EntryResult.Bad(ImportError.MissingName(number))

    val image = when (val field = element.optionalText(ImportField.Image)) {
        is FieldResult.Ok -> field.value
        FieldResult.Wrong -> return wrong(ImportField.Image)
    }
    val ingredients = when (val field = element.ingredients()) {
        is FieldResult.Ok -> field.value
        FieldResult.Wrong -> return wrong(ImportField.Ingredients)
    }
    val month = when (val field = element.month()) {
        is FieldResult.Ok -> field.value
        FieldResult.Wrong -> return wrong(ImportField.Month)
    }

    val attributes = listOfNotNull(
        ingredients?.let { Attribute(ImportField.Ingredients.key, it) },
        month?.let { Attribute(ImportField.Month.key, it) },
    )
    return when (val validated = validateCard(name, attributes)) {
        is CardSaveOutcome.Saved ->
            EntryResult.Ok(ImportEntry(validated.value.title, validated.value.attributes, image))
        is CardSaveOutcome.Rejected -> EntryResult.Bad(ImportError.MissingName(number))
    }
}

private fun String.orAbsent(): String? = trim().ifEmpty { null }

private fun JsonObject.optionalText(field: ImportField): FieldResult<String?> =
    when (val value = this[field.key]) {
        null, JsonNull -> FieldResult.Ok(null)
        is JsonPrimitive -> if (value.isString) FieldResult.Ok(value.content.orAbsent()) else FieldResult.Wrong
        else -> FieldResult.Wrong
    }

/** A string, or a list of strings joined with commas. */
private fun JsonObject.ingredients(): FieldResult<String?> =
    when (val value = this[ImportField.Ingredients.key]) {
        null, JsonNull -> FieldResult.Ok(null)
        is JsonPrimitive -> if (value.isString) FieldResult.Ok(value.content.orAbsent()) else FieldResult.Wrong
        is JsonArray ->
            if (value.all { it is JsonPrimitive && it.isString }) {
                val parts = value.mapNotNull { (it as JsonPrimitive).content.orAbsent() }
                FieldResult.Ok(parts.joinToString(", ").orAbsent())
            } else {
                FieldResult.Wrong
            }
        else -> FieldResult.Wrong
    }

/** Text or a number, kept as written: `7` and `"7"` both give `7`. */
private fun JsonObject.month(): FieldResult<String?> =
    when (val value = this[ImportField.Month.key]) {
        null, JsonNull -> FieldResult.Ok(null)
        is JsonPrimitive -> when {
            value.isString -> FieldResult.Ok(value.content.orAbsent())
            value.content == "true" || value.content == "false" -> FieldResult.Wrong
            else -> FieldResult.Ok(value.content)
        }
        else -> FieldResult.Wrong
    }
