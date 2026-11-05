package app.cardfilter.domain.image

/** Largest image stored with a card. Bigger downloads are dropped (Android reads a row up to about 2 MB). */
const val MaxImageBytes = 1_000_000

interface ImageDownloader {
    /**
     * The body of [url], at most [maxBytes] long. Throws on any failure: bad address, no network,
     * timeout, a non-success status or a body over the limit. Callers decide what a failure means.
     */
    suspend fun download(url: String, maxBytes: Int): ByteArray
}
