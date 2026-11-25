package com.biggemott.chatdemo.ui.chat

import com.biggemott.chatdemo.data.demoSeedMessages
import com.biggemott.chatdemo.data.fixedDemoChat
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import java.time.Instant

internal val emptyPreviewChat = ChatUiState(chat = fixedDemoChat, history = ChatHistory.Content(emptyList()))

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

internal fun pendingReplyPreviewChat(): ChatUiState = seededPreviewChat().copy(isReplyPending = true)

internal const val PREVIEW_DRAFT = "I have the atlas.\nI'll bring it to the harbor."
internal const val PREVIEW_LONG_MESSAGE = "Meet at the steps. 🗺️\nBring the map with the harbor trail marked in blue."
