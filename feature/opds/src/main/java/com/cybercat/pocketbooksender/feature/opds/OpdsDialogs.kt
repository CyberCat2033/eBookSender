package com.cybercat.pocketbooksender.feature.opds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.ui.AnimatedAlertDialog
import com.cybercat.pocketbooksender.ui.AppOutlinedTextField
import com.cybercat.pocketbooksender.ui.LocalDismissDialog

@Composable
internal fun AddSourceDialog(
    url: String,
    title: String,
    username: String,
    password: String,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSaveSource: () -> Unit
) {
    val strings = LocalStrings.current
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.opdsAddTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppOutlinedTextField(
                    value = url,
                    onValueChange = onUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsUrlField) },
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    placeholderText = strings.opdsUrlPlaceholder
                )
                AppOutlinedTextField(
                    value = title,
                    onValueChange = onTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsTitleField) }
                )
                AppOutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsUsernameField) }
                )
                AppOutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsPasswordField) },
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            Button(
                onClick = {
                    onSaveSource()
                    dismiss()
                },
                enabled = url.isNotBlank()
            ) {
                Text(strings.opdsBtnSave)
            }
        },
        dismissButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(onClick = dismiss) {
                Text(strings.opdsBtnCancel)
            }
        }
    )
}

@Composable
internal fun OpdsCredentialsDialog(
    sourceTitle: String,
    username: String,
    password: String,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val strings = LocalStrings.current
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.opdsAuthRequiredTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = sourceTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppOutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsUsernameLabel) },
                    leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) }
                )
                AppOutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsPasswordLabel) },
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            Button(onClick = {
                onSave()
                dismiss()
            }) {
                Text(strings.opdsAuthBtnLogin)
            }
        },
        dismissButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(onClick = dismiss) {
                Text(strings.opdsBtnCancel)
            }
        }
    )
}
