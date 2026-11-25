package com.biggemott.chatdemo.ui.chat

import com.biggemott.chatdemo.domain.ConversationId
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.domain.UserId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class ChatTimelineTest {
    @Test
    fun emptyHistoryHasNoSection() {
        assertTrue(mapChatTimeline(emptyList(), currentUser).isEmpty())
    }

    @Test
    fun singleMessageHasASectionAndIsOutgoing() {
        val items = mapChatTimeline(listOf(message(1, 0)), currentUser)

        assertEquals(listOf("section:1", "message:1"), items.map { it.key })
        val bubble = items.filterIsInstance<ChatTimelineItem.Bubble>().single()
        assertTrue(bubble.isOutgoing)
        assertEquals(GroupPosition.Single, bubble.groupPosition)
    }

    @Test
    fun preservesRepositoryOrderAndKeysWhenHistoryGrows() {
        val initial = listOf(message(4, 0, otherUser), message(9, 0))
        val original = mapChatTimeline(initial, currentUser)
        val updated = mapChatTimeline(initial + message(3, 1), currentUser)

        assertEquals(listOf("section:4", "message:4", "message:9"), original.map { it.key })
        assertEquals(original.map { it.key } + "message:3", updated.map { it.key })
        assertFalse(original.filterIsInstance<ChatTimelineItem.Bubble>().first().isOutgoing)
    }

    @Test
    fun groupingUsesBothNeighborsAndExactElapsedMilliseconds() {
        val messages =
            listOf(
                message(1, 0),
                message(2, 0),
                message(3, 19_999),
                message(4, 39_999),
                message(5, 60_000),
                message(6, 60_000, otherUser),
            )

        assertEquals(
            listOf(
                GroupPosition.First,
                GroupPosition.Middle,
                GroupPosition.Last,
                GroupPosition.Single,
                GroupPosition.Single,
                GroupPosition.Single,
            ),
            mapChatTimeline(messages, currentUser)
                .filterIsInstance<ChatTimelineItem.Bubble>()
                .map { it.groupPosition },
        )
    }

    @Test
    fun sectionsUseTheImmediatePredecessorAndRequireMoreThanAnHour() {
        val messages =
            listOf(
                message(1, 0),
                message(2, 3_599_999, otherUser),
                message(3, 7_199_999),
                message(4, 10_800_000, otherUser),
                message(5, 14_400_001, otherUser),
            )

        assertEquals(
            listOf("section:1", "message:1", "message:2", "message:3", "section:4", "message:4", "section:5", "message:5"),
            mapChatTimeline(messages, currentUser).map { it.key },
        )
    }

    @Test
    fun crossingMidnightAloneKeepsTheSameSection() {
        val beforeMidnight = message(1, 0).copy(sentAt = Instant.parse("2026-01-01T23:59:59Z"))
        val afterMidnight = message(2, 0).copy(sentAt = Instant.parse("2026-01-02T00:00:00Z"))

        assertEquals(
            listOf("section:1", "message:1", "message:2"),
            mapChatTimeline(listOf(beforeMidnight, afterMidnight), currentUser).map { it.key },
        )
    }

    @Test
    fun theSameSectionFormatsForEachLocaleAndZone() {
        val message = message(1, 0).copy(sentAt = Instant.parse("2026-01-01T11:59:00Z"))
        val section =
            mapChatTimeline(listOf(message), currentUser)
                .filterIsInstance<ChatTimelineItem.TimeSection>()
                .single()

        assertEquals("Thursday 11:59", timeSectionFormatter(Locale.ENGLISH, ZoneOffset.UTC).format(section.startsAt))
        assertEquals("vendredi 01:59", timeSectionFormatter(Locale.FRENCH, ZoneId.of("Pacific/Kiritimati")).format(section.startsAt))
    }

    private fun message(
        id: Long,
        epochMillis: Long,
        sender: UserId = currentUser,
    ) = Message(MessageId(id), ConversationId("demo"), sender, "Message $id", Instant.ofEpochMilli(epochMillis))

    private val currentUser = UserId("self")
    private val otherUser = UserId("other")
}
