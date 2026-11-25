package com.biggemott.chatdemo.data

import com.biggemott.chatdemo.domain.ChatRepository
import com.biggemott.chatdemo.domain.ConversationId
import com.biggemott.chatdemo.domain.DemoChat
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.domain.NewMessage
import com.biggemott.chatdemo.domain.UserId
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class RoomChatRepository @Inject constructor(
    private val dao: MessageDao,
    private val clock: Clock,
) : ChatRepository {
    override val demoChat: DemoChat get() = fixedDemoChat

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> =
        dao.observe(conversationId.value)
            .map { rows -> rows.map(MessageEntity::toDomain) }
            .distinctUntilChanged()

    override suspend fun sendMessage(message: NewMessage): Message {
        require(message.text.isNotBlank()) { "Message text must not be blank" }
        val row = MessageEntity(
            conversationId = message.conversationId.value,
            senderId = message.senderId.value,
            text = message.text,
            sentAtEpochMs = clock.millis(),
        )
        val id = dao.insert(row)
        return row.copy(id = id).toDomain()
    }
}

private fun MessageEntity.toDomain() = Message(
    id = MessageId(id),
    conversationId = ConversationId(conversationId),
    senderId = UserId(senderId),
    text = text,
    sentAt = Instant.ofEpochMilli(sentAtEpochMs),
)
