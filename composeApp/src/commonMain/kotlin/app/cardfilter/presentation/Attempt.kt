package app.cardfilter.presentation

import kotlin.coroutines.cancellation.CancellationException

/** Like `runCatching`, but never swallows coroutine cancellation. */
internal suspend inline fun <T> attempt(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Result.failure(failure)
    }
