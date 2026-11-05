package app.cardfilter.domain.usecase

import app.cardfilter.domain.model.QuickFilter
import app.cardfilter.domain.model.QuickFilterId
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.validation.SaveOutcome
import app.cardfilter.domain.validation.TitleError
import app.cardfilter.testutil.FakeQuickFilterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuickFilterUseCasesTest {
    private val repository = FakeQuickFilterRepository()
    private val topic = TopicId(1)
    private val observe = ObserveQuickFilters(repository)
    private val save = SaveQuickFilter(repository)
    private val edit = EditQuickFilter(repository)
    private val delete = DeleteQuickFilter(repository)

    @Test
    fun save_trimsTheNameAndBothQueries() = runTest {
        val outcome = save(topic, "  Safe 7m  ", " 7 ", " egg, milk ")

        val expected = QuickFilter(QuickFilterId(1), topic, "Safe 7m", "7", "egg, milk")
        assertEquals(SaveOutcome.Saved(expected), outcome)
        assertEquals(listOf(expected), observe(topic).first())
    }

    @Test
    fun save_rejectsABlankNameAndStoresNothing() = runTest {
        val outcome = save(topic, "   ", "7", "egg")

        assertEquals(SaveOutcome.Rejected(TitleError.Blank), outcome)
        assertTrue(observe(topic).first().isEmpty())
    }

    @Test
    fun save_keepsVietnameseAndJapaneseTextUnchanged() = runTest {
        save(topic, "Không trứng", "thịt bầm", "卵、たまご")

        val filter = observe(topic).first().single()
        assertEquals("Không trứng", filter.name)
        assertEquals("thịt bầm", filter.include)
        assertEquals("卵、たまご", filter.exclude)
    }

    @Test
    fun observe_returnsOnlyTheTopicsOwnFilters() = runTest {
        save(topic, "mine", "a", "")
        save(TopicId(2), "theirs", "b", "")

        assertEquals(listOf("mine"), observe(topic).first().map { it.name })
    }

    @Test
    fun edit_trimsAndUpdates() = runTest {
        val saved = save(topic, "Old", "7", "") as SaveOutcome.Saved

        val outcome = edit(saved.value.id, " New ", " 8 ", " egg ")

        assertEquals(SaveOutcome.Saved(Unit), outcome)
        val filter = observe(topic).first().single()
        assertEquals(Triple("New", "8", "egg"), Triple(filter.name, filter.include, filter.exclude))
    }

    @Test
    fun edit_rejectsABlankNameAndKeepsTheOldFilter() = runTest {
        val saved = save(topic, "Old", "7", "egg") as SaveOutcome.Saved

        val outcome = edit(saved.value.id, " ", "8", "milk")

        assertEquals(SaveOutcome.Rejected(TitleError.Blank), outcome)
        assertEquals(saved.value, observe(topic).first().single())
    }

    @Test
    fun delete_removesOnlyThatFilter() = runTest {
        val a = save(topic, "A", "1", "") as SaveOutcome.Saved
        save(topic, "B", "2", "")

        delete(a.value.id)

        assertEquals(listOf("B"), observe(topic).first().map { it.name })
    }
}
