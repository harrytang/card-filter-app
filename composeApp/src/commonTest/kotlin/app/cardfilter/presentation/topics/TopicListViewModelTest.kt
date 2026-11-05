package app.cardfilter.presentation.topics

import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.model.TopicId
import app.cardfilter.domain.repository.TopicRepository
import app.cardfilter.domain.usecase.CreateTopic
import app.cardfilter.domain.usecase.DeleteTopic
import app.cardfilter.domain.usecase.ObserveTopics
import app.cardfilter.domain.usecase.RenameTopic
import app.cardfilter.domain.validation.TitleError
import app.cardfilter.testutil.FakeTopicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TopicListViewModelTest {
    private val repository = FakeTopicRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(source: TopicRepository = repository) = TopicListViewModel(
        observeTopics = ObserveTopics(source),
        createTopic = CreateTopic(source),
        renameTopic = RenameTopic(source),
        deleteTopic = DeleteTopic(source),
    )

    @Test
    fun startsEmptyAndNotLoadingOnceTheFirstListArrives() {
        val state = viewModel().state.value

        assertTrue(state.topics.isEmpty())
        assertEquals(false, state.isLoading)
        assertNull(state.dialog)
    }

    @Test
    fun addingATopic_opensDialogThenSavesTrimmedNameAndCloses() {
        val viewModel = viewModel()

        viewModel.onAddClick()
        assertEquals(TopicDialog.Create, viewModel.state.value.dialog)

        viewModel.onConfirm("  Pigeon ")

        assertEquals(listOf("Pigeon"), viewModel.state.value.topics.map { it.name })
        assertNull(viewModel.state.value.dialog)
        assertNull(viewModel.state.value.titleError)
    }

    @Test
    fun blankName_keepsDialogOpenAndShowsError() {
        val viewModel = viewModel()
        viewModel.onAddClick()

        viewModel.onConfirm("   ")

        assertEquals(TopicDialog.Create, viewModel.state.value.dialog)
        assertEquals(TitleError.Blank, viewModel.state.value.titleError)
        assertTrue(viewModel.state.value.topics.isEmpty())
    }

    @Test
    fun typingAfterAnError_clearsTheError() {
        val viewModel = viewModel()
        viewModel.onAddClick()
        viewModel.onConfirm("")

        viewModel.onInputChanged()

        assertNull(viewModel.state.value.titleError)
    }

    @Test
    fun renamingATopic_opensDialogForThatTopicThenUpdatesName() {
        val viewModel = viewModel()
        viewModel.onAddClick()
        viewModel.onConfirm("Old")
        val topic = viewModel.state.value.topics.single()

        viewModel.onRenameClick(topic)
        assertEquals(TopicDialog.Rename(topic), viewModel.state.value.dialog)

        viewModel.onConfirm("New")

        assertEquals(listOf(Topic(topic.id, "New")), viewModel.state.value.topics)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun deletingATopic_asksForConfirmationThenRemovesIt() {
        val viewModel = viewModel()
        viewModel.onAddClick()
        viewModel.onConfirm("Drop")
        val topic = viewModel.state.value.topics.single()

        viewModel.onDeleteClick(topic)
        assertEquals(TopicDialog.ConfirmDelete(topic), viewModel.state.value.dialog)
        assertEquals(1, viewModel.state.value.topics.size)

        viewModel.onDeleteConfirmed()

        assertTrue(viewModel.state.value.topics.isEmpty())
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun dismissingTheDialog_clearsDialogAndError() {
        val viewModel = viewModel()
        viewModel.onAddClick()
        viewModel.onConfirm("")

        viewModel.onDialogDismiss()

        assertNull(viewModel.state.value.dialog)
        assertNull(viewModel.state.value.titleError)
    }

    @Test
    fun aFailingRepository_setsFailureAndKeepsTheDialogOpen() {
        val failing = object : TopicRepository by repository {
            override suspend fun create(name: String): Topic = error("disk full")
        }
        val viewModel = viewModel(failing)
        viewModel.onAddClick()

        viewModel.onConfirm("Pigeon")

        assertTrue(viewModel.state.value.failure)
        assertEquals(TopicDialog.Create, viewModel.state.value.dialog)

        viewModel.onFailureShown()
        assertEquals(false, viewModel.state.value.failure)
    }

    @Test
    fun aFailingList_endsLoadingAndSetsFailure() {
        val failing = object : TopicRepository by repository {
            override fun observeAll(): Flow<List<Topic>> = flow { error("db unreadable") }
        }

        val state = viewModel(failing).state.value

        assertEquals(false, state.isLoading)
        assertTrue(state.failure)
    }

    @Test
    fun aFailingDelete_setsFailureAndKeepsTheConfirmationOpen() {
        val failing = object : TopicRepository by repository {
            override suspend fun delete(id: TopicId) {
                error("database locked")
            }
        }
        val viewModel = viewModel(failing)
        viewModel.onAddClick()
        viewModel.onConfirm("Keep")
        val topic = viewModel.state.value.topics.single()
        viewModel.onDeleteClick(topic)

        viewModel.onDeleteConfirmed()

        assertTrue(viewModel.state.value.failure)
        assertEquals(TopicDialog.ConfirmDelete(topic), viewModel.state.value.dialog)
        assertEquals(listOf(topic), viewModel.state.value.topics)
    }

    @Test
    fun confirmingWithoutTheMatchingDialog_doesNothing() {
        val viewModel = viewModel()
        viewModel.onAddClick()
        viewModel.onConfirm("Real")
        val topic = viewModel.state.value.topics.single()

        viewModel.onConfirm("ignored: no dialog is open")
        viewModel.onDeleteConfirmed()
        viewModel.onDeleteClick(topic)
        viewModel.onConfirm("ignored: a delete dialog is open")

        assertEquals(listOf(topic), viewModel.state.value.topics)
        assertEquals(TopicDialog.ConfirmDelete(topic), viewModel.state.value.dialog)
    }
}
