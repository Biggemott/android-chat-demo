package com.biggemott.chatdemo.domain

import java.time.Instant

@JvmInline
value class UserId(val value: String)

@JvmInline
value class ConversationId(val value: String)

@JvmInline
value class MessageId(val value: Long)

data class User(val id: UserId, val displayName: String)

data class Conversation(val id: ConversationId)

data class ConversationParticipant(val conversationId: ConversationId, val userId: UserId)

data class DemoChat(val conversation: Conversation, val currentUser: User, val otherUser: User)

data class NewMessage(
    val conversationId: ConversationId,
    val senderId: UserId,
    val text: String,
)

data class Message(
    val id: MessageId,
    val conversationId: ConversationId,
    val senderId: UserId,
    val text: String,
    val sentAt: Instant,
)
