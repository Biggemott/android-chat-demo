package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

internal const val TYPING_ITEM_KEY = "typing"
internal val scrollButtonSize = 48.dp
internal val historyVerticalPadding = 12.dp
internal val historyGutter = 16.dp

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
            with(density) {
                ChatScroll(
                    list,
                    outgoing,
                    40.dp.roundToPx(),
                    (typingRowHeight + historyVerticalPadding * 2).roundToPx(),
                    IntSize((scrollButtonSize + historyGutter).roundToPx(), scrollButtonSize.roundToPx()),
                )
            }
        }
    val acknowledge by rememberUpdatedState(onOutgoingScrolled)
    LaunchedEffect(scroll, outgoingKey) {
        if (outgoingKey == null) return@LaunchedEffect
        snapshotFlow {
            val layout = list.layoutInfo
            scroll.hasAppliedViewport(layout.viewportSize) && layout.viewportSize.width > 0 && layout.viewportSize.height > 0 &&
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
    private val buttonSpace: IntSize,
) {
    private var viewport by mutableStateOf(IntSize.Zero)
    private var bottomRequests by mutableIntStateOf(0)
    private var handledBottomRequests = 0
    private var previousTimeline: List<ChatTimelineItem>? = null
    private var previousTyping = false
    private var previousOutgoingKey: String? = null
    private var appliedViewport by mutableStateOf(IntSize.Zero)
    val showBottomButton by derivedStateOf {
        viewport.width >= buttonSpace.width && viewport.height >= buttonSpace.height && !isNearBottom()
    }

    fun requestBottom() {
        bottomRequests++
    }

    fun onViewportChanged(size: IntSize) {
        viewport = size
    }

    // A newly measured viewport may reveal the message before its scroll decision has run.
    fun hasAppliedViewport(size: IntSize): Boolean = size == appliedViewport

    @Composable
    fun ApplyRequests(
        timeline: List<ChatTimelineItem>,
        typing: Boolean,
    ) {
        val outgoingKey = outgoing.value
        val requests = bottomRequests
        val size = viewport
        val outgoingIndex = remember(timeline, typing, outgoingKey) { findOutgoing(timeline, typing, outgoingKey) }
        // Apply the committed content's intent before the lazy list remeasures and retains a stable key.
        // Size-only updates arrive through onSizeChanged with the same item provider.
        SideEffect {
            val contentChanged = timeline !== previousTimeline || typing != previousTyping
            val layoutChanged = contentChanged || size != appliedViewport
            val bottomRequested = requests != handledBottomRequests
            if (size.height > 0) {
                val sendRequested = layoutChanged || outgoingKey != previousOutgoingKey || bottomRequested
                when {
                    outgoingIndex >= 0 && sendRequested -> scrollToOutgoing(outgoingIndex, typing, size.height)
                    bottomRequested || (layoutChanged && shouldFollowLatest()) -> list.requestScrollToItem(0)
                }
                handledBottomRequests = requests
            }
            previousTimeline = timeline
            previousTyping = typing
            previousOutgoingKey = outgoingKey
            appliedViewport = size
        }
    }

    private fun scrollToOutgoing(
        index: Int,
        typing: Boolean,
        viewportHeight: Int,
    ) {
        val showTypingWithMessage = index == 1 && typing && viewportHeight > typingWithPadding
        list.requestScrollToItem(if (showTypingWithMessage) 0 else index)
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

    private fun shouldFollowLatest() = appliedViewport.height > 0 && !list.isScrollInProgress && isNearBottom()

    private fun isNearBottom(): Boolean {
        // A requested position can update these values before layoutInfo reflects the next measurement.
        val offset = list.firstVisibleItemScrollOffset
        return when (list.firstVisibleItemIndex) {
            0 -> {
                offset <= threshold
            }

            1 -> {
                // A short newest row can still overlap the padding immediately before the current item.
                val newest = list.layoutInfo.visibleItemsInfo.firstOrNull()
                newest != null && newest.index == 0 && newest.offset + newest.size == -offset && newest.offset >= -threshold
            }

            else -> {
                false
            }
        }
    }
}
