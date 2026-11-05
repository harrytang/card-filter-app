package app.cardfilter.presentation.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cardfilter.designsystem.ConfirmDeleteDialog
import app.cardfilter.designsystem.EmptyState
import app.cardfilter.designsystem.ItemActionsMenu
import app.cardfilter.designsystem.Spacing
import app.cardfilter.designsystem.TextInputDialog
import app.cardfilter.domain.model.Topic
import app.cardfilter.domain.model.TopicId
import app.cardfilter.presentation.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicListScreen(
    viewModel: TopicListViewModel,
    onOpenTopic: (TopicId) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.failure) {
        if (state.failure) {
            snackbarHostState.showSnackbar(Strings.actionFailed)
            viewModel.onFailureShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(Strings.topicsTitle) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAddClick) {
                Icon(Icons.Default.Add, contentDescription = Strings.addTopic)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.topics.isEmpty() -> EmptyState(
                title = Strings.topicsEmptyTitle,
                message = Strings.topicsEmptyMessage,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.lg,
                    bottom = 96.dp, // keeps the last row clear of the floating button
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.topics, key = { it.id.value }) { topic ->
                    TopicRow(
                        topic = topic,
                        onOpen = { onOpenTopic(topic.id) },
                        onRename = { viewModel.onRenameClick(topic) },
                        onDelete = { viewModel.onDeleteClick(topic) },
                    )
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        TopicDialog.Create -> TextInputDialog(
            title = Strings.addTopic,
            label = Strings.topicName,
            confirmLabel = Strings.save,
            dismissLabel = Strings.cancel,
            errorText = state.titleError?.let(Strings::errorFor),
            onInputChanged = viewModel::onInputChanged,
            onConfirm = viewModel::onConfirm,
            onDismiss = viewModel::onDialogDismiss,
        )
        is TopicDialog.Rename -> TextInputDialog(
            title = Strings.renameTopic,
            label = Strings.topicName,
            initialText = dialog.topic.name,
            confirmLabel = Strings.save,
            dismissLabel = Strings.cancel,
            errorText = state.titleError?.let(Strings::errorFor),
            onInputChanged = viewModel::onInputChanged,
            onConfirm = viewModel::onConfirm,
            onDismiss = viewModel::onDialogDismiss,
        )
        is TopicDialog.ConfirmDelete -> ConfirmDeleteDialog(
            title = Strings.deleteTopic,
            message = Strings.deleteTopicMessage(dialog.topic.name),
            confirmLabel = Strings.delete,
            dismissLabel = Strings.cancel,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDialogDismiss,
        )
        null -> Unit
    }
}

@Composable
private fun TopicRow(
    topic: Topic,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget + Spacing.sm)
                .padding(start = Spacing.lg, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = topic.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ItemActionsMenu(
                menuDescription = Strings.moreActions,
                editLabel = Strings.renameTopic,
                deleteLabel = Strings.deleteTopic,
                onEdit = onRename,
                onDelete = onDelete,
            )
        }
    }
}
