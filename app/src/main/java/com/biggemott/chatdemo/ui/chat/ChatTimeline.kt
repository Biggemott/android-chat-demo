package com.biggemott.chatdemo.ui.chat

import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.domain.UserId
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun messageKey(id: MessageId): String = "message:${id.value}"

sealed interface ChatTimelineItem {
    val key: String

    data class Bubble(
        val message: Message,
        val isOutgoing: Boolean,
        val groupPosition: GroupPosition,
    ) : ChatTimelineItem {
        override val key = messageKey(message.id)
    }

    data class TimeSection(
        val firstMessageId: MessageId,
        val startsAt: Instant,
    ) : ChatTimelineItem {
        override val key = "section:${firstMessageId.value}"
    }
}

internal fun timeSectionFormatter(
    locale: Locale,
    zone: ZoneId,
): DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE HH:mm", locale).withZone(zone)

enum class GroupPosition {
    Single,
    First,
    Middle,
    Last,
}

private const val GROUP_GAP_SECONDS = 20L
private val groupGap = Duration.ofSeconds(GROUP_GAP_SECONDS)
private val sectionGap = Duration.ofHours(1)

fun mapChatTimeline(
    messages: List<Message>,
    currentUserId: UserId,
): List<ChatTimelineItem> =
    buildList {
        var previous: Message? = null
        var joinsPrevious = false
        messages.forEachIndexed { index, message ->
            val previousMessage = previous
            if (previousMessage == null || Duration.between(previousMessage.sentAt, message.sentAt) > sectionGap) {
                add(ChatTimelineItem.TimeSection(message.id, message.sentAt))
            }

            val next = messages.getOrNull(index + 1)
            val joinsNext =
                next != null && message.senderId == next.senderId &&
                    Duration.between(message.sentAt, next.sentAt) < groupGap
            val position =
                when {
                    joinsPrevious && joinsNext -> GroupPosition.Middle
                    joinsPrevious -> GroupPosition.Last
                    joinsNext -> GroupPosition.First
                    else -> GroupPosition.Single
                }
            add(ChatTimelineItem.Bubble(message, message.senderId == currentUserId, position))
            previous = message
            joinsPrevious = joinsNext
        }
    }
