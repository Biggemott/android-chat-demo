package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biggemott.chatdemo.data.fixedDemoChat
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ChatScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private val chat = fixedDemoChat
    private val messages =
        List(36) { index ->
            Message(MessageId(index + 1L), chat.conversation.id, chat.otherUser.id, "Note ${index + 1}\nTwo lines", Instant.EPOCH)
        }
    private val list = LazyListState()
    private var state by mutableStateOf(ChatUiState(chat, history(messages)))
    private var height by mutableStateOf(360.dp)
    private lateinit var scope: CoroutineScope

    @Test
    fun switchingButtonVisibilityKeepsListGeometryAndMessageWrapping() {
        render()
        val threshold = threshold()
        position(threshold - 1)
        val viewport = compose.runOnIdle { list.layoutInfo.viewportSize }
        val textSize = compose.onNodeWithText(messages.last().text).fetchSemanticsNode().size
        bottomButton().assertDoesNotExist()

        position(threshold + 1)
        bottomButton().assertIsDisplayed()
        compose.runOnIdle { assertEquals(viewport, list.layoutInfo.viewportSize) }
        assertEquals(textSize, compose.onNodeWithText(messages.last().text).fetchSemanticsNode().size)

        position(threshold - 1)
        bottomButton().assertDoesNotExist()
        compose.runOnIdle { assertEquals(viewport, list.layoutInfo.viewportSize) }
    }

    @Test
    fun aContinuousDragCanCrossTheThresholdInBothDirections() {
        render()
        position(threshold() - 1)
        val timeline = compose.onNode(hasScrollToIndexAction())
        timeline.performTouchInput {
            down(center)
            moveBy(Offset(0f, 28.dp.toPx()))
        }
        bottomButton().assertIsDisplayed()
        timeline.performTouchInput {
            moveBy(Offset(0f, -36.dp.toPx()))
        }
        bottomButton().assertDoesNotExist()
        timeline.performTouchInput { up() }
    }

    @Test
    fun simultaneousTypingAndResizeFollowFromJustInsideTheThreshold() {
        render()
        position(threshold() - 1)
        compose.runOnIdle {
            height = 300.dp
            state = state.copy(isReplyPending = true)
        }
        compose.runOnIdle {
            assertEquals(
                TYPING_ITEM_KEY,
                list.layoutInfo.visibleItemsInfo
                    .first()
                    .key,
            )
            assertEquals(0, list.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun replyDuringAnActiveUserScrollKeepsTheReadingAnchor() {
        render()
        position(threshold() - 1)
        val scroll =
            compose.runOnIdle {
                scope.launch { list.scroll(MutatePriority.UserInput) { awaitCancellation() } }
            }
        compose.runOnIdle {
            assertTrue(list.isScrollInProgress)
            state = state.copy(history = history(messages + nextMessage()))
        }
        compose.runOnIdle {
            assertTrue("The incoming message must not cancel the active scroll", scroll.isActive)
            assertEquals(1, list.firstVisibleItemIndex)
            assertEquals(threshold() - 1, list.firstVisibleItemScrollOffset)
            scroll.cancel()
        }
        compose.runOnIdle { assertEquals(1, list.firstVisibleItemIndex) }
    }

    @Test
    fun outgoingWaitsForAVisibleViewportThenAcknowledgesTheTallMessage() {
        val acknowledged = mutableListOf<String>()
        render(ChatActions(onOutgoingScrolled = acknowledged::add))
        val sent = nextMessage().copy(senderId = chat.currentUser.id, text = (1..60).joinToString("\n") { "Line $it" })
        val key = messageKey(sent.id)
        compose.runOnIdle { height = 48.dp }
        compose.runOnIdle {
            assertEquals(0, list.layoutInfo.viewportSize.height)
            state = state.copy(history = history(messages + sent), outgoingScrollKey = key, isReplyPending = true)
        }
        compose.runOnIdle {
            assertTrue("A zero-height viewport cannot display a message", acknowledged.isEmpty())
            height = 320.dp
        }
        compose.onNodeWithText(sent.text).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(key), acknowledged) }
    }

    private fun render(actions: ChatActions = ChatActions()) {
        compose.setContent {
            scope = rememberCoroutineScope()
            ChatDemoTheme {
                Box(Modifier.height(height)) {
                    ChatScreen(state, actions = actions, listState = list, animateTyping = false)
                }
            }
        }
    }

    private fun threshold() = with(compose.density) { 40.dp.roundToPx() }

    private fun position(offset: Int) {
        compose.runOnIdle { list.requestScrollToItem(0, offset) }
        compose.runOnIdle { assertEquals(offset, list.firstVisibleItemScrollOffset) }
    }

    private fun nextMessage() = messages.last().copy(id = MessageId(37), text = "Newest message")

    private fun history(records: List<Message>) = ChatHistory.Content(mapChatTimeline(records, chat.currentUser.id))

    private fun bottomButton() = compose.onNodeWithContentDescription("Scroll to latest message")
}
