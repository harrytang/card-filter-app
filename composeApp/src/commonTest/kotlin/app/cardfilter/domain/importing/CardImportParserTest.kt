package app.cardfilter.domain.importing

import app.cardfilter.domain.model.Attribute
import app.cardfilter.testutil.WakodoSampleJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private fun parsed(text: String): List<ImportEntry> =
    assertIs<ImportParse.Parsed>(parseCardImport(text)).entries

private fun invalid(text: String): ImportError =
    assertIs<ImportParse.Invalid>(parseCardImport(text)).error

class CardImportParserTest {
    @Test
    fun parses_aWakodoStyleEntry() {
        val entries = parsed(
            """[{"name":"Wakodo A","image":"https://example.com/a.png","ingredients":"rice, milk","month":7}]""",
        )

        assertEquals(
            listOf(
                ImportEntry(
                    title = "Wakodo A",
                    attributes = listOf(Attribute("ingredients", "rice, milk"), Attribute("month", "7")),
                    imageUrl = "https://example.com/a.png",
                ),
            ),
            entries,
        )
    }

    @Test
    fun theRealWakodoSample_parsesToSixCardsWithTheirImagesIngredientsAndMonths() {
        val entries = parsed(WakodoSampleJson)

        assertEquals(6, entries.size)
        assertEquals(
            listOf("40194820", "40603500", "40194840", "40194860", "40194870", "40194880").map {
                "https://www.wakodo.co.jp/product/image/pdt/$it.jpg"
            },
            entries.map { it.imageUrl },
        )
        assertEquals(
            listOf("7", "7", "9", "9", "12", "12"),
            entries.map { entry -> entry.attributes.single { it.name == "month" }.value },
        )
        entries.forEach { entry ->
            assertEquals(listOf("ingredients", "month"), entry.attributes.map { it.name })
        }
    }

    @Test
    fun theRealWakodoSample_keepsTheJapaneseAndEnglishTextUnchanged() {
        val first = parsed(WakodoSampleJson).first()

        assertEquals(
            "１食分の野菜入り そのまま素材 定番野菜 [Ready-to-use Ingredients with One Meal's Serving of Vegetables: Classic Vegetables]",
            first.title,
        )
        assertEquals(
            "にんじん（国産） [Carrot (grown in Japan)], たまねぎペースト [Onion paste], じゃがいも [Potato], " +
                "かつお昆布だし [Bonito and kombu kelp stock], でん粉 [Starch]",
            first.attributes.single { it.name == "ingredients" }.value,
        )
    }

    @Test
    fun theRealWakodoSample_hasSixDifferentNames() {
        assertEquals(6, parsed(WakodoSampleJson).map { it.title }.toSet().size)
    }

    @Test
    fun keepsTheOrderOfTheFile() {
        val entries = parsed("""[{"name":"B"},{"name":"A"},{"name":"C"}]""")

        assertEquals(listOf("B", "A", "C"), entries.map { it.title })
    }

    @Test
    fun notJson_isReported() {
        listOf("not json", "", "[1,", """[{"name":"A"}""").forEach {
            assertEquals(ImportError.NotJson, invalid(it), "input: $it")
        }
    }

    @Test
    fun jsonThatIsNotAnArray_isReported() {
        listOf("{}", "\"text\"", "5", "null").forEach {
            assertEquals(ImportError.NotAnArray, invalid(it), "input: $it")
        }
    }

    @Test
    fun emptyArray_isReported() {
        assertEquals(ImportError.Empty, invalid("[]"))
    }

    @Test
    fun moreThanTheLimit_isReported() {
        val tooMany = (1..MaxImportCards + 1).joinToString(prefix = "[", postfix = "]") { """{"name":"Card $it"}""" }

        assertEquals(ImportError.TooMany(MaxImportCards + 1), invalid(tooMany))
    }

    @Test
    fun exactlyTheLimit_isAccepted() {
        val atLimit = (1..MaxImportCards).joinToString(prefix = "[", postfix = "]") { """{"name":"Card $it"}""" }

        assertEquals(MaxImportCards, parsed(atLimit).size)
    }

    @Test
    fun entryThatIsNotAnObject_isReportedWithItsNumber() {
        assertEquals(ImportError.EntryNotObject(2), invalid("""[{"name":"A"},"B"]"""))
        assertEquals(ImportError.EntryNotObject(1), invalid("[null]"))
        assertEquals(ImportError.EntryNotObject(1), invalid("[[]]"))
    }

    @Test
    fun missingBlankOrNonTextName_isReportedWithItsNumber() {
        listOf(
            """[{}]""",
            """[{"name":""}]""",
            """[{"name":"   "}]""",
            """[{"name":null}]""",
            """[{"name":5}]""",
            """[{"name":["A"]}]""",
        ).forEach {
            assertEquals(ImportError.MissingName(1), invalid(it), "input: $it")
        }
    }

    @Test
    fun imageOfTheWrongType_isReported() {
        listOf("""{"a":1}""", "5", "true", """["x"]""").forEach {
            assertEquals(
                ImportError.WrongType(1, ImportField.Image),
                invalid("""[{"name":"A","image":$it}]"""),
                "image: $it",
            )
        }
    }

    @Test
    fun ingredientsOfTheWrongType_isReported() {
        listOf("""{"a":1}""", "true", """[["x"]]""", """["x",5]""", """["x",null]""").forEach {
            assertEquals(
                ImportError.WrongType(1, ImportField.Ingredients),
                invalid("""[{"name":"A","ingredients":$it}]"""),
                "ingredients: $it",
            )
        }
    }

    @Test
    fun monthOfTheWrongType_isReported() {
        listOf("""{"a":1}""", "true", """["7"]""").forEach {
            assertEquals(
                ImportError.WrongType(1, ImportField.Month),
                invalid("""[{"name":"A","month":$it}]"""),
                "month: $it",
            )
        }
    }

    @Test
    fun nullAndBlankOptionalFields_areAbsent() {
        val entries = parsed(
            """[
                {"name":"A","image":null,"ingredients":null,"month":null},
                {"name":"B","image":"  ","ingredients":"","month":" "},
                {"name":"C","ingredients":[],"month":""}
            ]""",
        )

        entries.forEach {
            assertNull(it.imageUrl, it.title)
            assertEquals(emptyList(), it.attributes, it.title)
        }
    }

    @Test
    fun ingredientsGivenAsAList_areJoinedWithCommas() {
        val entry = parsed("""[{"name":"A","ingredients":["rice"," milk ","","sugar"]}]""").single()

        assertEquals(listOf(Attribute("ingredients", "rice, milk, sugar")), entry.attributes)
    }

    @Test
    fun monthAsNumberAndAsText_giveTheSameValue() {
        val numeric = parsed("""[{"name":"A","month":7}]""").single()
        val text = parsed("""[{"name":"A","month":"7"}]""").single()

        assertEquals(listOf(Attribute("month", "7")), numeric.attributes)
        assertEquals(numeric.attributes, text.attributes)
    }

    @Test
    fun monthAsDecimal_isKeptAsWritten() {
        val entry = parsed("""[{"name":"A","month":7.5}]""").single()

        assertEquals(listOf(Attribute("month", "7.5")), entry.attributes)
    }

    @Test
    fun unknownKeys_areIgnored() {
        val entry = parsed("""[{"name":"A","brand":"Wakodo","tags":["x"],"month":5}]""").single()

        assertEquals(listOf(Attribute("month", "5")), entry.attributes)
    }

    @Test
    fun firstProblem_isTheOneReported() {
        assertEquals(
            ImportError.MissingName(2),
            invalid("""[{"name":"A"},{"image":5},"x"]"""),
            "an earlier entry wins over a later one",
        )
        assertEquals(
            ImportError.MissingName(1),
            invalid("""[{"image":5,"month":true}]"""),
            "within an entry the name is checked first",
        )
        assertEquals(
            ImportError.WrongType(1, ImportField.Image),
            invalid("""[{"name":"A","image":5,"month":true}]"""),
            "then image, ingredients, month",
        )
    }

    @Test
    fun valuesAreTrimmed() {
        val entry = parsed(
            """[{"name":"  A  ","image":"  https://example.com/a.png ","ingredients":" rice ","month":" 7 "}]""",
        ).single()

        assertEquals("A", entry.title)
        assertEquals("https://example.com/a.png", entry.imageUrl)
        assertEquals(listOf(Attribute("ingredients", "rice"), Attribute("month", "7")), entry.attributes)
    }

    @Test
    fun vietnameseAndJapaneseText_survivesUnchanged() {
        val entry = parsed("""[{"name":"Bánh gạo ăn dặm","ingredients":"gạo, sữa, 米粉"}]""").single()

        assertEquals("Bánh gạo ăn dặm", entry.title)
        assertEquals(listOf(Attribute("ingredients", "gạo, sữa, 米粉")), entry.attributes)
    }

    @Test
    fun leadingByteOrderMark_isIgnored() {
        assertEquals(listOf("A"), parsed("\uFEFF" + """[{"name":"A"}]""").map { it.title })
    }

    @Test
    fun duplicateKeysInOneEntry_lastOneWins() {
        assertEquals(listOf("B"), parsed("""[{"name":"A","name":"B"}]""").map { it.title })
    }

    @Test
    fun sizeCheck_acceptsUpToTheLimitAndRejectsMore() {
        assertNull(checkImportSize(MaxImportFileBytes.toLong()))
        assertEquals(ImportError.FileTooLarge, checkImportSize(MaxImportFileBytes + 1L))
    }
}
