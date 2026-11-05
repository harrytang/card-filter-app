package app.cardfilter.presentation.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cardfilter.designsystem.CardImage
import app.cardfilter.designsystem.ConfirmDeleteDialog
import app.cardfilter.designsystem.EmptyState
import app.cardfilter.designsystem.ItemActionsMenu
import app.cardfilter.designsystem.QuickFilterChip
import app.cardfilter.designsystem.SearchField
import app.cardfilter.designsystem.Spacing
import app.cardfilter.domain.model.Card
import app.cardfilter.domain.importing.ImportFailure
import app.cardfilter.domain.importing.checkImportSize
import app.cardfilter.domain.model.CardId
import app.cardfilter.domain.model.QuickFilter
import app.cardfilter.domain.model.QuickFilterId
import app.cardfilter.presentation.Strings
import androidx.compose.material3.TextButton
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.size

/** How many `name: value` lines a card row shows before collapsing the rest into "+N more". */
private const val PreviewAttributeCount = 3

private val ThumbnailSize = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(
    viewModel: CardListViewModel,
    importViewModel: CardImportViewModel,
    onBack: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (CardId) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val importState by importViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var importMenuOpen by remember { mutableStateOf(false) }
    var pasteDialogOpen by remember { mutableStateOf(false) }
    // No file type filter: some Android file managers label .json files as plain text or a generic
    // binary, which would grey them out. A file that is not JSON is reported as such.
    val filePicker = rememberFilePickerLauncher(type = FileKitType.File()) { file ->
        if (file != null) importViewModel.onFileChosen { file.readImportText() }
    }

    LaunchedEffect(state.failure) {
        if (state.failure) {
            snackbarHostState.showSnackbar(Strings.actionFailed)
            viewModel.onFailureShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.topicName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.back)
                    }
                },
                actions = {
                    Box {
                        TextButton(
                            onClick = { importMenuOpen = true },
                            enabled = importState == CardImportState.Idle,
                        ) {
                            Text(Strings.importCards)
                        }
                        DropdownMenu(expanded = importMenuOpen, onDismissRequest = { importMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(Strings.importFromFile) },
                                onClick = {
                                    importMenuOpen = false
                                    filePicker.launch()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings.importPasteJson) },
                                onClick = {
                                    importMenuOpen = false
                                    pasteDialogOpen = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // A new card that does not match the query would vanish after saving.
                    viewModel.onQueryChange("")
                    viewModel.onNotQueryChange("")
                    onAddCard()
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = Strings.addCard)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // imePadding keeps the results and the no-results message above the keyboard while typing.
        Column(modifier = Modifier.padding(padding).imePadding()) {
            // Stays visible while the query matches nothing, so the user can always clear it.
            if (state.cards.isNotEmpty()) {
                QuickFilterRow(
                    filters = state.quickFilters,
                    activeId = state.activeQuickFilterId,
                    onApply = viewModel::onQuickFilterClick,
                    onAdd = viewModel::onAddQuickFilterClick,
                    onEdit = viewModel::onQuickFilterEditClick,
                    onDelete = viewModel::onQuickFilterDeleteClick,
                )
                // Re-created when a quick filter sets the queries: the fields keep their own text
                // while typing (for IME composition) and only read the state when they are created.
                key(state.queryRevision) {
                    SearchField(
                        initialText = state.query,
                        label = Strings.searchCards,
                        clearLabel = Strings.clearSearch,
                        onTextChange = viewModel::onQueryChange,
                        onSearch = focusManager::clearFocus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        supportingText = Strings.searchNote.takeIf { state.query.isNotBlank() },
                    )
                    SearchField(
                        initialText = state.notQuery,
                        label = Strings.notFilter,
                        clearLabel = Strings.clearNotFilter,
                        onTextChange = viewModel::onNotQueryChange,
                        onSearch = focusManager::clearFocus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        supportingText = Strings.notFilterNote.takeIf { state.notQuery.isNotBlank() },
                    )
                }
            }
            when {
                state.isLoading -> Unit
                state.cards.isEmpty() -> EmptyState(
                    title = Strings.cardsEmptyTitle,
                    message = Strings.cardsEmptyMessage,
                )
                state.visibleCards.isEmpty() -> EmptyState(
                    title = Strings.noMatchesTitle,
                    message = Strings.noMatchesMessage,
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.sm,
                        bottom = 96.dp, // keeps the last row clear of the floating button
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.visibleCards, key = { it.id.value }) { card ->
                        CardRow(
                            card = card,
                            loadImage = { viewModel.loadImage(card) },
                            onOpen = { onEditCard(card.id) },
                            onDelete = { viewModel.onDeleteClick(card) },
                        )
                    }
                }
            }
        }
    }

    if (pasteDialogOpen) {
        PasteJsonDialog(
            onImport = { text ->
                pasteDialogOpen = false
                importViewModel.onTextPasted(text)
            },
            onDismiss = { pasteDialogOpen = false },
        )
    }

    CardImportDialogs(
        state = importState,
        onCancel = importViewModel::onCancel,
        onConfirmOverwrite = importViewModel::onConfirmOverwrite,
        onDismissResult = importViewModel::onDismissResult,
    )

    when (val dialog = state.quickFilterDialog) {
        is QuickFilterDialog.Editor -> QuickFilterEditorDialog(
            editor = dialog,
            nameError = state.nameError,
            onNameChanged = viewModel::onQuickFilterNameChanged,
            onSave = viewModel::onQuickFilterSave,
            onDismiss = viewModel::onQuickFilterDialogDismiss,
        )
        is QuickFilterDialog.ConfirmDelete -> ConfirmDeleteDialog(
            title = Strings.deleteQuickFilter,
            message = Strings.deleteQuickFilterMessage(dialog.filter.name),
            confirmLabel = Strings.delete,
            dismissLabel = Strings.cancel,
            onConfirm = viewModel::onQuickFilterDeleteConfirmed,
            onDismiss = viewModel::onQuickFilterDialogDismiss,
        )
        null -> Unit
    }

    state.cardPendingDelete?.let { card ->
        ConfirmDeleteDialog(
            title = Strings.deleteCard,
            message = Strings.deleteCardMessage(card.title),
            confirmLabel = Strings.delete,
            dismissLabel = Strings.cancel,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismiss,
        )
    }
}

/** The saved filters of the topic as a sideways-scrolling row of chips, plus a chip to add one. */
@Composable
private fun QuickFilterRow(
    filters: List<QuickFilter>,
    activeId: QuickFilterId?,
    onApply: (QuickFilter) -> Unit,
    onAdd: () -> Unit,
    onEdit: (QuickFilter) -> Unit,
    onDelete: (QuickFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(filters, key = { it.id.value }) { filter ->
            QuickFilterChip(
                name = filter.name,
                selected = filter.id == activeId,
                longPressLabel = Strings.quickFilterActionsHint,
                editLabel = Strings.editQuickFilter,
                deleteLabel = Strings.deleteQuickFilter,
                onClick = { onApply(filter) },
                onEdit = { onEdit(filter) },
                onDelete = { onDelete(filter) },
            )
        }
        item(key = "add") {
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(Strings.addQuickFilter, modifier = Modifier.padding(start = Spacing.xs))
            }
        }
    }
}

/** The file's text, after checking its size so a huge file is never read into memory. */
private suspend fun PlatformFile.readImportText(): String {
    checkImportSize(size())?.let { throw ImportFailure(it) }
    return readString()
}

@Composable
private fun CardRow(
    card: Card,
    loadImage: suspend () -> ByteArray?,
    onOpen: () -> Unit,
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
            if (card.hasImage) {
                // Draws nothing at all if the stored bytes turn out not to be an image.
                CardImage(
                    imageKey = card.id.value,
                    load = loadImage,
                    // The title right beside it already names the card; a description would be read twice.
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = Spacing.sm, bottom = Spacing.sm, end = Spacing.md)
                        .size(ThumbnailSize)
                        .clip(MaterialTheme.shapes.small),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                card.attributes.take(PreviewAttributeCount).forEach { attribute ->
                    Text(
                        text = "${attribute.name}: ${attribute.value}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val hidden = card.attributes.size - PreviewAttributeCount
                if (hidden > 0) {
                    Text(
                        text = Strings.moreAttributes(hidden),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ItemActionsMenu(
                menuDescription = Strings.moreActions,
                editLabel = Strings.editCard,
                deleteLabel = Strings.deleteCard,
                onEdit = onOpen,
                onDelete = onDelete,
            )
        }
    }
}
