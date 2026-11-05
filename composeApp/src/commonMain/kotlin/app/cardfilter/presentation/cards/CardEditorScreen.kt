package app.cardfilter.presentation.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.cardfilter.designsystem.CardImage
import app.cardfilter.designsystem.ConfirmDeleteDialog
import app.cardfilter.designsystem.DraftTextField
import app.cardfilter.designsystem.EmptyState
import app.cardfilter.designsystem.Spacing
import app.cardfilter.domain.validation.AttributeField
import app.cardfilter.presentation.Strings

private val ImageHeaderHeight = 200.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorScreen(
    viewModel: CardEditorViewModel,
    isNewCard: Boolean,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onClose()
    }
    LaunchedEffect(state.failure) {
        if (state.failure) {
            snackbarHostState.showSnackbar(Strings.actionFailed)
            viewModel.onFailureShown()
        }
    }

    // Only intercept back while there is something to lose; otherwise the system default pops.
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = state.isDirty,
        onBackCompleted = viewModel::onBackClick,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewCard) Strings.addCard else Strings.editCard) },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.back)
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::onSave,
                        enabled = !state.isLoading && !state.loadFailed && !state.isSaving,
                    ) {
                        Text(Strings.save)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.loadFailed -> EmptyState(
                title = Strings.cardLoadFailedTitle,
                message = Strings.cardLoadFailedMessage,
                modifier = Modifier.padding(padding),
            )
            else -> Column(Modifier.padding(padding)) {
                if (isDownloadingImage(state)) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                EditorForm(state, viewModel, isNewCard, Modifier.weight(1f))
            }
        }
    }

    if (state.showDiscardConfirm) {
        ConfirmDeleteDialog(
            title = Strings.discardChangesTitle,
            message = Strings.discardChangesMessage,
            confirmLabel = Strings.discard,
            dismissLabel = Strings.keepEditing,
            onConfirm = viewModel::onDiscardConfirmed,
            onDismiss = viewModel::onDiscardCancelled,
        )
    }
}

@Composable
private fun EditorForm(
    state: CardEditorUiState,
    viewModel: CardEditorViewModel,
    isNewCard: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.hasImage) {
            item(key = "image") {
                // Read-only: replacing a card's image is out of scope.
                CardImage(
                    imageKey = "card-image",
                    load = viewModel::loadImage,
                    contentDescription = Strings.cardImage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ImageHeaderHeight)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        item(key = "title") {
            DraftTextField(
                initialText = state.title,
                label = Strings.cardTitle,
                onTextChange = viewModel::onTitleChanged,
                errorText = state.titleError?.let(Strings::errorFor),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isNewCard) {
            item(key = "imageUrl") {
                // Only a new card takes an image; a stored card's image is never replaced here.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    DraftTextField(
                        initialText = state.imageUrl,
                        label = Strings.imageUrl,
                        onTextChange = viewModel::onImageUrlChanged,
                        singleLine = true,
                        enabled = !state.isSaving,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (isDownloadingImage(state)) Strings.downloadingImage else Strings.imageUrlHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(state.rows, key = { it.key }) { row ->
            AttributeRowEditor(
                row = row,
                onNameChange = { viewModel.onNameChanged(row.key, it) },
                onValueChange = { viewModel.onValueChanged(row.key, it) },
                onRemove = { viewModel.onRemoveRow(row.key) },
            )
        }
        item(key = "add") {
            OutlinedButton(
                onClick = viewModel::onAddRow,
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(Strings.addAttribute)
            }
        }
    }
}

@Composable
private fun AttributeRowEditor(
    row: AttributeRow,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(
                start = Spacing.lg,
                top = Spacing.sm,
                end = Spacing.xs,
                bottom = Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                DraftTextField(
                    initialText = row.name,
                    label = Strings.attributeName,
                    onTextChange = onNameChange,
                    errorText = row.errorText(AttributeField.Name),
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(top = Spacing.sm),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = Strings.removeAttribute)
                }
            }
            DraftTextField(
                initialText = row.value,
                label = Strings.attributeValue,
                onTextChange = onValueChange,
                errorText = row.errorText(AttributeField.Value),
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(end = Spacing.md),
            )
        }
    }
}

/** Saving a new card that has an image URL: the card is not stored until the download ends. */
private fun isDownloadingImage(state: CardEditorUiState) = state.isSaving && state.imageUrl.isNotBlank()

private fun AttributeRow.errorText(field: AttributeField): String? =
    if (field in errors) Strings.attributeErrorFor(field) else null
