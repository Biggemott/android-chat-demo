package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.biggemott.chatdemo.R
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme

private val composerRowPadding = 8.dp
private val composerSidePadding = 16.dp
private val errorVerticalPadding = 4.dp
private val minimumTouchTarget = 48.dp

@Composable
private fun minimumComposerHeight(): Dp {
    val lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
    val padding = OutlinedTextFieldDefaults.contentPadding()
    // One readable line, the outlined field's default insets, the surrounding row and its divider.
    val textHeight = with(LocalDensity.current) { lineHeight.toDp() }
    val line = textHeight + padding.calculateTopPadding() + padding.calculateBottomPadding()
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
    val canRecover = onRecoverDraft != null
    val isComposerEmpty = draft.isEmpty()
    BoxWithConstraints {
        val errorLayout =
            if (sendError) {
                measureSendError(isComposerEmpty, canRecover, maxWidth, maxHeight - editorHeight)
            } else {
                null
            }
        val minimumFooter = editorHeight + (errorLayout?.height ?: 0.dp)
        val headerSpace = if (maxHeight >= minimumFooter + chatHeaderExtent + scrollButtonSize) chatHeaderExtent else 0.dp
        val heightBelowHeader = maxHeight - headerSpace
        val historySpace = headerSpace + minOf(96.dp, heightBelowHeader / 2, (heightBelowHeader - minimumFooter).coerceAtLeast(0.dp))
        val footerHeight = maxHeight - historySpace
        val compactEditor = maxHeight < editorHeight
        Column(Modifier.heightIn(max = footerHeight)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (errorLayout != null) {
                SendError(errorLayout, isComposerEmpty, onRecoverDraft, Modifier.heightIn(max = footerHeight - editorHeight))
            }
            ComposerInput(draft, onDraftChanged, onSend, compact = compactEditor)
        }
    }
}

@Composable
private fun ComposerInput(
    draft: String,
    onDraftChanged: (String) -> Unit,
    onSend: (() -> Unit)?,
    compact: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val hint = stringResource(R.string.chat_message_hint)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = composerSidePadding, vertical = if (compact) 0.dp else composerRowPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChanged,
            modifier = Modifier.weight(1f).semantics { contentDescription = hint },
            placeholder = { Text(hint, Modifier.semantics { hideFromAccessibility() }) },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
        )
        FilledIconButton(
            onClick = { onSend?.invoke() },
            enabled = onSend != null,
            modifier = Modifier.padding(bottom = if (compact) 0.dp else 4.dp).size(minimumTouchTarget),
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

private class SendErrorLayout(
    val message: String,
    val recoveryLabel: String?,
    val height: Dp,
    // A compact recovery action uses an icon instead of a text button.
    val recoveryTextMaxWidth: Dp?,
)

@Composable
private fun measureSendError(
    isComposerEmpty: Boolean,
    canRecover: Boolean,
    maxWidth: Dp,
    availableHeight: Dp,
): SendErrorLayout? {
    if (availableHeight < minimumTouchTarget) return null
    val message = stringResource(if (canRecover && !isComposerEmpty) R.string.chat_send_error_held else R.string.chat_send_error)
    val recoveryLabel = if (canRecover) stringResource(R.string.chat_recover_draft) else null
    val bannerWidth = (maxWidth - composerSidePadding * 2).coerceAtLeast(0.dp)
    val fullHeight = sendErrorHeight(message, recoveryLabel, bannerWidth) + errorVerticalPadding * 2
    val compactRecovery = canRecover && fullHeight > availableHeight
    val height =
        if (compactRecovery) {
            sendErrorHeight(message, null, (bannerWidth - minimumTouchTarget).coerceAtLeast(0.dp))
        } else {
            fullHeight
        }
    return SendErrorLayout(message, recoveryLabel, minOf(height, availableHeight), (bannerWidth / 2).takeUnless { compactRecovery })
}

@Composable
private fun sendErrorHeight(
    error: String,
    recoveryLabel: String?,
    width: Dp,
): Dp {
    val measurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val padding = ButtonDefaults.TextButtonContentPadding
    return with(LocalDensity.current) {
        val actionWidth = if (recoveryLabel != null) width / 2 else 0.dp
        val errorHeight =
            measurer
                .measure(error, typography.bodySmall, constraints = Constraints(maxWidth = (width - actionWidth).roundToPx()))
                .size.height
                .toDp()
        val labelHeight =
            if (recoveryLabel != null) {
                val horizontalPadding =
                    padding.calculateLeftPadding(LayoutDirection.Ltr) + padding.calculateRightPadding(LayoutDirection.Ltr)
                val labelWidth = (actionWidth - horizontalPadding).coerceAtLeast(0.dp)
                measurer
                    .measure(recoveryLabel, typography.labelLarge, constraints = Constraints(maxWidth = labelWidth.roundToPx()))
                    .size.height
                    .toDp() + padding.calculateTopPadding() + padding.calculateBottomPadding()
            } else {
                0.dp
            }
        // A half-width cap on the action guarantees the space used to measure the status text.
        maxOf(errorHeight, labelHeight, minimumTouchTarget)
    }
}

@Composable
private fun SendError(
    layout: SendErrorLayout,
    isComposerEmpty: Boolean,
    onRecoverDraft: (() -> Unit)?,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val verticalPadding = if (layout.recoveryTextMaxWidth == null) 0.dp else errorVerticalPadding
    Row(
        modifier =
            modifier.fillMaxWidth().padding(horizontal = composerSidePadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = layout.message,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            color = colors.error,
            style = MaterialTheme.typography.bodySmall,
            overflow = TextOverflow.Ellipsis,
        )
        if (onRecoverDraft != null && layout.recoveryLabel != null) {
            if (layout.recoveryTextMaxWidth == null) {
                IconButton(
                    onClick = onRecoverDraft,
                    enabled = isComposerEmpty,
                    modifier = Modifier.size(minimumTouchTarget),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            contentColor = colors.primary,
                            disabledContentColor = colors.onSurfaceVariant,
                        ),
                ) {
                    Icon(painterResource(R.drawable.ic_chat_recover), contentDescription = layout.recoveryLabel)
                }
            } else {
                TextButton(
                    onClick = onRecoverDraft,
                    enabled = isComposerEmpty,
                    modifier = Modifier.widthIn(max = layout.recoveryTextMaxWidth),
                    colors = ButtonDefaults.textButtonColors(disabledContentColor = colors.onSurfaceVariant),
                ) {
                    Text(layout.recoveryLabel)
                }
            }
        }
    }
}

@Preview(name = "Blank composer", widthDp = 393, locale = "en")
@Composable
private fun BlankComposerPreview() = PreviewChatComposer("")

@Preview(name = "Send enabled", widthDp = 393, heightDp = 260, locale = "en")
@Composable
private fun SendEnabledPreview() = PreviewChatComposer(PREVIEW_DRAFT)

@Preview(name = "Send error", widthDp = 393, locale = "en")
@Composable
private fun SendErrorPreview() = PreviewChatComposer("", sendError = true)

@Preview(name = "Arabic recovery, 200% text", widthDp = 320, heightDp = 480, locale = "ar-rEG", fontScale = 2f)
@Composable
private fun ArabicComposerPreview() = PreviewChatComposer(PREVIEW_ARABIC_DRAFT, sendError = true)

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
