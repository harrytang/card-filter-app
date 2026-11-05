package app.cardfilter.domain.usecase

import app.cardfilter.domain.importing.CardWrite
import app.cardfilter.domain.importing.ImportError
import app.cardfilter.domain.importing.ImportPlan
import app.cardfilter.domain.importing.ImportPlanResult
import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.CardRepository
import app.cardfilter.testutil.FakeCardRepository
import app.cardfilter.testutil.FakeImageDownloader
import app.cardfilter.testutil.WakodoSampleJson
import app.cardfilter.testutil.pngBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportUseCasesTest {
    private val repository = FakeCardRepository()
    private val downloader = FakeImageDownloader()
    private val topic = TopicId(10)
    private val prepare = PrepareCardImport(repository)
    private val import = ImportCards(repository, FetchCardImage(downloader))

    private suspend fun planOf(json: String): ImportPlan =
        assertIs<ImportPlanResult.Planned>(prepare(topic, json)).plan

    private suspend fun cards() = repository.observeByTopic(topic).first()

    // ---- PrepareCardImport

    @Test
    fun prepare_plansTheFileAgainstTheTopicsExistingCards() = runTest {
        repository.create(topic, "A", emptyList())
        repository.create(TopicId(99), "B", emptyList())

        val plan = planOf("""[{"name":"a"},{"name":"B"}]""")

        assertEquals(1, plan.overwriteCount, "a matches the card in this topic")
        assertEquals(1, plan.createCount, "B only exists in another topic")
    }

    @Test
    fun prepare_anInvalidFile_isReportedAndNothingIsWritten() = runTest {
        assertEquals(ImportPlanResult.Invalid(ImportError.NotJson), prepare(topic, "not json"))
        assertEquals(emptyList(), repository.importCalls)
        assertEquals(emptyList(), cards())
    }

    @Test
    fun prepare_twoEntriesWithTheSameName_areReported() = runTest {
        val result = prepare(topic, """[{"name":"A"},{"name":"a"}]""")

        assertEquals(ImportPlanResult.Invalid(ImportError.DuplicateName(entry = 2, firstEntry = 1)), result)
    }

    @Test
    fun prepare_makesNoDownloadAndNoWrite() = runTest {
        planOf("""[{"name":"A","image":"https://example.com/a.png"}]""")

        assertEquals(emptyList(), downloader.requests)
        assertEquals(emptyList(), repository.importCalls)
    }

    // ---- ImportCards

    @Test
    fun import_storesEveryImageOnceWithItsCard() = runTest {
        downloader.respond = { pngBytes() }
        val plan = planOf(
            """[{"name":"A","image":"https://example.com/a.png"},{"name":"B","image":"https://example.com/b.png"}]""",
        )

        import(topic, plan)

        assertEquals(listOf("https://example.com/a.png", "https://example.com/b.png"), downloader.requests.sorted())
        assertEquals(listOf(true, true), cards().map { it.hasImage })
        assertContentEquals(pngBytes(), repository.getImage(cards().first().id))
    }

    @Test
    fun theRealWakodoSample_importsSixCardsEachWithItsOwnImage() = runTest {
        // Each image carries its own URL after the PNG signature, so a swapped or shared image is seen.
        downloader.respond = { url -> pngBytes() + url.encodeToByteArray() }

        val summary = import(topic, planOf(WakodoSampleJson))

        assertEquals(ImportSummary(created = 6, overwritten = 0, imagesFailed = 0), summary)
        assertEquals(6, downloader.requests.toSet().size, "one request per product")
        val stored = cards()
        assertEquals(6, stored.size)
        val expectedUrls = listOf("40194820", "40603500", "40194840", "40194860", "40194870", "40194880")
            .map { "https://www.wakodo.co.jp/product/image/pdt/$it.jpg" }
        stored.zip(expectedUrls).forEach { (card, url) ->
            assertEquals(url, repository.getImage(card.id)!!.decodeToString(startIndex = pngBytes().size), card.title)
        }
    }

    @Test
    fun theRealWakodoSample_importedTwice_leavesSixCardsAndKeepsTheirImages() = runTest {
        downloader.respond = { pngBytes() }
        import(topic, planOf(WakodoSampleJson))
        val firstIds = cards().map { it.id }

        val againPlan = planOf(WakodoSampleJson)
        assertEquals(6, againPlan.overwriteCount)
        downloader.respond = { error("offline the second time") }
        val summary = import(topic, againPlan)

        assertEquals(ImportSummary(created = 0, overwritten = 6, imagesFailed = 6), summary)
        assertEquals(firstIds, cards().map { it.id }, "same cards, same order, no duplicates")
        assertTrue(cards().all { it.hasImage }, "a failed download must not delete the stored image")
    }

    @Test
    fun import_savesTheAttributesOfEachCard() = runTest {
        val plan = planOf("""[{"name":"A","ingredients":"rice","month":7}]""")

        import(topic, plan)

        assertEquals(listOf(Attribute("ingredients", "rice"), Attribute("month", "7")), cards().single().attributes)
    }

    @Test
    fun import_anEntryWithoutAnImageUrl_makesNoRequest() = runTest {
        val plan = planOf("""[{"name":"A"},{"name":"B","image":null},{"name":"C","image":"  "}]""")

        val summary = import(topic, plan)

        assertEquals(emptyList(), downloader.requests)
        assertEquals(0, summary.imagesFailed, "having no image url is not a failed download")
        assertEquals(3, cards().size)
    }

    @Test
    fun import_aFailedDownload_savesTheCardWithoutAnImageAndCountsIt() = runTest {
        downloader.respond = { error("offline") }
        val plan = planOf("""[{"name":"A","image":"https://example.com/a.png"},{"name":"B"}]""")

        val summary = import(topic, plan)

        assertEquals(listOf("A", "B"), cards().map { it.title })
        assertEquals(listOf(false, false), cards().map { it.hasImage })
        assertEquals(1, summary.imagesFailed)
    }

    @Test
    fun import_bytesThatAreNotAnImage_areCountedAsAFailedImage() = runTest {
        downloader.respond = { "<html>not found</html>".encodeToByteArray() }
        val plan = planOf("""[{"name":"A","image":"https://example.com/a"}]""")

        val summary = import(topic, plan)

        assertEquals(false, cards().single().hasImage)
        assertEquals(1, summary.imagesFailed)
    }

    @Test
    fun import_anAddressThatIsNotHttp_isCountedAsAFailedImageWithoutARequest() = runTest {
        val plan = planOf("""[{"name":"A","image":"ftp://example.com/a.png"}]""")

        val summary = import(topic, plan)

        assertEquals(emptyList(), downloader.requests)
        assertEquals(1, summary.imagesFailed)
    }

    @Test
    fun import_anOverwriteWhoseDownloadFailed_keepsTheOldImage() = runTest {
        val old = repository.create(topic, "A", listOf(Attribute("k", "old")), byteArrayOf(1, 1))
        downloader.respond = { error("offline") }
        val plan = planOf("""[{"name":"A","image":"https://example.com/a.png","month":3}]""")

        import(topic, plan)

        assertContentEquals(byteArrayOf(1, 1), repository.getImage(old.id))
        assertEquals(listOf(Attribute("month", "3")), repository.get(old.id)?.attributes)
    }

    @Test
    fun import_anOverwriteWithAnEntryWithoutAnImage_keepsTheOldImage() = runTest {
        val old = repository.create(topic, "A", emptyList(), byteArrayOf(1, 1))

        import(topic, planOf("""[{"name":"A"}]"""))

        assertContentEquals(byteArrayOf(1, 1), repository.getImage(old.id))
    }

    @Test
    fun import_anOverwriteWithANewImage_replacesTheOldOne() = runTest {
        val old = repository.create(topic, "A", emptyList(), byteArrayOf(1, 1))
        downloader.respond = { pngBytes() }

        import(topic, planOf("""[{"name":"A","image":"https://example.com/a.png"}]"""))

        assertContentEquals(pngBytes(), repository.getImage(old.id))
        assertEquals(1, cards().size, "overwritten in place, not duplicated")
    }

    @Test
    fun import_reportsHowManyWereCreatedOverwrittenAndLackAnImage() = runTest {
        repository.create(topic, "Existing", emptyList())
        downloader.respond = { url -> if (url.endsWith("ok.png")) pngBytes() else error("404") }
        val plan = planOf(
            """[
                {"name":"Existing"},
                {"name":"Good","image":"https://example.com/ok.png"},
                {"name":"Bad","image":"https://example.com/missing.png"}
            ]""",
        )

        val summary = import(topic, plan)

        assertEquals(ImportSummary(created = 2, overwritten = 1, imagesFailed = 1), summary)
    }

    @Test
    fun import_writesAllCardsInOneCallAfterTheLastDownload() = runTest {
        downloader.respond = { delay(50); pngBytes() }
        val plan = planOf(
            """[
                {"name":"A","image":"https://example.com/a.png"},
                {"name":"B","image":"https://example.com/b.png"},
                {"name":"C"}
            ]""",
        )

        import(topic, plan)

        assertEquals(1, repository.importCalls.size, "one write for the whole import")
        assertEquals(listOf("A", "B", "C"), repository.importCalls.single().map { it.title }, "file order")
        assertTrue(repository.importCalls.single().all { it is CardWrite.Create })
    }

    @Test
    fun import_runsAtMostFourDownloadsAtOnce() = runTest {
        var running = 0
        var peak = 0
        downloader.respond = {
            running++
            peak = maxOf(peak, running)
            delay(100)
            running--
            pngBytes()
        }
        val json = (1..10).joinToString(prefix = "[", postfix = "]") {
            """{"name":"Card $it","image":"https://example.com/$it.png"}"""
        }

        import(topic, planOf(json))

        assertEquals(4, peak)
        assertEquals(10, cards().size)
    }

    @Test
    fun import_reportsProgressFromZeroToTheNumberOfEntries() = runTest {
        downloader.respond = { pngBytes() }
        val plan = planOf(
            """[{"name":"A","image":"https://example.com/a.png"},{"name":"B"},{"name":"C","image":"https://example.com/c.png"}]""",
        )
        val reports = mutableListOf<Pair<Int, Int>>()

        import(topic, plan) { done, total -> reports += done to total }

        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), reports)
    }

    @Test
    fun import_cancelledDuringTheDownloads_writesNothing() = runTest {
        val existing = repository.create(topic, "Existing", emptyList())
        val never = CompletableDeferred<ByteArray>()
        downloader.respond = { never.await() }
        val plan = planOf(
            """[{"name":"Existing","image":"https://example.com/a.png"},{"name":"New","image":"https://example.com/b.png"}]""",
        )

        val job = launch { import(topic, plan) }
        runCurrent()
        assertEquals(2, downloader.requests.size, "the downloads had started")
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(emptyList(), repository.importCalls)
        assertEquals(listOf(existing), cards(), "the topic is exactly as it was")
        assertNull(repository.getImage(existing.id))
    }

    @Test
    fun import_aFailingWrite_propagates() = runTest {
        val failing = object : CardRepository by repository {
            override suspend fun importCards(topicId: TopicId, writes: List<CardWrite>) = error("disk full")
        }
        val plan = planOf("""[{"name":"A"}]""")

        assertFailsWith<IllegalStateException> { ImportCards(failing, FetchCardImage(downloader))(topic, plan) }
    }
}
