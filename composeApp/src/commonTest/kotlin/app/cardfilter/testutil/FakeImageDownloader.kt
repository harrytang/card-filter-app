package app.cardfilter.testutil

import app.cardfilter.domain.image.ImageDownloader

/** Records every request; [respond] decides what a download returns, throws or waits for. */
class FakeImageDownloader : ImageDownloader {
    val requests = mutableListOf<String>()
    var lastMaxBytes: Int? = null

    var respond: suspend (url: String) -> ByteArray = { error("no response configured for $it") }

    override suspend fun download(url: String, maxBytes: Int): ByteArray {
        requests += url
        lastMaxBytes = maxBytes
        return respond(url)
    }
}

/** The smallest bytes that [app.cardfilter.domain.image.looksLikeImage] accepts: a PNG signature. */
fun pngBytes(size: Int = 12): ByteArray {
    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    return signature + ByteArray(maxOf(0, size - signature.size))
}
