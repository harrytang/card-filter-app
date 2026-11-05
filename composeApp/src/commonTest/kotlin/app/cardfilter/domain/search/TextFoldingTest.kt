package app.cardfilter.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Composed vs decomposed forms and combining marks are written as escapes so an editor cannot
 * silently normalise them. `commonTest` runs on Android (JVM) and on the iOS simulator, so this
 * suite also proves the two platform normalisers agree.
 */
class TextFoldingTest {
    private val combiningAcute = "\u0301"
    private val combiningCircumflex = "\u0302"
    private val voicingMark = "\u3099"

    @Test
    fun ignoresCase() {
        assertEquals("thit", foldForSearch("THỊT"))
        assertEquals("hello 123", foldForSearch("Hello 123"))
    }

    @Test
    fun removesVietnameseDiacritics() {
        // thịt bầm
        assertEquals("thit bam", foldForSearch("thịt bầm"))
        // Cháo cá
        assertEquals("chao ca", foldForSearch("Cháo cá"))
        // Nguyễn
        assertEquals("nguyen", foldForSearch("Nguyễn"))
        // ước
        assertEquals("uoc", foldForSearch("ước"))
    }

    @Test
    fun composedAndDecomposedVietnameseFoldTheSame() {
        val composed = "ế" // ế
        val decomposed = "e$combiningCircumflex$combiningAcute"
        assertNotEquals(composed, decomposed)
        assertEquals("e", foldForSearch(composed))
        assertEquals("e", foldForSearch(decomposed))
    }

    @Test
    fun dWithStrokeBecomesPlainD() {
        assertEquals("d", foldForSearch("đ"))
        assertEquals("d", foldForSearch("Đ"))
        assertEquals("duong", foldForSearch("Đường"))
    }

    @Test
    fun hiraganaAndKatakanaFoldTheSame() {
        assertEquals(foldForSearch("たまご"), foldForSearch("タマゴ"))
        assertEquals(foldForSearch("べびーふーど"), foldForSearch("ベビーフード"))
        assertEquals(foldForSearch("ゝ"), foldForSearch("ヽ"))
    }

    @Test
    fun voicingMarksAreKept_soVoicedAndUnvoicedKanaStayDifferent() {
        assertNotEquals(foldForSearch("か"), foldForSearch("が"))
        assertNotEquals(foldForSearch("か"), foldForSearch("ガ"))
        assertNotEquals(foldForSearch("ぱ"), foldForSearch("ば"))
        assertNotEquals(foldForSearch("たまこ"), foldForSearch("たまご"))
    }

    @Test
    fun composedAndDecomposedKanaFoldTheSame() {
        val composed = "が" // が
        val decomposed = "か$voicingMark" // か + voiced sound mark
        assertNotEquals(composed, decomposed)
        assertEquals(foldForSearch(composed), foldForSearch(decomposed))
        assertEquals(foldForSearch(composed), foldForSearch("ガ")) // ガ
    }

    @Test
    fun prolongedSoundMarkIsUnchanged() {
        assertTrue('ー' in foldForSearch("ベビーフード"))
    }

    @Test
    fun kanjiIsNotLinkedToItsReading() {
        assertEquals("鶏", foldForSearch("鶏"))
        assertNotEquals(foldForSearch("鶏"), foldForSearch("とり"))
        assertNotEquals(foldForSearch("鶏"), foldForSearch("トリ"))
    }

    @Test
    fun emptyAndAccentOnlyTextFoldToNothing() {
        assertEquals("", foldForSearch(""))
        assertEquals("", foldForSearch(combiningAcute))
    }
}
