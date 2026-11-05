package app.cardfilter.domain.usecase

import app.cardfilter.domain.model.TopicId
import app.cardfilter.testutil.FakeCardRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetCardFavoriteTest {
    private val repository = FakeCardRepository()
    private val setFavorite = SetCardFavorite(repository)
    private val topic = TopicId(1)

    @Test
    fun aNewCardIsNotAFavorite() = runTest {
        val card = repository.create(topic, "A", emptyList())

        assertFalse(card.isFavorite)
    }

    @Test
    fun marksAndUnmarksACardAsFavorite() = runTest {
        val card = repository.create(topic, "A", emptyList())

        setFavorite(card.id, true)
        assertTrue(repository.get(card.id)!!.isFavorite)

        setFavorite(card.id, false)
        assertFalse(repository.get(card.id)!!.isFavorite)
    }

    @Test
    fun onlyTheGivenCardChanges() = runTest {
        val a = repository.create(topic, "A", emptyList())
        val b = repository.create(topic, "B", emptyList())

        setFavorite(a.id, true)

        assertEquals(listOf(true, false), listOf(a, b).map { repository.get(it.id)!!.isFavorite })
    }
}
