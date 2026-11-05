package app.cardfilter.data

import app.cardfilter.domain.image.ImageDownloader
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray

/**
 * Downloads with [client], which carries the timeouts (see `AppContainer`). Every problem is thrown;
 * the use case that calls this decides that it means "no image".
 */
class KtorImageDownloader(private val client: HttpClient) : ImageDownloader {
    override suspend fun download(url: String, maxBytes: Int): ByteArray =
        client.prepareGet(url).execute { response ->
            check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
            // Refuse a declared oversize body before reading any of it.
            check((response.contentLength() ?: 0L) <= maxBytes) { "Image is larger than $maxBytes bytes" }

            // Read one byte past the limit: enough to know it is too big, never the whole body.
            val bytes = response.bodyAsChannel().readBuffer(maxBytes + 1L).readByteArray()
            check(bytes.size <= maxBytes) { "Image is larger than $maxBytes bytes" }
            bytes
        }
}
