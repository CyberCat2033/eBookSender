package com.cybercat.ebooksender.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AdaptiveSingleLineText(text: String, compactText: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        var useCompactText by remember(text, compactText, maxWidth) {
            mutableStateOf(false)
        }

        Text(
            text = if (useCompactText) compactText else text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            onTextLayout = { layoutResult ->
                if (!useCompactText && layoutResult.hasVisualOverflow) {
                    useCompactText = true
                }
            }
        )
    }
}
