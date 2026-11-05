package app.cardfilter.presentation

import app.cardfilter.domain.importing.ImportError
import app.cardfilter.domain.importing.ImportField
import app.cardfilter.domain.importing.MaxImportCards
import app.cardfilter.domain.importing.MaxImportFileBytes
import app.cardfilter.domain.usecase.ImportSummary
import app.cardfilter.domain.validation.AttributeField
import app.cardfilter.domain.validation.TitleError

/**
 * All user-visible text in one place so localisation (the PRD is Vietnamese; the UI language
 * is still an open question) is a single swap later.
 */
internal object Strings {
    const val topicsTitle = "Topics"
    const val addTopic = "Add topic"
    const val renameTopic = "Rename topic"
    const val deleteTopic = "Delete topic"
    const val topicName = "Topic name"
    const val topicsEmptyTitle = "No topics yet"
    const val topicsEmptyMessage = "Tap + to create your first topic."
    fun deleteTopicMessage(name: String) = "Delete \"$name\" and all of its cards? This cannot be undone."

    const val addCard = "Add card"
    const val editCard = "Edit card"
    const val deleteCard = "Delete card"
    const val cardTitle = "Card title"
    const val cardsEmptyTitle = "No cards yet"
    const val cardsEmptyMessage = "Tap + to add a card to this topic."
    fun deleteCardMessage(title: String) = "Delete \"$title\"? This cannot be undone."
    fun moreAttributes(count: Int) = "+$count more"

    const val searchCards = "Search this topic"
    const val clearSearch = "Clear search"
    const val searchNote = "Shows cards that contain any of these words. It does not show what a card lacks."
    const val notFilter = "Not (hide cards with)"
    const val clearNotFilter = "Clear Not filter"
    const val notFilterNote =
        "Hides cards that contain any of these words. A card with no recorded value is not hidden."
    const val addFavorite = "Add to favorites"
    const val removeFavorite = "Remove from favorites"

    /** "12 cards", or "3 of 12 cards" while a filter hides some of them. */
    fun cardCount(shown: Int, total: Int): String {
        val noun = if (total == 1) "card" else "cards"
        return if (shown == total) "$total $noun" else "$shown of $total $noun"
    }

    const val quickFilters = "Quick filters"
    const val addQuickFilter = "New filter"
    const val newQuickFilterTitle = "New quick filter"
    const val editQuickFilterTitle = "Edit quick filter"
    const val quickFilterName = "Name"
    const val quickFilterInclude = "Search words"
    const val quickFilterActions = "Filter actions"
    const val quickFilterActionsHint = "Hold for edit and delete"
    const val editQuickFilter = "Edit filter"
    const val deleteQuickFilter = "Delete filter"
    fun deleteQuickFilterMessage(name: String) = "Delete the filter \"$name\"? Your cards are not changed."

    const val noMatchesTitle = "No matching cards"
    const val noMatchesMessage =
        "No card in this topic fits these filters. This is a text search, not an allergy check."

    const val cardImage = "Card image"
    const val imageUrl = "Image URL"
    const val imageUrlHint = "Optional. The image is saved on this device."
    const val downloadingImage = "Downloading image…"

    const val importCards = "Import"
    const val importTitle = "Import cards"
    const val importReading = "Reading…"
    const val importFromFile = "From file"
    const val importPasteJson = "Paste JSON"
    const val pasteJsonTitle = "Paste JSON"
    const val pasteJsonHint = "Paste a JSON list of cards"
    const val overwriteTitle = "Replace existing cards?"
    const val overwriteConfirm = "Overwrite and import"
    const val importDoneTitle = "Import finished"
    const val importFailedTitle = "Couldn't import"
    const val importFailedNote = "Nothing was imported."
    const val ok = "OK"
    fun importProgress(done: Int, total: Int) = "Importing $done of $total…"

    private fun cards(count: Int) = if (count == 1) "1 card" else "$count cards"

    /** Longest title the overwrite confirmation shows whole; product names can run past 100 characters. */
    private const val ListedTitleLimit = 30

    /** [title] cut to [ListedTitleLimit] UTF-16 units including the "…", never between the two halves of one character. */
    private fun shortened(title: String): String {
        if (title.length <= ListedTitleLimit) return title
        var end = ListedTitleLimit - 1
        if (title[end - 1].isHighSurrogate()) end--
        return title.substring(0, end).trimEnd() + "…"
    }

    fun overwriteMessage(overwriteCount: Int, titles: List<String>): String {
        val listed = titles.joinToString(", ") { "\"${shortened(it)}\"" }
        val more = overwriteCount - titles.size
        val rest = if (more > 0) " and $more more" else ""
        return "${cards(overwriteCount)} in this topic will be replaced: $listed$rest."
    }

    fun importSummary(summary: ImportSummary): String {
        val replaced = if (summary.overwritten > 0) " (${summary.overwritten} replaced)" else ""
        val images = when (summary.imagesFailed) {
            0 -> ""
            1 -> "\n1 image could not be downloaded."
            else -> "\n${summary.imagesFailed} images could not be downloaded."
        }
        return "Imported ${cards(summary.created + summary.overwritten)}$replaced.$images"
    }

    fun errorFor(error: ImportError): String = when (error) {
        ImportError.NotJson -> "This is not valid JSON."
        ImportError.NotAnArray -> "The JSON must be a list of cards."
        ImportError.Empty -> "There are no cards to import."
        is ImportError.TooMany -> "There are ${error.count} cards. The limit is $MaxImportCards."
        is ImportError.EntryNotObject -> "Card ${error.entry} is not a card entry."
        is ImportError.MissingName -> "Card ${error.entry} has no name."
        is ImportError.WrongType ->
            "Card ${error.entry}: \"${error.field.key}\" is not ${expectedType(error.field)}."
        is ImportError.DuplicateName -> "Card ${error.entry} has the same name as card ${error.firstEntry}."
        ImportError.FileTooLarge -> "The JSON is too large. The limit is ${MaxImportFileBytes / 1_000_000} MB."
        ImportError.Unreadable -> "The file could not be read."
        ImportError.Storage -> "Something went wrong while saving."
    }

    private fun expectedType(field: ImportField) = when (field) {
        ImportField.Month -> "text or a number"
        ImportField.Ingredients -> "text or a list of text"
        ImportField.Image, ImportField.Name -> "text"
    }

    const val addAttribute = "Add attribute"
    const val removeAttribute = "Remove attribute"
    const val attributeName = "Attribute name"
    const val attributeValue = "Value"
    const val cardLoadFailedTitle = "Couldn't open this card"
    const val cardLoadFailedMessage = "Go back and try again."

    const val discardChangesTitle = "Discard changes?"
    const val discardChangesMessage = "This card has changes that have not been saved."
    const val discard = "Discard"
    const val keepEditing = "Keep editing"

    const val save = "Save"
    const val cancel = "Cancel"
    const val delete = "Delete"
    const val back = "Back"
    const val moreActions = "More actions"
    const val actionFailed = "Something went wrong. Please try again."

    fun errorFor(error: TitleError): String = when (error) {
        TitleError.Blank -> "Enter some text"
    }

    fun attributeErrorFor(field: AttributeField): String = when (field) {
        AttributeField.Name -> "Enter a name"
        AttributeField.Value -> "Enter a value"
    }
}
