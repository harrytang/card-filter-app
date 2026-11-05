package app.cardfilter.data

import app.cardfilter.data.db.AppDatabase
import app.cardfilter.domain.importing.CardWrite
import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.model.TopicId
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class TestDatabase(clock: () -> Long) {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        .also { AppDatabase.Schema.create(it) }
    val database = AppDatabase(driver)
    val topics = SqlTopicRepository(database, Dispatchers.Unconfined, clock)
    val cards = SqlCardRepository(database, Dispatchers.Unconfined, clock)

    /** Every stored attribute row, including any that no card owns (orphans). */
    fun attributeRowCount(): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM card_attribute",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(checkNotNull(cursor.getLong(0)))
        },
        parameters = 0,
    ).value

    /** Every stored image row, including any that no card owns (orphans). */
    fun imageRowCount(): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM card_image",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(checkNotNull(cursor.getLong(0)))
        },
        parameters = 0,
    ).value

    /** Runs raw SQL, for setting up a database fault that the app itself cannot produce. */
    fun execute(sql: String) {
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }

    fun close() = driver.close()
}

private fun ticking(): () -> Long {
    var tick = 0L
    return { tick++ }
}

class SqlTopicRepositoryTest {
    private val db = TestDatabase(ticking())

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun create_returnsTopicWithGeneratedId() = runTest {
        val first = db.topics.create("Pigeon")
        val second = db.topics.create("Wakodo")

        assertEquals(Topic(TopicId(1), "Pigeon"), first)
        assertEquals(Topic(TopicId(2), "Wakodo"), second)
    }

    @Test
    fun observeAll_listsTopicsInCreationOrder() = runTest {
        db.topics.create("B")
        db.topics.create("A")

        assertEquals(listOf("B", "A"), db.topics.observeAll().first().map { it.name })
    }

    @Test
    fun observeAll_ordersByInsertionWhenTimestampsTie() = runTest {
        val sameInstant = TestDatabase { 1_000L }
        sameInstant.topics.create("first")
        sameInstant.topics.create("second")

        assertEquals(listOf("first", "second"), sameInstant.topics.observeAll().first().map { it.name })
        sameInstant.close()
    }

    @Test
    fun rename_updatesNameAndObserveEmitsIt() = runTest {
        val topic = db.topics.create("Old")

        db.topics.rename(topic.id, "New")

        assertEquals(Topic(topic.id, "New"), db.topics.observe(topic.id).first())
    }

    @Test
    fun observe_unknownIdEmitsNull() = runTest {
        assertNull(db.topics.observe(TopicId(99)).first())
    }

    @Test
    fun delete_removesTheTopicItsCardsAndTheirAttributesButNotOtherTopics() = runTest {
        val dropped = db.topics.create("Drop")
        val kept = db.topics.create("Keep")
        db.cards.create(dropped.id, "gone 1", listOf(Attribute("a", "1"), Attribute("b", "2")))
        db.cards.create(dropped.id, "gone 2", listOf(Attribute("c", "3")))
        val survivor = db.cards.create(kept.id, "stays", listOf(Attribute("d", "4")))

        db.topics.delete(dropped.id)

        assertEquals(listOf(kept), db.topics.observeAll().first())
        assertTrue(db.cards.observeByTopic(dropped.id).first().isEmpty())
        assertEquals(listOf(survivor), db.cards.observeByTopic(kept.id).first())
        assertEquals(1L, db.attributeRowCount(), "only the surviving card's attribute may remain")
    }

    @Test
    fun delete_removesTheImagesOfItsCardsButNotOtherTopicsImages() = runTest {
        val doomed = db.topics.create("Doomed")
        val survivor = db.topics.create("Survivor")
        db.cards.create(doomed.id, "d1", emptyList(), byteArrayOf(1))
        db.cards.create(doomed.id, "d2", emptyList(), byteArrayOf(2))
        val kept = db.cards.create(survivor.id, "s1", emptyList(), byteArrayOf(3))

        db.topics.delete(doomed.id)

        assertEquals(1L, db.imageRowCount(), "only the surviving topic's image may remain")
        assertContentEquals(byteArrayOf(3), db.cards.getImage(kept.id))
    }

    @Test
    fun roundTrip_keepsVietnameseAndJapaneseTextExactly() = runTest {
        db.topics.create("thịt bầm")
        db.topics.create("ベビーフード たまご 鶏")

        assertEquals(
            listOf("thịt bầm", "ベビーフード たまご 鶏"),
            db.topics.observeAll().first().map { it.name },
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SqlCardRepositoryTest {
    private val db = TestDatabase(ticking())
    private val topicA = TopicId(1)
    private val topicB = TopicId(2)

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun create_returnsCardWithGeneratedIdTopicAndAttributes() = runTest {
        val attributes = listOf(Attribute("thành phần", "thịt bầm"), Attribute("tuổi", "7 tháng"))

        val card = db.cards.create(topicA, "Cháo thịt bầm", attributes)

        assertEquals(Card(CardId(1), topicA, "Cháo thịt bầm", attributes), card)
    }

    @Test
    fun create_storesAttributesInTheOrderGiven() = runTest {
        val attributes = listOf(Attribute("z", "1"), Attribute("a", "2"), Attribute("m", "3"))
        val card = db.cards.create(topicA, "Order", attributes)

        assertEquals(attributes, db.cards.get(card.id)?.attributes)
        assertEquals(attributes, db.cards.observeByTopic(topicA).first().single().attributes)
    }

    @Test
    fun observeByTopic_returnsOnlyThatTopicsCardsInCreationOrder() = runTest {
        db.cards.create(topicA, "A1", listOf(Attribute("k", "a1")))
        db.cards.create(topicB, "B1", listOf(Attribute("k", "b1")))
        db.cards.create(topicA, "A2", listOf(Attribute("k", "a2")))

        assertEquals(listOf("A1", "A2"), db.cards.observeByTopic(topicA).first().map { it.title })
        assertEquals(listOf("B1"), db.cards.observeByTopic(topicB).first().map { it.title })
    }

    @Test
    fun observeByTopic_keepsEachCardsOwnAttributesAndCardsWithoutAny() = runTest {
        val first = db.cards.create(topicA, "first", listOf(Attribute("a", "1"), Attribute("b", "2")))
        val bare = db.cards.create(topicA, "bare", emptyList())
        val last = db.cards.create(topicA, "last", listOf(Attribute("c", "3")))

        assertEquals(listOf(first, bare, last), db.cards.observeByTopic(topicA).first())
    }

    @Test
    fun get_returnsTheCardWithAttributesOrNullWhenUnknown() = runTest {
        val card = db.cards.create(topicA, "Cháo", listOf(Attribute("tuổi", "7 tháng")))
        db.cards.create(topicA, "Other", listOf(Attribute("x", "y")))

        assertEquals(card, db.cards.get(card.id))
        assertNull(db.cards.get(CardId(99)))
    }

    @Test
    fun update_replacesTitleAndEveryAttributeOfThatCardOnly() = runTest {
        val target = db.cards.create(topicA, "Old", listOf(Attribute("a", "1"), Attribute("b", "2")))
        val other = db.cards.create(topicA, "Other", listOf(Attribute("x", "y")))

        db.cards.update(target.id, "たまご", listOf(Attribute("c", "3")))

        assertEquals(
            listOf(target.copy(title = "たまご", attributes = listOf(Attribute("c", "3"))), other),
            db.cards.observeByTopic(topicA).first(),
        )
        assertEquals(2L, db.attributeRowCount(), "replaced rows must not linger or duplicate")
    }

    @Test
    fun update_toNoAttributesClearsThem() = runTest {
        val card = db.cards.create(topicA, "Cháo", listOf(Attribute("a", "1")))

        db.cards.update(card.id, "Cháo", emptyList())

        assertTrue(db.cards.get(card.id)?.attributes?.isEmpty() == true)
        assertEquals(0L, db.attributeRowCount())
    }

    @Test
    fun observeByTopic_emitsAgainAfterAnAttributeOnlyChange() = runTest {
        val card = db.cards.create(topicA, "Cháo", listOf(Attribute("a", "1")))
        val emissions = mutableListOf<List<Card>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            db.cards.observeByTopic(topicA).toList(emissions)
        }

        db.cards.update(card.id, "Cháo", listOf(Attribute("a", "2")))

        assertEquals(listOf(Attribute("a", "2")), emissions.last().single().attributes)
    }

    @Test
    fun delete_removesOnlyThatCardAndItsAttributes() = runTest {
        val drop = db.cards.create(topicA, "Drop", listOf(Attribute("a", "1"), Attribute("b", "2")))
        val keep = db.cards.create(topicA, "Keep", listOf(Attribute("c", "3")))

        db.cards.delete(drop.id)

        assertEquals(listOf(keep), db.cards.observeByTopic(topicA).first())
        assertEquals(1L, db.attributeRowCount())
    }

    @Test
    fun create_withImage_reportsHasImageAndStoresTheBytes() = runTest {
        val image = byteArrayOf(1, 2, 3, -1)

        val created = db.cards.create(topicA, "Cháo", emptyList(), image)

        assertTrue(created.hasImage)
        assertContentEquals(image, db.cards.getImage(created.id))
        assertTrue(checkNotNull(db.cards.get(created.id)).hasImage)
        assertTrue(db.cards.observeByTopic(topicA).first().single().hasImage)
    }

    @Test
    fun create_withoutImage_hasNoImageAndStoresNoRow() = runTest {
        val created = db.cards.create(topicA, "Cháo", listOf(Attribute("tuổi", "7 tháng")))

        assertFalse(created.hasImage)
        assertNull(db.cards.getImage(created.id))
        assertFalse(db.cards.observeByTopic(topicA).first().single().hasImage)
        assertEquals(0L, db.imageRowCount())
    }

    @Test
    fun getImage_unknownCard_returnsNull() = runTest {
        assertNull(db.cards.getImage(CardId(99)))
    }

    @Test
    fun create_twoCards_eachKeepsItsOwnImage() = runTest {
        val first = db.cards.create(topicA, "first", emptyList(), byteArrayOf(1))
        val second = db.cards.create(topicA, "second", emptyList(), byteArrayOf(2, 2))

        assertContentEquals(byteArrayOf(1), db.cards.getImage(first.id))
        assertContentEquals(byteArrayOf(2, 2), db.cards.getImage(second.id))
    }

    @Test
    fun create_storesAZeroByteImageAsGiven() = runTest {
        // Size and "is it really an image" checks belong to ingestion, not to the repository.
        val created = db.cards.create(topicA, "empty", emptyList(), ByteArray(0))

        assertTrue(created.hasImage)
        assertContentEquals(ByteArray(0), db.cards.getImage(created.id))
    }

    @Test
    fun update_keepsTheCardsImage() = runTest {
        val image = byteArrayOf(9, 8, 7)
        val card = db.cards.create(topicA, "Old", listOf(Attribute("a", "1")), image)

        db.cards.update(card.id, "New", listOf(Attribute("b", "2")))

        val updated = checkNotNull(db.cards.get(card.id))
        assertEquals("New", updated.title)
        assertTrue(updated.hasImage)
        assertContentEquals(image, db.cards.getImage(card.id))
        assertEquals(1L, db.imageRowCount())
    }

    @Test
    fun delete_removesTheCardsImageRowButNotOtherCardsImages() = runTest {
        val doomed = db.cards.create(topicA, "doomed", emptyList(), byteArrayOf(1))
        val survivor = db.cards.create(topicA, "survivor", emptyList(), byteArrayOf(2))

        db.cards.delete(doomed.id)

        assertEquals(1L, db.imageRowCount(), "no orphan image row may remain")
        assertNull(db.cards.getImage(doomed.id))
        assertContentEquals(byteArrayOf(2), db.cards.getImage(survivor.id))
    }

    @Test
    fun importCards_createsCardsWithTheirAttributesAndImages() = runTest {
        db.cards.importCards(
            topicA,
            listOf(
                CardWrite.Create("A", listOf(Attribute("month", "7")), byteArrayOf(1)),
                CardWrite.Create("B", emptyList(), null),
            ),
        )

        val cards = db.cards.observeByTopic(topicA).first()
        assertEquals(listOf("A", "B"), cards.map { it.title })
        assertEquals(listOf(Attribute("month", "7")), cards[0].attributes)
        assertEquals(listOf(true, false), cards.map { it.hasImage })
        assertContentEquals(byteArrayOf(1), db.cards.getImage(cards[0].id))
        assertEquals(1L, db.imageRowCount())
    }

    @Test
    fun importCards_overwrite_keepsIdAndListPositionAndReplacesTitleAndAllAttributes() = runTest {
        val first = db.cards.create(topicA, "A", listOf(Attribute("old", "1"), Attribute("older", "2")))
        db.cards.create(topicA, "B", emptyList())

        db.cards.importCards(
            topicA,
            listOf(CardWrite.Overwrite(first.id, "A renamed", listOf(Attribute("new", "3")), null)),
        )

        val cards = db.cards.observeByTopic(topicA).first()
        assertEquals(listOf("A renamed", "B"), cards.map { it.title }, "the card keeps its place in the list")
        assertEquals(first.id, cards.first().id)
        assertEquals(listOf(Attribute("new", "3")), cards.first().attributes)
        assertEquals(1L, db.attributeRowCount(), "the replaced attribute rows must not linger")
    }

    @Test
    fun importCards_overwriteWithAnImage_replacesTheOldImage() = runTest {
        val card = db.cards.create(topicA, "A", emptyList(), byteArrayOf(1))

        db.cards.importCards(topicA, listOf(CardWrite.Overwrite(card.id, "A", emptyList(), byteArrayOf(2, 2))))

        assertContentEquals(byteArrayOf(2, 2), db.cards.getImage(card.id))
        assertEquals(1L, db.imageRowCount())
    }

    @Test
    fun importCards_overwriteWithAnImage_givesAnImageToACardThatHadNone() = runTest {
        val card = db.cards.create(topicA, "A", emptyList())

        db.cards.importCards(topicA, listOf(CardWrite.Overwrite(card.id, "A", emptyList(), byteArrayOf(5))))

        assertTrue(db.cards.get(card.id)?.hasImage == true)
        assertContentEquals(byteArrayOf(5), db.cards.getImage(card.id))
    }

    @Test
    fun importCards_overwriteWithoutAnImage_keepsTheOldImage() = runTest {
        val card = db.cards.create(topicA, "A", emptyList(), byteArrayOf(1))

        db.cards.importCards(topicA, listOf(CardWrite.Overwrite(card.id, "A2", emptyList(), null)))

        assertContentEquals(byteArrayOf(1), db.cards.getImage(card.id))
        assertEquals(1L, db.imageRowCount())
    }

    @Test
    fun importCards_mixesCreatesAndOverwritesInOneCall() = runTest {
        val existing = db.cards.create(topicA, "Existing", listOf(Attribute("k", "v")))

        db.cards.importCards(
            topicA,
            listOf(
                CardWrite.Create("Fresh", listOf(Attribute("a", "1")), null),
                CardWrite.Overwrite(existing.id, "Existing", listOf(Attribute("k", "v2")), null),
            ),
        )

        val cards = db.cards.observeByTopic(topicA).first()
        assertEquals(listOf("Existing", "Fresh"), cards.map { it.title })
        assertEquals(listOf(Attribute("k", "v2")), cards[0].attributes)
    }

    @Test
    fun importCards_whenAWriteFails_noneOfTheCallIsStored() = runTest {
        val old = db.cards.create(topicA, "Old", listOf(Attribute("k", "v")), byteArrayOf(1))
        db.execute(
            "CREATE TRIGGER fail_on_boom BEFORE INSERT ON card_attribute WHEN NEW.value = 'boom' " +
                "BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )

        assertFailsWith<Exception> {
            db.cards.importCards(
                topicA,
                listOf(
                    CardWrite.Create("New", listOf(Attribute("k", "fine")), byteArrayOf(9)),
                    CardWrite.Overwrite(old.id, "Changed", listOf(Attribute("k", "changed")), byteArrayOf(8)),
                    CardWrite.Create("Bad", listOf(Attribute("k", "boom")), null),
                ),
            )
        }

        val cards = db.cards.observeByTopic(topicA).first()
        assertEquals(listOf("Old"), cards.map { it.title }, "no created card and no renamed card may remain")
        assertEquals(listOf(Attribute("k", "v")), cards.single().attributes)
        assertContentEquals(byteArrayOf(1), db.cards.getImage(old.id))
        assertEquals(1L, db.imageRowCount())
        assertEquals(1L, db.attributeRowCount())
    }

    @Test
    fun roundTrip_keepsVietnameseJapaneseAndNewlinesInAttributesExactly() = runTest {
        val attributes = listOf(
            Attribute("thành phần", "thịt bầm\ncà rốt"),
            Attribute("アレルゲン", "卵　たまご（卵）鶏肉 とり"),
        )
        val card = db.cards.create(topicA, "ベビーフード", attributes)

        assertEquals(attributes, db.cards.get(card.id)?.attributes)
    }
}
