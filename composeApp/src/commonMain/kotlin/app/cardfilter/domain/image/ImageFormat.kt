package app.cardfilter.domain.image

/**
 * Whether [bytes] start like a PNG, JPEG, GIF or WebP file. Only the signature is read, nothing is
 * decoded: the usual bad download is an HTML error page, which this rejects. Other formats (SVG,
 * AVIF, HEIC) are not accepted.
 */
fun looksLikeImage(bytes: ByteArray): Boolean =
    bytes.startsWith(PNG) ||
        bytes.startsWith(JPEG) ||
        bytes.startsWith(GIF87A) ||
        bytes.startsWith(GIF89A) ||
        isWebp(bytes)

private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val GIF87A = "GIF87a".encodeToByteArray()
private val GIF89A = "GIF89a".encodeToByteArray()
private val RIFF = "RIFF".encodeToByteArray()
private val WEBP = "WEBP".encodeToByteArray()

// RIFF, a 4-byte size, then WEBP: a RIFF file with another type (WAV, AVI) is not an image.
private fun isWebp(bytes: ByteArray): Boolean =
    bytes.size >= 12 && bytes.startsWith(RIFF) && bytes.startsWith(WEBP, offset = 8)

private fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean =
    size >= offset + prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }
