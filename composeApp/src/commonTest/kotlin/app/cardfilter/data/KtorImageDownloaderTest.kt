package app.cardfilter.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class KtorImageDownloaderTest {
    private val url = "https://example.com/a.png"

    private fun downloader(timeoutMillis: Long = 5_000, handler: MockRequestHandler) =
        KtorImageDownloader(
            HttpClient(MockEngine(handler)) {
                install(HttpTimeout) { requestTimeoutMillis = timeoutMillis }
            },
        )

    @Test
    fun download_returnsTheBodyOfASuccessfulResponse() = runTest {
        val body = byteArrayOf(1, 2, 3, 4)
        val downloader = downloader { respond(body, HttpStatusCode.OK) }

        assertContentEquals(body, downloader.download(url, maxBytes = 100))
    }

    @Test
    fun download_returnsABodyExactlyAtTheLimit() = runTest {
        val body = ByteArray(100) { it.toByte() }
        val downloader = downloader { respond(body, HttpStatusCode.OK) }

        assertContentEquals(body, downloader.download(url, maxBytes = 100))
    }

    @Test
    fun download_failsOnNotFound() = runTest {
        val downloader = downloader { respondError(HttpStatusCode.NotFound) }

        assertFails { downloader.download(url, maxBytes = 100) }
    }

    @Test
    fun download_failsOnServerError() = runTest {
        val downloader = downloader { respondError(HttpStatusCode.InternalServerError) }

        assertFails { downloader.download(url, maxBytes = 100) }
    }

    @Test
    fun download_failsWhenContentLengthIsOverTheLimit() = runTest {
        val downloader = downloader {
            respond(
                ByteArray(10),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentLength, "5000"),
            )
        }

        assertFails { downloader.download(url, maxBytes = 100) }
    }

    @Test
    fun download_failsWhenTheBodyIsOverTheLimitWithoutAContentLength() = runTest {
        val downloader = downloader { respond(ByteArray(5_000), HttpStatusCode.OK) }

        assertFails { downloader.download(url, maxBytes = 100) }
    }

    @Test
    fun download_failsWhenTheConnectionFails() = runTest {
        val downloader = downloader { error("no route to host") }

        assertFails { downloader.download(url, maxBytes = 100) }
    }

    @Test
    fun download_failsWhenTheServerDoesNotAnswerInTime() = runTest {
        val downloader = downloader(timeoutMillis = 100) {
            delay(10_000)
            respond(ByteArray(1), HttpStatusCode.OK)
        }

        // Real time on purpose: the timeout plugin does not run on the test scheduler.
        withContext(Dispatchers.Default) {
            assertFails { downloader.download(url, maxBytes = 100) }
        }
    }

    @Test
    fun download_followsARedirect() = runTest {
        val requested = mutableListOf<String>()
        val body = byteArrayOf(9, 8, 7)
        val downloader = downloader { request ->
            requested += request.url.toString()
            if (request.url.encodedPath == "/a.png") {
                respond(
                    ByteArray(0),
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://cdn.example.com/b.png"),
                )
            } else {
                respond(body, HttpStatusCode.OK)
            }
        }

        assertContentEquals(body, downloader.download(url, maxBytes = 100))
        assertEquals(listOf(url, "https://cdn.example.com/b.png"), requested)
    }
}
