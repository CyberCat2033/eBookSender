package com.cybercat.ebooksender.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.ui.AppOutlinedTextField
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed
import kotlin.math.roundToInt

@Composable
internal fun ValidatedSettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    resetKey: Any = Unit,
    leadingIcon: ImageVector? = null,
    placeholder: String = "",
    imeAction: ImeAction = ImeAction.Next,
    onPreviewChange: ((String) -> Unit)? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    validation: (String) -> String = { it },
    isSaving: Boolean = false,
    actionEnabled: Boolean = true
) {
    var textFieldValue by remember(value, resetKey) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val isChanged = textFieldValue.text != value
    val canCommitChange = isChanged && !isSaving && actionEnabled
    val scrollFocusedField = LocalSettingsFocusedFieldScroller.current
    var fieldCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun commitChange(clearFocus: Boolean) {
        if (!canCommitChange) return
        view.performHapticIfAllowed(context, true, AppHapticFeedback.Confirm)
        onValueChange(validation(textFieldValue.text))
        if (clearFocus) focusManager.clearFocus()
    }

    AppOutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            if (!isSaving) {
                textFieldValue = newValue
                onPreviewChange?.invoke(newValue.text)
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    fieldCoordinates = coordinates
                }
                .onFocusChanged { focusState ->
                    onFocusChanged(focusState.isFocused)
                    if (focusState.isFocused) {
                        fieldCoordinates?.let(scrollFocusedField)
                    }
                },
        label = { Text(label) },
        placeholderText = placeholder,
        leadingIcon =
            leadingIcon?.let { icon ->
                { Icon(icon, contentDescription = null) }
            },
        trailingIcon =
            if (isChanged || isSaving) {
                {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isSaving,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(durationMillis = 120))
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(durationMillis = 90))
                                    )
                            },
                            label = "SettingsFieldSaveState"
                        ) { saving ->
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { commitChange(clearFocus = true) },
                                    enabled = canCommitChange
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = strings.get("action_save"),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                null
            },
        readOnly = isSaving,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions =
            KeyboardActions(
                onAny = {
                    commitChange(clearFocus = false)
                    if (imeAction == ImeAction.Done) {
                        focusManager.clearFocus()
                    } else {
                        defaultKeyboardAction(imeAction)
                    }
                }
            )
    )
}

@Composable
internal fun NamingTokensHint(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp)
        )
    }
}

internal enum class NamingTemplateSlot {
    Books,
    Documents,
    Manga
}

@Composable
internal fun NamingTokensAnchor(
    slot: NamingTemplateSlot,
    activeSlot: NamingTemplateSlot?,
    tokenHeightPx: Int,
    containerCoordinates: LayoutCoordinates?,
    onPositioned: (Int) -> Unit
) {
    val density = LocalDensity.current
    val targetHeight =
        if (slot == activeSlot) {
            if (tokenHeightPx == 0) {
                64.dp
            } else {
                with(density) { tokenHeightPx.toDp() }
            }
        } else {
            0.dp
        }
    val spacerHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec =
            spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = androidx.compose.ui.unit.Dp.VisibilityThreshold
            ),
        label = "NamingTokensAnchorHeight"
    )

    Spacer(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(spacerHeight)
                .onGloballyPositioned { coordinates ->
                    if (activeSlot == null || slot != activeSlot) return@onGloballyPositioned
                    val container = containerCoordinates ?: return@onGloballyPositioned
                    onPositioned(
                        container.localPositionOf(coordinates, Offset.Zero).y.roundToInt()
                    )
                }
    )
}

@Composable
internal fun MovingNamingTokensHint(
    text: String,
    targetOffsetY: Int,
    onHeightChanged: (Int) -> Unit
) {
    val animatedOffsetY by animateIntAsState(
        targetValue = targetOffsetY,
        animationSpec =
            spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 1
            ),
        label = "NamingTokensOffset"
    )

    NamingTokensHint(
        text = text,
        modifier =
            Modifier
                .offset { IntOffset(x = 0, y = animatedOffsetY) }
                .onSizeChanged { size -> onHeightChanged(size.height) }
                .zIndex(1f)
    )
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun NamingTemplateBlock(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onPreviewChange: (String) -> Unit,
    previewLabel: String,
    previewTemplate: String,
    exampleTokens: Map<String, String>,
    folderName: String,
    groupFolder: String,
    extension: String = "epub",
    onFocusChanged: (Boolean) -> Unit = {},
    validation: (String) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ValidatedSettingsField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            imeAction = imeAction,
            onPreviewChange = onPreviewChange,
            onFocusChanged = onFocusChanged,
            validation = validation
        )
        NamingPreview(
            label = previewLabel,
            template = previewTemplate,
            exampleTokens = exampleTokens,
            folderName = folderName,
            groupFolder = groupFolder,
            extension = extension
        )
    }
}

@Composable
internal fun NamingPreview(
    label: String,
    template: String,
    exampleTokens: Map<String, String>,
    folderName: String,
    groupFolder: String,
    extension: String = "epub"
) {
    val previewTokens = exampleTokens + ("ext" to extension)
    val rendered =
        previewTokens.entries.fold(template.ifBlank { "{title}" }) { current, (key, value) ->
            current.replace("{$key}", value)
        }
    val path = "$folderName/$groupFolder/$rendered.$extension"

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun sanitizeFolderName(input: String, fallback: String): String {
    val clean = input.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
    return if (clean.isBlank() || clean.equals("system", ignoreCase = true)) fallback else clean
}
