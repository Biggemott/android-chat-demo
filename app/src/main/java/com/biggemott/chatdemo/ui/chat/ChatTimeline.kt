package com.biggemott.chatdemo.ui.chat

import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.domain.UserId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface ChatTimelineItem {
    val key: String

    data class Bubble(
        val message: Message,
        val isOutgoing: Boolean,
        val groupPosition: GroupPosition,
    ) : ChatTimelineItem {
        override val key = "message:${message.id.value}"
    }

    data class TimeSection(
        val firstMessageId: MessageId,
        val startsAt: Instant,
    ) : ChatTimelineItem {
        override val key = "section:${firstMessageId.value}"

        fun label(locale: Locale, zone: ZoneId): String =
            DateTimeFormatter.ofPattern("EEEE HH:mm", locale).withZone(zone).format(startsAt)
    }
}

enum class GroupPosition {
    Single,
    First,
    Middle,
    Last,
}

fun mapChatTimeline(messages: List<Message>, currentUserId: UserId): List<ChatTimelineItem> =
    buildList {
        messages.forEachIndexed { index, message ->
            if (index == 0) {
                add(ChatTimelineItem.TimeSection(message.id, message.sentAt))
            }
            add(ChatTimelineItem.Bubble(message, message.senderId == currentUserId, GroupPosition.Single))
        }
    }
