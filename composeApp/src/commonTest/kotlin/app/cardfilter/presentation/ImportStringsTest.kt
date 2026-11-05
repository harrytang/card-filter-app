package app.cardfilter.presentation

import app.cardfilter.domain.importing.ImportError
import app.cardfilter.domain.importing.ImportField
import app.cardfilter.domain.importing.MaxImportCards
import app.cardfilter.domain.usecase.ImportSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportStringsTest {
    @Test
    fun summary_countsEveryCardTheImportTouched() {
        assertEquals("Imported 30 cards.", Strings.importSummary(ImportSummary(30, 0, 0)))
        assertEquals("Imported 1 card.", Strings.importSummary(ImportSummary(1, 0, 0)))
        assertEquals("Imported 30 cards (4 replaced).", Strings.importSummary(ImportSummary(26, 4, 0)))
    }

    @Test
    fun summary_saysHowManyImagesCouldNotBeDownloaded() {
        assertEquals(
            "Imported 5 cards.\n3 images could not be downloaded.",
            Strings.importSummary(ImportSummary(5, 0, 3)),
        )
        assertEquals(
            "Imported 5 cards.\n1 image could not be downloaded.",
            Strings.importSummary(ImportSummary(5, 0, 1)),
        )
    }

    @Test
    fun overwriteMessage_shortensLongTitlesSoTheDialogStaysReadable() {
        val long = "１食分の野菜入り そのまま素材 定番野菜 [Ready-to-use Ingredients with One Meal's Serving of Vegetables]"

        val message = Strings.overwriteMessage(overwriteCount = 1, titles = listOf(long))

        assertEquals("1 card in this topic will be replaced: \"１食分の野菜入り そのまま素材 定番野菜 [Ready-t…\".", message)
    }

    @Test
    fun overwriteMessage_keepsATitleOfExactlyTheLimitWhole() {
        val thirty = "a".repeat(30)

        assertEquals(
            "1 card in this topic will be replaced: \"$thirty\".",
            Strings.overwriteMessage(overwriteCount = 1, titles = listOf(thirty)),
        )
    }

    @Test
    fun overwriteMessage_neverSplitsACharacterMadeOfTwoUnits() {
        // 29 letters, then an emoji (two UTF-16 units) that would be cut in half at 30 units.
        val title = "a".repeat(29) + "😀" + "tail"

        val message = Strings.overwriteMessage(overwriteCount = 1, titles = listOf(title))

        assertEquals("1 card in this topic will be replaced: \"${"a".repeat(29)}…\".", message)
    }

    @Test
    fun overwriteMessage_listsTheTitlesAndCountsTheRest() {
        assertEquals(
            "1 card in this topic will be replaced: \"A\".",
            Strings.overwriteMessage(overwriteCount = 1, titles = listOf("A")),
        )
        assertEquals(
            "7 cards in this topic will be replaced: \"A\", \"B\" and 5 more.",
            Strings.overwriteMessage(overwriteCount = 7, titles = listOf("A", "B")),
        )
        assertEquals(
            "2 cards in this topic will be replaced: \"A\", \"B\".",
            Strings.overwriteMessage(overwriteCount = 2, titles = listOf("A", "B")),
        )
    }

    @Test
    fun importErrors_nameTheEntryOrTheFileProblem() {
        assertEquals("Card 3 has no name.", Strings.errorFor(ImportError.MissingName(3)))
        assertEquals(
            "Card 2: \"month\" is not text or a number.",
            Strings.errorFor(ImportError.WrongType(2, ImportField.Month)),
        )
        assertEquals(
            "Card 4 has the same name as card 1.",
            Strings.errorFor(ImportError.DuplicateName(entry = 4, firstEntry = 1)),
        )
        assertEquals(
            "There are ${MaxImportCards + 1} cards. The limit is $MaxImportCards.",
            Strings.errorFor(ImportError.TooMany(MaxImportCards + 1)),
        )
    }

    @Test
    fun everyImportError_hasAMessageThatSaysNothingWasImported() {
        val errors = listOf(
            ImportError.NotJson,
            ImportError.NotAnArray,
            ImportError.Empty,
            ImportError.TooMany(101),
            ImportError.EntryNotObject(1),
            ImportError.MissingName(1),
            ImportError.WrongType(1, ImportField.Image),
            ImportError.DuplicateName(2, 1),
            ImportError.FileTooLarge,
            ImportError.Unreadable,
            ImportError.Storage,
        )

        errors.forEach { assertTrue(Strings.errorFor(it).isNotBlank(), "$it") }
        assertTrue(Strings.importFailedNote.contains("Nothing was imported"))
    }

    @Test
    fun importErrors_doNotSayFileSoTheyFitPastedJsonToo() {
        listOf(
            ImportError.NotJson,
            ImportError.NotAnArray,
            ImportError.Empty,
            ImportError.TooMany(101),
            ImportError.FileTooLarge,
        ).forEach { assertTrue(!Strings.errorFor(it).contains("file", ignoreCase = true), "$it") }
        assertTrue(!Strings.importFailedNote.contains("file", ignoreCase = true))
    }

    @Test
    fun importMenu_offersFileAndPaste() {
        assertEquals("From file", Strings.importFromFile)
        assertEquals("Paste JSON", Strings.importPasteJson)
        assertEquals("Paste JSON", Strings.pasteJsonTitle)
        assertTrue(Strings.pasteJsonHint.isNotBlank())
    }
}
