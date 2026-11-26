package com.biggemott.chatdemo.ui.chat

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biggemott.chatdemo.R
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import java.time.ZoneId
import java.time.ZoneOffset

private val chatHeaderHeight = 56.dp

@Composable
internal fun ChatRoute(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel, onBack) {
            ChatActions(
                viewModel::onDraftChanged,
                viewModel::onSend,
                viewModel::onRecoverDraft,
                viewModel::onOutgoingScrolled,
                onBack,
            )
        }
    ChatScreen(state, actions)
}

@Composable
internal fun ChatScreen(
    state: ChatUiState,
    actions: ChatActions = ChatActions(),
    zone: ZoneId = ZoneId.systemDefault(),
    listState: LazyListState = rememberLazyListState(),
    animateTyping: Boolean = true,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ChatHeader(state.chat.otherUser.displayName, actions.onBack)
            Box(Modifier.weight(1f)) {
                ChatHistoryPane(
                    history = state.history,
                    chat = state.chat,
                    zoneId = zone.id,
                    scroll = rememberChatScroll(listState, state.outgoingScrollKey, actions.onOutgoingScrolled),
                    typing =
                        when {
                            !state.isReplyPending -> null
                            animateTyping -> Typing.Animated
                            else -> Typing.Static
                        },
                )
                ChatTypingAnnouncement(state.chat.otherUser.displayName, state.isReplyPending, Modifier.align(Alignment.BottomStart))
            }
            ChatComposer(
                draft = state.draft,
                onDraftChanged = actions.onDraftChanged,
                onSend = actions.onSend?.takeIf { state.canSend },
                sendError = state.sendError,
                onRecoverDraft = actions.onRecoverDraft.takeIf { state.canRecoverDraft },
            )
        }
    }
}

@Composable
private fun ChatHeader(
    displayName: String,
    onBack: () -> Unit,
) {
    val minimumWindowHeight = chatHeaderHeight + minimumComposerHeight() + scrollButtonSize
    Column(
        Modifier.layout { measurable, constraints ->
            if (constraints.maxHeight < minimumWindowHeight.roundToPx()) {
                layout(0, 0) {}
            } else {
                val header = measurable.measure(constraints)
                layout(header.width, header.height) { header.placeRelative(0, 0) }
            }
        },
    ) {
        ChatHeaderContent(displayName, onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ChatHeaderContent(
    displayName: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = chatHeaderHeight)
                .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_chat_back),
                contentDescription = stringResource(R.string.chat_back),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayName.take(1),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Preview(name = "Light", widthDp = 393, heightDp = 760, locale = "en")
@Preview(name = "Dark", widthDp = 393, heightDp = 760, locale = "en", uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class ChatThemePreviews

@ChatThemePreviews
@Composable
private fun SeededChatPreview() = PreviewChatScreen(seededPreviewChat())

@Preview(name = "Empty chat", widthDp = 393, heightDp = 760, locale = "en")
@Composable
private fun EmptyChatPreview() = PreviewChatScreen(emptyPreviewChat)

@Preview(name = "Multiline composer", widthDp = 393, heightDp = 760, locale = "en")
@Composable
private fun MultilineChatPreview() = PreviewChatScreen(seededPreviewChat().copy(draft = PREVIEW_DRAFT))

@Preview(name = "Large text", widthDp = 393, heightDp = 760, locale = "en", fontScale = 1.5f)
@Composable
private fun LargeTextPreview() = PreviewChatScreen(seededPreviewChat(lastMessageText = PREVIEW_LONG_MESSAGE))

@Preview(name = "Pending reply", widthDp = 393, heightDp = 760, locale = "en")
@Composable
private fun PendingReplyPreview() = PreviewChatScreen(pendingReplyPreviewChat())

@Preview(name = "Older history and multiline draft", widthDp = 393, heightDp = 440, locale = "en")
@Composable
private fun ReadingHistoryPreview() = PreviewChatScreen(seededPreviewChat().copy(draft = PREVIEW_TALL_DRAFT), firstVisibleIndex = 2)

@Composable
private fun PreviewChatScreen(
    state: ChatUiState,
    firstVisibleIndex: Int = 0,
) {
    ChatDemoTheme {
        ChatScreen(
            state = state,
            actions = ChatActions(onSend = {}),
            zone = ZoneOffset.UTC,
            listState = rememberLazyListState(initialFirstVisibleItemIndex = firstVisibleIndex),
            animateTyping = false,
        )
    }
}
