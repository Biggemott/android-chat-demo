package com.biggemott.chatdemo.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.biggemott.chatdemo.domain.Conversation
import com.biggemott.chatdemo.domain.ConversationId
import com.biggemott.chatdemo.domain.ConversationParticipant
import com.biggemott.chatdemo.domain.DemoChat
import com.biggemott.chatdemo.domain.User
import com.biggemott.chatdemo.domain.UserId
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

internal val fixedDemoChat = DemoChat(
    conversation = Conversation(ConversationId("demo")),
    currentUser = User(UserId("nikita"), "Nikita"),
    otherUser = User(UserId("sarah"), "Sarah"),
)

internal data class DemoSeedMessage(val senderId: UserId, val text: String, val sentAt: Instant)

internal fun demoSeedMessages(createdAt: Instant): List<DemoSeedMessage> {
    val nikita = fixedDemoChat.currentUser.id
    val sarah = fixedDemoChat.otherUser.id
    val atlasFoundAt = createdAt.minus(149, ChronoUnit.MINUTES)
    val agreedAt = createdAt.minus(3, ChronoUnit.MINUTES)
    return listOf(
        DemoSeedMessage(sarah, "Did the library have that atlas?", createdAt.minus(150, ChronoUnit.MINUTES)),
        DemoSeedMessage(nikita, "Found it on the second floor.", atlasFoundAt),
        DemoSeedMessage(nikita, "It still has the foldout maps!", atlasFoundAt.plusSeconds(8)),
        DemoSeedMessage(sarah, "Where should we take it first?", createdAt.minus(5, ChronoUnit.MINUTES)),
        DemoSeedMessage(
            nikita,
            "There's a footpath from the old lighthouse down to the harbor.",
            createdAt.minus(4, ChronoUnit.MINUTES),
        ),
        DemoSeedMessage(sarah, "I'm in.", agreedAt),
        DemoSeedMessage(sarah, "I'll bring the thermos. It looks windy out there.", agreedAt.plusSeconds(7)),
        DemoSeedMessage(sarah, "See you at the steps.", agreedAt.plusSeconds(14)),
    )
}

internal class DemoChatSeed(private val clock: Clock) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        // The framework runs this callback within the database creation transaction.
        val messages = demoSeedMessages(clock.instant())
        val conversationId = fixedDemoChat.conversation.id
        db.execSQL("INSERT INTO conversations (id) VALUES (?)", arrayOf(conversationId.value))
        for (user in listOf(fixedDemoChat.currentUser, fixedDemoChat.otherUser)) {
            db.execSQL(
                "INSERT INTO users (id, display_name) VALUES (?, ?)",
                arrayOf(user.id.value, user.displayName),
            )
            val participant = ConversationParticipant(conversationId, user.id)
            db.execSQL(
                "INSERT INTO conversation_participants (conversation_id, user_id) VALUES (?, ?)",
                arrayOf(participant.conversationId.value, participant.userId.value),
            )
        }
        for (message in messages) {
            db.execSQL(
                "INSERT INTO messages (conversation_id, sender_id, text, sent_at_epoch_ms) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(conversationId.value, message.senderId.value, message.text, message.sentAt.toEpochMilli()),
            )
        }
    }
}
