package app.cardfilter

import app.cardfilter.data.SqlCardRepository
import app.cardfilter.data.SqlQuickFilterRepository
import app.cardfilter.data.KtorImageDownloader
import app.cardfilter.data.SqlTopicRepository
import app.cardfilter.data.db.AppDatabase
import app.cardfilter.domain.usecase.AddCard
import app.cardfilter.domain.usecase.CreateTopic
import app.cardfilter.domain.usecase.DeleteCard
import app.cardfilter.domain.usecase.DeleteQuickFilter
import app.cardfilter.domain.usecase.DeleteTopic
import app.cardfilter.domain.usecase.EditQuickFilter
import app.cardfilter.domain.usecase.EditCard
import app.cardfilter.domain.usecase.FetchCardImage
import app.cardfilter.domain.usecase.GetCard
import app.cardfilter.domain.usecase.GetCardImage
import app.cardfilter.domain.usecase.ImportCards
import app.cardfilter.domain.usecase.PrepareCardImport
import app.cardfilter.domain.usecase.ObserveCards
import app.cardfilter.domain.usecase.ObserveQuickFilters
import app.cardfilter.domain.usecase.SaveQuickFilter
import app.cardfilter.domain.usecase.ObserveTopic
import app.cardfilter.domain.usecase.ObserveTopics
import app.cardfilter.domain.usecase.RenameTopic
import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock

/** Offline fails fast on a normal device; these only bite on a captive portal or a very slow link. */
private const val ImageConnectTimeoutMillis = 10_000L
private const val ImageRequestTimeoutMillis = 30_000L

/**
 * Hand-wired dependencies. The platform entry point supplies the [SqlDriver]
 * (Android and iOS each create their own), everything else is shared.
 */
class AppContainer(driver: SqlDriver) {
    private val database = AppDatabase(driver)
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
    private val topics = SqlTopicRepository(database, Dispatchers.Default, now)
    private val cards = SqlCardRepository(database, Dispatchers.Default, now)
    private val quickFilters = SqlQuickFilterRepository(database, Dispatchers.Default, now)

    // No engine is named: Ktor picks OkHttp on Android and Darwin on iOS from the platform dependencies.
    private val httpClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = ImageConnectTimeoutMillis
            requestTimeoutMillis = ImageRequestTimeoutMillis
        }
    }
    private val fetchCardImage = FetchCardImage(KtorImageDownloader(httpClient))

    val observeTopics = ObserveTopics(topics)
    val observeTopic = ObserveTopic(topics)
    val createTopic = CreateTopic(topics)
    val renameTopic = RenameTopic(topics)
    val deleteTopic = DeleteTopic(topics)

    val observeCards = ObserveCards(cards)
    val getCard = GetCard(cards)
    val getCardImage = GetCardImage(cards)
    val addCard = AddCard(cards, fetchCardImage)
    val prepareCardImport = PrepareCardImport(cards)
    val importCards = ImportCards(cards, fetchCardImage)
    val editCard = EditCard(cards)
    val deleteCard = DeleteCard(cards)

    val observeQuickFilters = ObserveQuickFilters(quickFilters)
    val saveQuickFilter = SaveQuickFilter(quickFilters)
    val editQuickFilter = EditQuickFilter(quickFilters)
    val deleteQuickFilter = DeleteQuickFilter(quickFilters)
}
