package com.cybercat.ebooksender.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class SourceSelectionItem(val id: String, val title: String)

@Composable
fun SourceSelectionMenu(
    selectedTitle: String,
    items: List<SourceSelectionItem>,
    enabled: Boolean,
    contentDescription: String,
    onItemSelected: (SourceSelectionItem) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector = Icons.Outlined.Folder,
    onPress: () -> Unit = {},
    trailingActions: (@Composable (SourceSelectionItem, closeMenu: () -> Unit) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                onPress()
                expanded = true
            },
            enabled = enabled && items.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { this.contentDescription = contentDescription }
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = selectedTitle,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(trailingIcon, contentDescription = null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = trailingActions?.let { actions ->
                        {
                            Row {
                                actions(item) { expanded = false }
                            }
                        }
                    },
                    onClick = {
                        onPress()
                        expanded = false
                        onItemSelected(item)
                    }
                )
            }
        }
    }
}
