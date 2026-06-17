package com.cybercat.pocketbooksender.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AppOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholderText: String? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            )
        )
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            )
        }
    }

    AppOutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            if (newValue.text != value) {
                onValueChange(newValue.text)
            }
        },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholderText = placeholderText,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholderText: String? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    if (visualTransformation != VisualTransformation.None) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = appTextFieldPlaceholder(placeholder, placeholderText, singleLine),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = OutlinedTextFieldDefaults.colors()
    val textFieldState = rememberTextFieldState(
        initialText = value.text,
        initialSelection = value.selection,
    )
    val scrollState = rememberScrollState()
    val keyboardActionHandler = rememberKeyboardActionHandler(keyboardOptions, keyboardActions)
    val effectiveTextStyle = textStyle.withDefaultColor(
        defaultColor = when {
            !enabled -> colors.disabledTextColor
            isError -> colors.errorTextColor
            isFocused -> colors.focusedTextColor
            else -> colors.unfocusedTextColor
        }
    )

    LaunchedEffect(value) {
        val currentValue = textFieldState.toTextFieldValue()
        if (currentValue != value) {
            textFieldState.edit {
                replace(0, length, value.text)
                selection = value.selection.coerceIn(value.text.length)
            }
        }
    }

    LaunchedEffect(textFieldState, value, onValueChange) {
        snapshotFlow { textFieldState.toTextFieldValue() }
            .collect { newValue ->
                if (newValue != value) {
                    onValueChange(newValue)
                }
            }
    }

    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            state = textFieldState,
            modifier = modifier
                .then(if (label != null) Modifier.semantics(mergeDescendants = true) {} else Modifier)
                .defaultMinSize(
                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                    minHeight = OutlinedTextFieldDefaults.MinHeight,
                ),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = effectiveTextStyle,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = keyboardActionHandler,
            lineLimits = if (singleLine) {
                TextFieldLineLimits.SingleLine
            } else {
                TextFieldLineLimits.MultiLine(
                    minHeightInLines = minLines,
                    maxHeightInLines = maxLines,
                )
            },
            interactionSource = interactionSource,
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            scrollState = scrollState,
            decorator = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = textFieldState.text.toString(),
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    isError = isError,
                    label = label,
                    placeholder = appTextFieldPlaceholder(placeholder, placeholderText, singleLine),
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = isError,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = OutlinedTextFieldDefaults.shape,
                        )
                    },
                )
            },
        )
    }
}

@Composable
private fun rememberKeyboardActionHandler(
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
): KeyboardActionHandler = remember(keyboardOptions, keyboardActions) {
    KeyboardActionHandler { defaultKeyboardAction ->
        val action = when (keyboardOptions.imeAction) {
            ImeAction.Done -> keyboardActions.onDone
            ImeAction.Go -> keyboardActions.onGo
            ImeAction.Next -> keyboardActions.onNext
            ImeAction.Previous -> keyboardActions.onPrevious
            ImeAction.Search -> keyboardActions.onSearch
            ImeAction.Send -> keyboardActions.onSend
            else -> null
        }
        if (action != null) {
            action(
                object : androidx.compose.foundation.text.KeyboardActionScope {
                    override fun defaultKeyboardAction(imeAction: ImeAction) {
                        defaultKeyboardAction()
                    }
                }
            )
        } else {
            defaultKeyboardAction()
        }
    }
}

private fun appTextFieldPlaceholder(
    placeholder: @Composable (() -> Unit)?,
    placeholderText: String?,
    singleLine: Boolean,
): @Composable (() -> Unit)? =
    placeholder ?: placeholderText?.let { text ->
        {
            Text(
                text = text,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
        }
    }

private fun TextFieldState.toTextFieldValue(): TextFieldValue =
    TextFieldValue(
        text = text.toString(),
        selection = selection,
    )

private fun TextRange.coerceIn(textLength: Int): TextRange {
    val coercedStart = start.coerceIn(0, textLength)
    val coercedEnd = end.coerceIn(0, textLength)
    return TextRange(coercedStart, coercedEnd)
}

private fun TextStyle.withDefaultColor(defaultColor: Color): TextStyle =
    if (color == Color.Unspecified) {
        copy(color = defaultColor)
    } else {
        this
    }
