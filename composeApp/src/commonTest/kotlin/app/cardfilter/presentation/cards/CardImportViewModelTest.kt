package app.cardfilter.presentation.cards

import app.cardfilter.domain.importing.CardWrite
import app.cardfilter.domain.importing.ImportError
import app.cardfilter.domain.importing.ImportFailure
import app.cardfilter.domain.importing.MaxImportFileBytes
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.CardRepository
import app.cardfilter.domain.usecase.FetchCardImage
import app.cardfilter.domain.usecase.ImportCards
import app.cardfilter.domain.usecase.ImportSummary
import app.cardfilter.domain.usecase.PrepareCardImport
import app.cardfilter.testutil.FakeCardRepository
import app.cardfilter.testutil.FakeImageDownloader
import app.cardfilter.testutil.pngBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CardImportViewModelTest {
    private val cards = FakeCardRepository()
    private val downloader = FakeImageDownloader()
    private val topic = TopicId(1)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(source: CardRepository = cards) = CardImportViewModel(
        topicId = topic,
        prepareCardImport = PrepareCardImport(source),
        importCards = ImportCards(source, FetchCardImage(downloader)),
    )

    private fun CardImportViewModel.choose(json: String) = onFileChosen { json }

    private suspend fun titles() = cards.observeByTopic(topic).first().map { it.title }

    @Test
    fun startsIdle() {
        assertEquals(CardImportState.Idle, viewModel().state.value)
    }

    @Test
    fun aValidFileWithNoOverwrites_importsStraightAwayAndEndsWithTheSummary() = runTest {
        val gate = CompletableDeferred<ByteArray>()
        downloader.respond = { gate.await() }
        val viewModel = viewModel()

        viewModel.choose("""[{"name":"A","image":"https://example.com/a.png"}]""")

        assertEquals(CardImportState.Importing(done = 0, total = 1), viewModel.state.value)
        gate.complete(pngBytes())
        assertEquals(
            CardImportState.Done(ImportSummary(created = 1, overwritten = 0, imagesFailed = 0)),
            viewModel.state.value,
        )
        assertEquals(listOf("A"), titles())
    }

    @Test
    fun pastedValidJson_importsLikeAFile() = runTest {
        val viewModel = viewModel()

        viewModel.onTextPasted("""[{"name":"A"},{"name":"B"}]""")

        assertEquals(
            CardImportState.Done(ImportSummary(created = 2, overwritten = 0, imagesFailed = 0)),
            viewModel.state.value,
        )
        assertEquals(listOf("A", "B"), titles())
    }

    @Test
    fun pastedTextThatIsNotJson_failsAndImportsNothing() = runTest {
        val viewModel = viewModel()

        viewModel.onTextPasted("[{\"name\": ")

        assertEquals(CardImportState.Failed(ImportError.NotJson), viewModel.state.value)
        assertEquals(emptyList(), titles())
    }

    @Test
    fun pastedTextOverTheSizeLimit_failsAsTooLargeCountingBytesNotCharacters() = runTest {
        val viewModel = viewModel()
        // Two bytes per character, so the character count alone is under the limit.
        val text = "é".repeat(MaxImportFileBytes / 2 + 1)

        viewModel.onTextPasted(text)

        assertEquals(CardImportState.Failed(ImportError.FileTooLarge), viewModel.state.value)
        assertEquals(emptyList(), titles())
    }

    @Test
    fun pastedJsonThatOverwritesCards_stopsAtTheSameConfirmation() = runTest {
        cards.create(topic, "A", emptyList())
        val viewModel = viewModel()

        viewModel.onTextPasted("""[{"name":"a"}]""")

        assertIs<CardImportState.Confirm>(viewModel.state.value)
    }

    @Test
    fun whileTheFileIsBeingRead_theStateIsReading() = runTest {
        val gate = CompletableDeferred<String>()
        val viewModel = viewModel()

        viewModel.onFileChosen { gate.await() }

        assertEquals(CardImportState.Reading, viewModel.state.value)
        gate.complete("""[{"name":"A"}]""")
        assertIs<CardImportState.Done>(viewModel.state.value)
    }

    @Test
    fun aFileThatOverwritesCards_stopsAtAConfirmationWithoutWritingAnything() = runTest {
        cards.create(topic, "Existing", emptyList())
        val viewModel = viewModel()

        viewModel.choose("""[{"name":"existing"},{"name":"Fresh"}]""")

        assertEquals(
            CardImportState.Confirm(createCount = 1, overwriteCount = 1, overwrittenTitles = listOf("Existing")),
            viewModel.state.value,
        )
        assertEquals(emptyList(), cards.importCalls)
    }

    @Test
    fun confirmingTheOverwrite_importsTheFile() = runTest {
        cards.create(topic, "Existing", emptyList())
        val viewModel = viewModel()
        viewModel.choose("""[{"name":"existing","month":4},{"name":"Fresh"}]""")

        viewModel.onConfirmOverwrite()

        assertEquals(
            CardImportState.Done(ImportSummary(created = 1, overwritten = 1, imagesFailed = 0)),
            viewModel.state.value,
        )
        assertEquals(listOf("existing", "Fresh"), titles())
    }

    @Test
    fun cancellingAtTheConfirmation_returnsToIdleAndWritesNothing() = runTest {
        val existing = cards.create(topic, "Existing", emptyList())
        val viewModel = viewModel()
        viewModel.choose("""[{"name":"Existing","month":4}]""")

        viewModel.onCancel()

        assertEquals(CardImportState.Idle, viewModel.state.value)
        assertEquals(emptyList(), cards.importCalls)
        assertEquals(listOf(existing), cards.observeByTopic(topic).first())
    }

    @Test
    fun anInvalidFile_failsWithItsErrorAndWritesNothing() = runTest {
        val viewModel = viewModel()

        viewModel.choose("""[{"name":"A"},{"name":""}]""")

        assertEquals(CardImportState.Failed(ImportError.MissingName(2)), viewModel.state.value)
        assertEquals(emptyList(), titles(), "the valid first entry must not have been imported")
    }

    @Test
    fun aFileThatCannotBeRead_failsAsUnreadable() = runTest {
        val viewModel = viewModel()

        viewModel.onFileChosen { error("permission denied") }

        assertEquals(CardImportState.Failed(ImportError.Unreadable), viewModel.state.value)
    }

    @Test
    fun aFileTheReaderRejectsAsTooLarge_failsWithThatError() = runTest {
        val viewModel = viewModel()

        viewModel.onFileChosen { throw ImportFailure(ImportError.FileTooLarge) }

        assertEquals(CardImportState.Failed(ImportError.FileTooLarge), viewModel.state.value)
    }

    @Test
    fun progress_isPublishedWhileADownloadIsStillRunning() = runTest {
        val gate = CompletableDeferred<ByteArray>()
        downloader.respond = { url -> if (url.endsWith("b.png")) gate.await() else pngBytes() }
        val viewModel = viewModel()

        viewModel.choose(
            """[{"name":"A","image":"https://example.com/a.png"},{"name":"B","image":"https://example.com/b.png"}]""",
        )

        assertEquals(CardImportState.Importing(done = 1, total = 2), viewModel.state.value)
        gate.complete(pngBytes())
        assertIs<CardImportState.Done>(viewModel.state.value)
    }

    @Test
    fun cancellingWhileImporting_returnsToIdleAndNeverWrites() = runTest {
        val gate = CompletableDeferred<ByteArray>()
        downloader.respond = { gate.await() }
        val viewModel = viewModel()
        viewModel.choose("""[{"name":"A","image":"https://example.com/a.png"}]""")

        viewModel.onCancel()
        gate.complete(pngBytes())

        assertEquals(CardImportState.Idle, viewModel.state.value)
        assertEquals(emptyList(), cards.importCalls)
        assertEquals(emptyList(), titles())
    }

    @Test
    fun aSecondFileWhileBusy_isIgnored() = runTest {
        val gate = CompletableDeferred<ByteArray>()
        downloader.respond = { gate.await() }
        val viewModel = viewModel()
        viewModel.choose("""[{"name":"A","image":"https://example.com/a.png"}]""")
        var secondWasRead = false

        viewModel.onFileChosen {
            secondWasRead = true
            """[{"name":"B"}]"""
        }

        assertFalse(secondWasRead)
        gate.complete(pngBytes())
        assertEquals(listOf("A"), titles())
    }

    @Test
    fun dismissingAResult_returnsToIdle() = runTest {
        val viewModel = viewModel()
        viewModel.choose("""[{"name":"A"}]""")
        assertIs<CardImportState.Done>(viewModel.state.value)

        viewModel.onDismissResult()
        assertEquals(CardImportState.Idle, viewModel.state.value)

        viewModel.choose("not json")
        assertIs<CardImportState.Failed>(viewModel.state.value)
        viewModel.onDismissResult()
        assertEquals(CardImportState.Idle, viewModel.state.value)
    }

    @Test
    fun aFailingWrite_endsAsAStorageFailure() = runTest {
        val failing = object : CardRepository by cards {
            override suspend fun importCards(topicId: TopicId, writes: List<CardWrite>) = error("disk full")
        }
        val viewModel = viewModel(failing)

        viewModel.choose("""[{"name":"A"}]""")

        assertEquals(CardImportState.Failed(ImportError.Storage), viewModel.state.value)
    }
}
