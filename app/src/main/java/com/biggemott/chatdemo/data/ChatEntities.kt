package com.biggemott.chatdemo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
internal data class UserEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
)

@Entity(tableName = "conversations")
internal data class ConversationEntity(
    @PrimaryKey val id: String,
)

@Entity(
    tableName = "conversation_participants",
    primaryKeys = ["conversation_id", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
        ),
    ],
    indices = [Index("user_id")],
)
internal data class ConversationParticipantEntity(
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "user_id") val userId: String,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationParticipantEntity::class,
            parentColumns = ["conversation_id", "user_id"],
            childColumns = ["conversation_id", "sender_id"],
        ),
    ],
    indices = [
        Index(value = ["conversation_id", "sender_id"]),
        Index(value = ["conversation_id", "sent_at_epoch_ms", "id"]),
    ],
)
internal data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    val text: String,
    @ColumnInfo(name = "sent_at_epoch_ms") val sentAtEpochMs: Long,
)
