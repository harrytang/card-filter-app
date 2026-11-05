package app.cardfilter.data

import app.cardfilter.data.db.AppDatabase
import app.cardfilter.domain.model.Attribute
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.TopicId
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Upgrading an installed Milestone 1 database (schema version 1) must keep every card.
 * The Gradle `verifyCommonMainAppDatabaseMigration` task proves the structure matches a fresh
 * install; these tests prove the data survives.
 */
class MigrationTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    @AfterTest
    fun tearDown() = driver.close()

    /** The Milestone 1 schema, copied verbatim and deliberately not derived from the current `.sq` files. */
    private fun installMilestoneOneDatabase() {
        listOf(
            """
            CREATE TABLE topic (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE card (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                topic_id INTEGER NOT NULL REFERENCES topic(id),
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX card_topic_id ON card(topic_id)",
            "INSERT INTO topic (name, created_at) VALUES ('Pigeon', 1)",
            "INSERT INTO card (topic_id, title, created_at) VALUES (1, 'Cháo thịt bầm', 1)",
            "INSERT INTO card (topic_id, title, created_at) VALUES (1, 'ベビーフード たまご', 2)",
        ).forEach { driver.execute(null, it, 0) }
    }

    private fun cards(): SqlCardRepository =
        SqlCardRepository(AppDatabase(driver), Dispatchers.Unconfined) { 100L }

    /** The Milestone 2 schema (version 2), copied verbatim: cards with attributes, no images yet. */
    private fun installMilestoneTwoDatabase() {
        installMilestoneOneDatabase()
        listOf(
            """
            CREATE TABLE card_attribute (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                card_id INTEGER NOT NULL REFERENCES card(id),
                name TEXT NOT NULL,
                value TEXT NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX card_attribute_card_id ON card_attribute(card_id)",
            "INSERT INTO card_attribute (card_id, name, value) VALUES (1, 'tuổi', '7 tháng')",
            "INSERT INTO card_attribute (card_id, name, value) VALUES (2, 'アレルゲン', '卵')",
        ).forEach { driver.execute(null, it, 0) }
    }

    private fun imageRowCount(): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM card_image",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(checkNotNull(cursor.getLong(0)))
        },
        parameters = 0,
    ).value

    @Test
    fun schemaVersionIsThreeAfterTheCardImageMigration() {
        assertEquals(3L, AppDatabase.Schema.version)
    }

    @Test
    fun migratingFromVersionTwo_keepsCardsAndTheirAttributes() = runTest {
        installMilestoneTwoDatabase()

        AppDatabase.Schema.migrate(driver, 2, AppDatabase.Schema.version)

        assertEquals(
            listOf(
                Card(CardId(1), TopicId(1), "Cháo thịt bầm", listOf(Attribute("tuổi", "7 tháng"))),
                Card(CardId(2), TopicId(1), "ベビーフード たまご", listOf(Attribute("アレルゲン", "卵"))),
            ),
            cards().observeByTopic(TopicId(1)).first(),
        )
    }

    @Test
    fun migratingFromVersionTwo_createsAnImageTableThatAcceptsAnImageForAnOldCard() {
        installMilestoneTwoDatabase()

        AppDatabase.Schema.migrate(driver, 2, AppDatabase.Schema.version)
        driver.execute(null, "INSERT INTO card_image (card_id, bytes) VALUES (1, X'0102')", 0)

        assertEquals(1L, imageRowCount())
    }

    @Test
    fun migratingFromVersionOne_keepsExistingCardsWithNoAttributes() = runTest {
        installMilestoneOneDatabase()

        AppDatabase.Schema.migrate(driver, 1, AppDatabase.Schema.version)

        assertEquals(
            listOf(
                Card(CardId(1), TopicId(1), "Cháo thịt bầm"),
                Card(CardId(2), TopicId(1), "ベビーフード たまご"),
            ),
            cards().observeByTopic(TopicId(1)).first(),
        )
    }

    @Test
    fun migratedDatabase_acceptsAttributesOnOldAndNewCards() = runTest {
        installMilestoneOneDatabase()
        AppDatabase.Schema.migrate(driver, 1, AppDatabase.Schema.version)
        val cards = cards()

        cards.update(CardId(1), "Cháo thịt bầm", listOf(Attribute("tuổi", "7 tháng")))
        val added = cards.create(TopicId(1), "Mới", listOf(Attribute("dị ứng", "trứng")))

        assertEquals(listOf(Attribute("tuổi", "7 tháng")), cards.get(CardId(1))?.attributes)
        assertEquals(added, cards.get(added.id))
    }
}
