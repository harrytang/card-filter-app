package app.cardfilter.domain.image

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageFormatTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String) = text.encodeToByteArray()

    @Test
    fun png_isAnImage() {
        assertTrue(looksLikeImage(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)))
    }

    @Test
    fun jpeg_isAnImage() {
        assertTrue(looksLikeImage(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)))
    }

    @Test
    fun gif87a_isAnImage() {
        assertTrue(looksLikeImage(ascii("GIF87a") + bytes(1, 0)))
    }

    @Test
    fun gif89a_isAnImage() {
        assertTrue(looksLikeImage(ascii("GIF89a") + bytes(1, 0)))
    }

    @Test
    fun webp_isAnImage() {
        assertTrue(looksLikeImage(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WEBPVP8 ")))
    }

    @Test
    fun riffThatIsNotWebp_isNotAnImage() {
        assertFalse(looksLikeImage(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WAVEfmt ")))
    }

    @Test
    fun htmlErrorPage_isNotAnImage() {
        assertFalse(looksLikeImage(ascii("<!DOCTYPE html><html><body>Not found</body></html>")))
    }

    @Test
    fun emptyBytes_isNotAnImage() {
        assertFalse(looksLikeImage(ByteArray(0)))
    }

    @Test
    fun aSingleByte_isNotAnImage() {
        assertFalse(looksLikeImage(bytes(0xFF)))
    }

    @Test
    fun headerCutShort_isNotAnImage() {
        assertFalse(looksLikeImage(bytes(0x89, 0x50, 0x4E)))
        assertFalse(looksLikeImage(bytes(0xFF, 0xD8)))
        assertFalse(looksLikeImage(ascii("GIF8")))
        assertFalse(looksLikeImage(ascii("RIFF") + bytes(0, 0, 0, 0) + ascii("WEB")))
    }

    @Test
    fun randomBytes_areNotAnImage() {
        assertFalse(looksLikeImage(bytes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)))
    }
}
