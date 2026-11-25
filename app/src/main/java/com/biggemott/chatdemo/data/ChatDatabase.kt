package com.biggemott.chatdemo.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import java.time.Clock

@Database(
    entities = [
        UserEntity::class,
        ConversationEntity::class,
        ConversationParticipantEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class ChatDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
}

@Dao
internal interface MessageDao {
    @Query(
        "SELECT * FROM messages WHERE conversation_id = :conversationId " +
            "ORDER BY sent_at_epoch_ms ASC, id ASC",
    )
    fun observe(conversationId: String): Flow<List<MessageEntity>>

    @Insert
    suspend fun insert(message: MessageEntity): Long
}

internal fun openDemoDatabase(
    context: Context,
    clock: Clock,
    name: String = "chat-demo.db",
): ChatDatabase =
    Room
        .databaseBuilder(context, ChatDatabase::class.java, name)
        .addCallback(DemoChatSeed(clock))
        .build()
