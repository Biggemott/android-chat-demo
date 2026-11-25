package com.biggemott.chatdemo.ui.chat

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.biggemott.chatdemo.data.demoSeedMessages
import com.biggemott.chatdemo.data.fixedDemoChat
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import java.time.Instant
import java.time.ZoneOffset

internal val emptyPreviewChat = ChatUiState(fixedDemoChat, ChatHistory.Content(emptyList()))

internal fun seededPreviewChat(lastMessageText: String? = null): ChatUiState {
    val seed = demoSeedMessages(Instant.parse("2026-05-14T14:29:00Z"))
    val messages =
        seed.mapIndexed { index, message ->
            Message(
                id = MessageId(index + 1L),
                conversationId = fixedDemoChat.conversation.id,
                senderId = message.senderId,
                text = if (index == seed.lastIndex && lastMessageText != null) lastMessageText else message.text,
                sentAt = message.sentAt,
            )
        }
    return emptyPreviewChat.copy(history = ChatHistory.Content(mapChatTimeline(messages, fixedDemoChat.currentUser.id)))
}

internal const val PREVIEW_DRAFT = "I have the atlas.\nI'll bring it to the harbor."
internal const val PREVIEW_LONG_MESSAGE = "Meet at the steps. 🗺️\nBring the map with the harbor trail marked in blue."

@Preview(name = "Seeded chat", widthDp = 393, heightDp = 760, locale = "en")
@Preview(name = "Dark chat", widthDp = 393, heightDp = 760, locale = "en", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SeededChatPreview() {
    PreviewChatScreen(seededPreviewChat())
}

@Preview(name = "Empty chat", widthDp = 393, heightDp = 760, locale = "en")
@Composable
internal fun EmptyChatPreview() {
    PreviewChatScreen(emptyPreviewChat)
}

@Preview(name = "Multiline composer", widthDp = 393, heightDp = 760, locale = "en")
@Composable
internal fun ComposerPreview() {
    PreviewChatScreen(seededPreviewChat().copy(draft = PREVIEW_DRAFT), onSend = {})
}

@Preview(name = "Large text", widthDp = 393, heightDp = 760, locale = "en", fontScale = 1.5f)
@Composable
internal fun LargeTextPreview() {
    PreviewChatScreen(seededPreviewChat(lastMessageText = PREVIEW_LONG_MESSAGE))
}

@Composable
private fun PreviewChatScreen(
    state: ChatUiState,
    onSend: (() -> Unit)? = null,
) {
    ChatDemoTheme {
        ChatScreen(
            state = state,
            onBack = {},
            onDraftChanged = {},
            onSend = onSend,
            zone = ZoneOffset.UTC,
        )
    }
}
