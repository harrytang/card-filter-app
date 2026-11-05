package app.cardfilter.domain.usecase

import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.validation.SaveOutcome
import app.cardfilter.domain.validation.TitleError
import app.cardfilter.testutil.FakeTopicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicUseCasesTest {
    private val repository = FakeTopicRepository()
    private val createTopic = CreateTopic(repository)
    private val renameTopic = RenameTopic(repository)
    private val deleteTopic = DeleteTopic(repository)

    @Test
    fun createTopic_trimsNameBeforeSaving() = runTest {
        val outcome = createTopic("  Pigeon  ")

        assertEquals(SaveOutcome.Saved(Topic(TopicId(1), "Pigeon")), outcome)
        assertEquals(listOf(Topic(TopicId(1), "Pigeon")), repository.observeAll().first())
    }

    @Test
    fun createTopic_rejectsBlankNameAndSavesNothing() = runTest {
        val outcome = createTopic("   ")

        assertEquals(SaveOutcome.Rejected(TitleError.Blank), outcome)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun createTopic_keepsVietnameseAndJapaneseTextUnchanged() = runTest {
        createTopic("thịt bầm")
        createTopic("　ベビーフード　")

        val names = repository.observeAll().first().map { it.name }
        assertEquals(listOf("thịt bầm", "ベビーフード"), names)
    }

    @Test
    fun renameTopic_trimsAndUpdatesName() = runTest {
        val saved = createTopic("Wakodo") as SaveOutcome.Saved

        val outcome = renameTopic(saved.value.id, " Wakodo set ")

        assertEquals(SaveOutcome.Saved(Unit), outcome)
        assertEquals("Wakodo set", repository.observeAll().first().single().name)
    }

    @Test
    fun renameTopic_rejectsBlankNameAndKeepsOldName() = runTest {
        val saved = createTopic("Wakodo") as SaveOutcome.Saved

        val outcome = renameTopic(saved.value.id, "")

        assertEquals(SaveOutcome.Rejected(TitleError.Blank), outcome)
        assertEquals("Wakodo", repository.observeAll().first().single().name)
    }

    @Test
    fun deleteTopic_removesOnlyThatTopic() = runTest {
        val keep = createTopic("Keep") as SaveOutcome.Saved
        val drop = createTopic("Drop") as SaveOutcome.Saved

        deleteTopic(drop.value.id)

        assertEquals(listOf(keep.value), repository.observeAll().first())
    }
}
