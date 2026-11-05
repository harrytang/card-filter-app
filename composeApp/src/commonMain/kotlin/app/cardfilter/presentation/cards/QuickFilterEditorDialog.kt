package app.cardfilter.presentation.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.cardfilter.designsystem.Spacing
import app.cardfilter.domain.validation.TitleError
import app.cardfilter.presentation.Strings
import androidx.compose.foundation.layout.Arrangement

/** Name, normal search words and "not" words of one quick filter. */
@Composable
internal fun QuickFilterEditorDialog(
    editor: QuickFilterDialog.Editor,
    nameError: TitleError?,
    onNameChanged: () -> Unit,
    onSave: (name: String, include: String, exclude: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(editor.name) }
    var include by rememberSaveable { mutableStateOf(editor.include) }
    var exclude by rememberSaveable { mutableStateOf(editor.exclude) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (editor.id == null) Strings.newQuickFilterTitle else Strings.editQuickFilterTitle)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onNameChanged()
                    },
                    label = { Text(Strings.quickFilterName) },
                    isError = nameError != null,
                    supportingText = nameError?.let { error -> { Text(Strings.errorFor(error)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                OutlinedTextField(
                    value = include,
                    onValueChange = { include = it },
                    label = { Text(Strings.quickFilterInclude) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = exclude,
                    onValueChange = { exclude = it },
                    label = { Text(Strings.notFilter) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, include, exclude) }) { Text(Strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } },
    )
}
