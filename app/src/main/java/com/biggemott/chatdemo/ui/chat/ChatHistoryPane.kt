package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biggemott.chatdemo.R
import com.biggemott.chatdemo.domain.DemoChat
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
internal fun ChatHistoryPane(
    history: ChatHistory,
    chat: DemoChat,
    zoneId: String,
    scroll: ChatScroll,
    typing: Typing? = null,
) {
    when (history) {
        ChatHistory.Loading -> {
            ChatHistoryStatus(stringResource(R.string.chat_loading))
        }

        ChatHistory.Unavailable -> {
            ChatHistoryStatus(stringResource(R.string.chat_unavailable))
        }

        is ChatHistory.Content -> {
            if (history.items.isEmpty()) {
                ChatHistoryStatus(stringResource(R.string.chat_empty))
            } else {
                val locale = LocalConfiguration.current.locales[0]
                val formatter = remember(locale, zoneId) { timeSectionFormatter(locale, ZoneId.of(zoneId)) }
                ChatTimeline(history.items, chat, scroll, typing) { formatter.format(it.startsAt) }
            }
        }
    }
}

@Composable
private fun ChatHistoryStatus(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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

@Composable
private fun ChatTimeline(
    timeline: List<ChatTimelineItem>,
    chat: DemoChat,
    scroll: ChatScroll,
    typing: Typing?,
    timeLabel: (ChatTimelineItem.TimeSection) -> String,
) {
    val newestFirst = remember(timeline) { timeline.asReversed() }
    scroll.ApplyRequests(timeline, typing != null)
    Box(Modifier.fillMaxSize().onSizeChanged(scroll::onViewportChanged)) {
        LazyColumn(
            modifier = Modifier.matchParentSize(),
            state = scroll.list,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = historyGutter, vertical = historyVerticalPadding),
        ) {
            if (typing != null) {
                item(key = TYPING_ITEM_KEY, contentType = TYPING_ITEM_KEY) {
                    ChatTypingBubble(animate = typing == Typing.Animated)
                }
            }
            items(
                items = newestFirst,
                key = ChatTimelineItem::key,
                contentType = { it::class },
            ) { item ->
                when (item) {
                    is ChatTimelineItem.TimeSection -> {
                        TimeHeader(timeLabel(item))
                    }

                    is ChatTimelineItem.Bubble -> {
                        val sender = if (item.isOutgoing) chat.currentUser else chat.otherUser
                        MessageBubble(item, sender.displayName)
                    }
                }
            }
        }
        if (scroll.showBottomButton) {
            ScrollToBottomButton(scroll::requestBottom, Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.padding(end = historyGutter).size(scrollButtonSize),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chat_bottom),
            contentDescription = stringResource(R.string.chat_scroll_to_bottom),
        )
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
) {
    val colors = MaterialTheme.colorScheme
    val outgoing = bubble.isOutgoing
    val groupedWithPrevious = bubble.groupPosition == GroupPosition.Middle || bubble.groupPosition == GroupPosition.Last
    val spokenMessage = stringResource(R.string.chat_spoken_message, senderName, bubble.message.text)
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = if (groupedWithPrevious) 3.dp else 9.dp),
        contentAlignment = if (outgoing) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft,
    ) {
        Box(
            modifier =
                Modifier
                    .limitBubbleWidth()
                    .background(
                        color = if (outgoing) colors.primaryContainer else colors.secondaryContainer,
                        shape = bubbleShape(bubble.groupPosition, outgoing),
                    ).semantics { contentDescription = spokenMessage },
        ) {
            Text(
                text = bubble.message.text,
                color = if (outgoing) colors.onPrimaryContainer else colors.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp).semantics { hideFromAccessibility() },
            )
        }
    }
}

private fun Modifier.limitBubbleWidth(): Modifier =
    layout { measurable, constraints ->
        // The lazy list has already deducted its horizontal content padding.
        val maxWidth = 340.dp.roundToPx()
        val availableWidth =
            if (constraints.hasBoundedWidth) (constraints.maxWidth * BUBBLE_WIDTH_FRACTION).roundToInt() else maxWidth
        val bubbleWidth = minOf(maxWidth, availableWidth).coerceAtLeast(constraints.minWidth)
        val bubble = measurable.measure(constraints.copy(maxWidth = bubbleWidth))
        layout(bubble.width, bubble.height) { bubble.placeRelative(0, 0) }
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

@Preview(name = "Loaded", widthDp = 393, heightDp = 460, locale = "en")
@Composable
private fun LoadedHistoryPreview() = PreviewChatHistory(seededPreviewChat().history)

@Preview(name = "Loading", widthDp = 393, heightDp = 200, locale = "en")
@Composable
private fun LoadingHistoryPreview() = PreviewChatHistory(ChatHistory.Loading)

@Preview(name = "Unavailable", widthDp = 393, heightDp = 200, locale = "en")
@Composable
private fun UnavailableHistoryPreview() = PreviewChatHistory(ChatHistory.Unavailable)

@Preview(name = "Arabic mixed text", widthDp = 320, heightDp = 460, locale = "ar-rEG")
@Composable
private fun ArabicHistoryPreview() = PreviewChatHistory(arabicPreviewChat().history)

@Composable
private fun PreviewChatHistory(history: ChatHistory) {
    ChatDemoTheme {
        ChatHistoryPane(
            history = history,
            chat = emptyPreviewChat.chat,
            zoneId = "UTC",
            scroll = rememberChatScroll(rememberLazyListState(), null) {},
        )
    }
}
