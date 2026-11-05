package app.cardfilter.presentation

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AttemptTest {
    @Test
    fun returnsTheValueOnSuccess() = runTest {
        assertEquals(42, attempt { 42 }.getOrNull())
    }

    @Test
    fun wrapsOrdinaryExceptionsInAFailure() = runTest {
        val boom = IllegalStateException("boom")

        val result = attempt<Unit> { throw boom }

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun rethrowsCancellationSoCoroutinesCanStop() = runTest {
        assertFailsWith<CancellationException> {
            attempt<Unit> { throw CancellationException("stop") }
        }
    }
}
