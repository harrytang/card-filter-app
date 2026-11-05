package app.cardfilter

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import app.cardfilter.designsystem.CardFilterTheme
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.TopicId
import app.cardfilter.presentation.cards.CardEditorScreen
import app.cardfilter.presentation.cards.CardEditorViewModel
import app.cardfilter.presentation.cards.CardImportViewModel
import app.cardfilter.presentation.cards.CardListScreen
import app.cardfilter.presentation.cards.CardListViewModel
import app.cardfilter.presentation.topics.TopicListScreen
import app.cardfilter.presentation.topics.TopicListViewModel

private const val TopicsRoute = "topics"
private const val TopicIdArg = "topicId"
private const val CardIdArg = "cardId"
private const val CardsRoute = "$TopicsRoute/{$TopicIdArg}"
private const val NewCardRoute = "$CardsRoute/cards/new"
private const val EditCardRoute = "$CardsRoute/cards/{$CardIdArg}/edit"

private val topicIdArgument = navArgument(TopicIdArg) { type = NavType.LongType }
private val cardIdArgument = navArgument(CardIdArg) { type = NavType.LongType }

private fun NavBackStackEntry.topicId() =
    TopicId(checkNotNull(arguments?.read { getLong(TopicIdArg) }))

private fun NavBackStackEntry.cardId() =
    CardId(checkNotNull(arguments?.read { getLong(CardIdArg) }))

@Composable
fun App(container: AppContainer) {
    CardFilterTheme {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = TopicsRoute) {
            composable(TopicsRoute) {
                val viewModel = viewModel {
                    TopicListViewModel(
                        observeTopics = container.observeTopics,
                        createTopic = container.createTopic,
                        renameTopic = container.renameTopic,
                        deleteTopic = container.deleteTopic,
                    )
                }
                TopicListScreen(
                    viewModel = viewModel,
                    onOpenTopic = { id -> navController.navigate("$TopicsRoute/${id.value}") },
                )
            }

            composable(route = CardsRoute, arguments = listOf(topicIdArgument)) { entry ->
                val topicId = entry.topicId()
                val viewModel = viewModel(key = "cards-${topicId.value}") {
                    CardListViewModel(
                        topicId = topicId,
                        observeTopic = container.observeTopic,
                        observeCards = container.observeCards,
                        deleteCard = container.deleteCard,
                        getCardImage = container.getCardImage,
                    )
                }
                val importViewModel = viewModel(key = "import-${topicId.value}") {
                    CardImportViewModel(
                        topicId = topicId,
                        prepareCardImport = container.prepareCardImport,
                        importCards = container.importCards,
                    )
                }
                CardListScreen(
                    viewModel = viewModel,
                    importViewModel = importViewModel,
                    onBack = { navController.popBackStack() },
                    onAddCard = { navController.navigate("$TopicsRoute/${topicId.value}/cards/new") },
                    onEditCard = { id ->
                        navController.navigate("$TopicsRoute/${topicId.value}/cards/${id.value}/edit")
                    },
                )
            }

            composable(route = NewCardRoute, arguments = listOf(topicIdArgument)) { entry ->
                val topicId = entry.topicId()
                val viewModel = viewModel(key = "card-editor-${topicId.value}-new") {
                    CardEditorViewModel(
                        topicId = topicId,
                        cardId = null,
                        getCard = container.getCard,
                        addCard = container.addCard,
                        editCard = container.editCard,
                        getCardImage = container.getCardImage,
                    )
                }
                CardEditorScreen(
                    viewModel = viewModel,
                    isNewCard = true,
                    onClose = { navController.popBackStack() },
                )
            }

            composable(
                route = EditCardRoute,
                arguments = listOf(topicIdArgument, cardIdArgument),
            ) { entry ->
                val topicId = entry.topicId()
                val cardId = entry.cardId()
                val viewModel = viewModel(key = "card-editor-${topicId.value}-${cardId.value}") {
                    CardEditorViewModel(
                        topicId = topicId,
                        cardId = cardId,
                        getCard = container.getCard,
                        addCard = container.addCard,
                        editCard = container.editCard,
                        getCardImage = container.getCardImage,
                    )
                }
                CardEditorScreen(
                    viewModel = viewModel,
                    isNewCard = false,
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}
