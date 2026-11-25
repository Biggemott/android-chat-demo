package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.biggemott.chatdemo.R
import com.biggemott.chatdemo.domain.DemoChat
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun ChatHistoryPane(
    history: ChatHistory,
    chat: DemoChat,
    locale: Locale,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    when (history) {
        ChatHistory.Loading -> {
            ChatHistoryStatus(stringResource(R.string.chat_loading), modifier)
        }

        ChatHistory.Unavailable -> {
            ChatHistoryStatus(stringResource(R.string.chat_unavailable), modifier)
        }

        is ChatHistory.Content -> {
            if (history.items.isEmpty()) {
                ChatHistoryStatus(stringResource(R.string.chat_empty), modifier)
            } else {
                ChatTimeline(history.items, chat, listState, modifier) { it.label(locale, zone) }
            }
        }
    }
}

@Composable
private fun ChatHistoryStatus(
    message: String,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private const val BUBBLE_WIDTH_FRACTION = 0.8f
private val historyGutter = 16.dp

@Composable
private fun ChatTimeline(
    timeline: List<ChatTimelineItem>,
    chat: DemoChat,
    listState: LazyListState,
    modifier: Modifier,
    timeLabel: (ChatTimelineItem.TimeSection) -> String,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bubbleWidth = ((maxWidth - historyGutter * 2) * BUBBLE_WIDTH_FRACTION).coerceIn(0.dp, 340.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = historyGutter, vertical = 12.dp),
        ) {
            items(
                items = timeline.asReversed(),
                key = ChatTimelineItem::key,
                contentType = { it::class },
            ) { item ->
                when (item) {
                    is ChatTimelineItem.TimeSection -> {
                        TimeHeader(timeLabel(item))
                    }

                    is ChatTimelineItem.Bubble -> {
                        val sender = if (item.isOutgoing) chat.currentUser else chat.otherUser
                        MessageBubble(item, sender.displayName, bubbleWidth)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeHeader(label: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 2.dp)
                .semantics { heading() },
    )
}

@Composable
private fun MessageBubble(
    bubble: ChatTimelineItem.Bubble,
    senderName: String,
    maxWidth: Dp,
) {
    val colors = MaterialTheme.colorScheme
    val outgoing = bubble.isOutgoing
    val groupedWithPrevious = bubble.groupPosition == GroupPosition.Middle || bubble.groupPosition == GroupPosition.Last
    val spokenMessage = stringResource(R.string.chat_spoken_message, senderName, bubble.message.text)
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = if (groupedWithPrevious) 3.dp else 9.dp),
        contentAlignment = if (outgoing) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft,
    ) {
        Text(
            text = bubble.message.text,
            color = if (outgoing) colors.onPrimaryContainer else colors.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            modifier =
                Modifier
                    .widthIn(max = maxWidth)
                    .background(
                        color = if (outgoing) colors.primaryContainer else colors.secondaryContainer,
                        shape = bubbleShape(bubble.groupPosition, outgoing),
                    ).semantics { contentDescription = spokenMessage }
                    .padding(horizontal = 13.dp, vertical = 9.dp),
        )
    }
}

private fun bubbleShape(
    position: GroupPosition,
    outgoing: Boolean,
): AbsoluteRoundedCornerShape {
    val round = 18.dp
    val compact = 5.dp
    val top = if (position == GroupPosition.Middle || position == GroupPosition.Last) compact else round
    val bottom = if (position == GroupPosition.First || position == GroupPosition.Middle) compact else round
    return if (outgoing) {
        AbsoluteRoundedCornerShape(topLeft = round, topRight = top, bottomRight = bottom, bottomLeft = round)
    } else {
        AbsoluteRoundedCornerShape(topLeft = top, topRight = round, bottomRight = round, bottomLeft = bottom)
    }
}
