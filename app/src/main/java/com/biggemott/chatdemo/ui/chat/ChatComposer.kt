package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.biggemott.chatdemo.R
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme

private val composerRowPadding = 8.dp
private val minimumErrorHeight = 56.dp

@Composable
internal fun minimumComposerHeight(): Dp {
    val lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
    val padding = OutlinedTextFieldDefaults.contentPadding()
    // One readable line, the outlined field's default insets, the surrounding row and its divider.
    val line = with(LocalDensity.current) { lineHeight.toDp() } + padding.calculateTopPadding() + padding.calculateBottomPadding()
    val field = maxOf(OutlinedTextFieldDefaults.MinHeight, line)
    return field + composerRowPadding * 2 + 1.dp
}

@Composable
internal fun ChatComposer(
    draft: String,
    onDraftChanged: (String) -> Unit,
    onSend: (() -> Unit)?,
    sendError: Boolean = false,
    onRecoverDraft: (() -> Unit)? = null,
) {
    val editorHeight = minimumComposerHeight()
    BoxWithConstraints {
        val showError = sendError && maxHeight >= editorHeight + minimumErrorHeight
        val minimumFooter = editorHeight + if (showError) minimumErrorHeight else 0.dp
        val historySpace = minOf(96.dp, maxHeight / 2, (maxHeight - minimumFooter).coerceAtLeast(0.dp))
        val footerHeight = maxHeight - historySpace
        Column(Modifier.heightIn(max = footerHeight)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (showError) {
                SendError(draft.isEmpty(), onRecoverDraft, Modifier.heightIn(max = footerHeight - editorHeight))
            }
            ComposerInput(draft, onDraftChanged, onSend)
        }
    }
}

@Composable
private fun ComposerInput(
    draft: String,
    onDraftChanged: (String) -> Unit,
    onSend: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val hint = stringResource(R.string.chat_message_hint)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = composerRowPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChanged,
            modifier = Modifier.weight(1f).semantics { contentDescription = hint },
            placeholder = { Text(hint) },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
        )
        FilledIconButton(
            onClick = { onSend?.invoke() },
            enabled = onSend != null,
            modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
            shape = CircleShape,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.tertiaryContainer,
                    disabledContentColor = colors.onTertiaryContainer,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chat_send),
                contentDescription = stringResource(R.string.chat_send),
            )
        }
    }
}

@Composable
private fun SendError(
    isComposerEmpty: Boolean,
    onRecoverDraft: (() -> Unit)?,
    modifier: Modifier,
) {
    Row(
        modifier =
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                stringResource(
                    if (onRecoverDraft != null && !isComposerEmpty) R.string.chat_send_error_held else R.string.chat_send_error,
                ),
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        if (onRecoverDraft != null) {
            TextButton(onClick = onRecoverDraft, enabled = isComposerEmpty) {
                Text(stringResource(R.string.chat_recover_draft))
            }
        }
    }
}

@Preview(name = "Blank composer", widthDp = 393, locale = "en")
@Composable
private fun BlankComposerPreview() = PreviewChatComposer("")

@Preview(name = "Send enabled", widthDp = 393, locale = "en")
@Composable
private fun SendEnabledPreview() = PreviewChatComposer(PREVIEW_DRAFT)

@Preview(name = "Send error", widthDp = 393, locale = "en")
@Composable
private fun SendErrorPreview() = PreviewChatComposer("", sendError = true)

@Composable
private fun PreviewChatComposer(
    draft: String,
    sendError: Boolean = false,
) {
    ChatDemoTheme {
        Surface {
            Column {
                ChatComposer(
                    draft = draft,
                    onDraftChanged = {},
                    onSend = if (draft.isNotBlank()) ({}) else null,
                    sendError = sendError,
                    onRecoverDraft = if (sendError) ({}) else null,
                )
            }
        }
    }
}
