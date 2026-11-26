package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

internal const val TYPING_ITEM_KEY = "typing"
internal val scrollButtonSize = 48.dp

@Composable
internal fun rememberChatScroll(
    list: LazyListState,
    outgoingKey: String?,
    onOutgoingScrolled: (String) -> Unit,
): ChatScroll {
    val density = LocalDensity.current
    val outgoing = rememberUpdatedState(outgoingKey)
    val scroll =
        remember(list, density) {
            with(density) { ChatScroll(list, outgoing, 40.dp.roundToPx(), (typingRowHeight + 24.dp).roundToPx()) }
        }
    val acknowledge by rememberUpdatedState(onOutgoingScrolled)
    LaunchedEffect(list, outgoingKey) {
        if (outgoingKey == null) return@LaunchedEffect
        snapshotFlow {
            val layout = list.layoutInfo
            layout.visibleItemsInfo.any { item ->
                item.key == outgoingKey && item.offset < layout.viewportEndOffset &&
                    item.offset + item.size > layout.viewportStartOffset
            }
        }.first { it }
        acknowledge(outgoingKey)
    }
    return scroll
}

internal class ChatScroll(
    val list: LazyListState,
    private val outgoing: State<String?>,
    private val threshold: Int,
    private val typingWithPadding: Int,
) {
    private var bottomRequests by mutableIntStateOf(0)
    private var handledBottomRequests = 0
    private var previousTimeline: List<ChatTimelineItem>? = null
    private var previousTyping = false
    private var previousOutgoingKey: String? = null
    private var previousViewport = IntSize.Zero
    val showBottomButton by derivedStateOf { !isNearBottom() }

    fun requestBottom() {
        bottomRequests++
    }

    // Called before the list measures its new content, while its state still describes the old layout.
    fun prepareMeasurement(
        timeline: List<ChatTimelineItem>,
        typing: Boolean,
        viewport: IntSize,
        listHeight: Int,
    ) {
        val outgoingKey = outgoing.value
        val requests = bottomRequests
        val contentChanged = timeline !== previousTimeline || typing != previousTyping
        val viewportChanged = viewport != previousViewport
        val outgoingIndex = if (contentChanged || outgoingKey != previousOutgoingKey) findOutgoing(timeline, typing, outgoingKey) else -1
        Snapshot.withoutReadObservation {
            val followLatest =
                previousTimeline != null && (contentChanged || viewportChanged) && !list.isScrollInProgress && isNearBottom()
            when {
                outgoingIndex == 1 && typing && listHeight > typingWithPadding -> list.requestScrollToItem(0)
                outgoingIndex >= 0 -> list.requestScrollToItem(outgoingIndex)
                requests != handledBottomRequests || followLatest -> list.requestScrollToItem(0)
            }
        }
        previousTimeline = timeline
        previousTyping = typing
        previousOutgoingKey = outgoingKey
        previousViewport = viewport
        handledBottomRequests = requests
    }

    private fun findOutgoing(
        timeline: List<ChatTimelineItem>,
        typing: Boolean,
        key: String?,
    ): Int {
        if (key == null) return -1
        val position = timeline.asReversed().indexOfFirst { it.key == key }
        return if (position >= 0 && typing) position + 1 else position
    }

    // Reverse layout measures distance from the latest edge while its first row is still on screen.
    private fun isNearBottom(): Boolean = list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset <= threshold
}
