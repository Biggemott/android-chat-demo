package com.biggemott.chatdemo.domain

import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    val demoChat: DemoChat

    /** Observes persisted messages in timestamp order, breaking ties by local message ID. */
    fun observeMessages(conversationId: ConversationId): Flow<List<Message>>

    /** Persists a nonblank message from a participant and returns the stored record. */
    suspend fun sendMessage(message: NewMessage): Message
}
