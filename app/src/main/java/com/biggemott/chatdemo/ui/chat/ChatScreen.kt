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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biggemott.chatdemo.R
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
internal fun ChatRoute(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel) {
            ChatActions(viewModel::onDraftChanged, viewModel::onSend, viewModel::onRecoverDraft, viewModel::onOutgoingScrolled)
        }
    ChatScreen(state, onBack, actions)
}

@Composable
internal fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    actions: ChatActions = ChatActions(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val listState = rememberLazyListState()
    val history = state.history
    val key = state.outgoingScrollKey
    LaunchedEffect(history, key) {
        if (key == null || history !is ChatHistory.Content) return@LaunchedEffect
        val index = history.items.asReversed().indexOfFirst { it.key == key }
        if (index < 0) return@LaunchedEffect
        // Request the next measurement's index directly so stable keys and an in-progress drag cannot veto it.
        listState.requestScrollToItem(index)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == key } }.first { it }
        actions.onOutgoingScrolled(key)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ChatHeader(state.chat.otherUser.displayName, onBack)
            ChatHistoryPane(
                history = state.history,
                chat = state.chat,
                zoneId = zone.id,
                modifier = Modifier.weight(1f),
                listState = listState,
            )
            if (state.isReplyPending) {
                Text(
                    text = stringResource(R.string.chat_typing, state.chat.otherUser.displayName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                )
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

@Composable
private fun PreviewChatScreen(state: ChatUiState) {
    ChatDemoTheme {
        ChatScreen(
            state = state,
            onBack = {},
            actions = ChatActions(onSend = {}),
            zone = ZoneOffset.UTC,
        )
    }
}
