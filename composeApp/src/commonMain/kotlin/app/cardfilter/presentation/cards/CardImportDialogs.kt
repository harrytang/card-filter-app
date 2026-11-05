package app.cardfilter.presentation.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cardfilter.designsystem.ConfirmDeleteDialog
import app.cardfilter.designsystem.Spacing
import app.cardfilter.presentation.Strings

/**
 * The dialog for the current step of an import, or nothing while idle. Back or tapping outside while
 * reading or importing cancels, which is safe: nothing is written before the end.
 */
@Composable
fun CardImportDialogs(
    state: CardImportState,
    onCancel: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onDismissResult: () -> Unit,
) {
    when (state) {
        CardImportState.Idle -> Unit
        CardImportState.Reading -> ProgressDialog(
            message = Strings.importReading,
            fraction = null,
            onCancel = onCancel,
        )
        is CardImportState.Importing -> ProgressDialog(
            message = Strings.importProgress(state.done, state.total),
            fraction = if (state.total == 0) 0f else state.done.toFloat() / state.total,
            onCancel = onCancel,
        )
        is CardImportState.Confirm -> ConfirmDeleteDialog(
            title = Strings.overwriteTitle,
            message = Strings.overwriteMessage(state.overwriteCount, state.overwrittenTitles),
            confirmLabel = Strings.overwriteConfirm,
            dismissLabel = Strings.cancel,
            onConfirm = onConfirmOverwrite,
            onDismiss = onCancel,
        )
        is CardImportState.Done -> ResultDialog(
            title = Strings.importDoneTitle,
            message = Strings.importSummary(state.summary),
            onDismiss = onDismissResult,
        )
        is CardImportState.Failed -> ResultDialog(
            title = Strings.importFailedTitle,
            message = "${Strings.errorFor(state.error)}\n${Strings.importFailedNote}",
            onDismiss = onDismissResult,
        )
    }
}

@Composable
private fun ProgressDialog(message: String, fraction: Float?, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(Strings.importTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(message)
                if (fraction == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(Strings.cancel) } },
    )
}

@Composable
private fun ResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Strings.ok) } },
    )
}

/** Where the user pastes the JSON. Import stays disabled until something is entered. */
@Composable
fun PasteJsonDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.pasteJsonTitle) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(Strings.pasteJsonHint) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text(Strings.importCards) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } },
    )
}
