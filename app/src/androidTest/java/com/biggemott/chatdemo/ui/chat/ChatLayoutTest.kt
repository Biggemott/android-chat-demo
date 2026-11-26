package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biggemott.chatdemo.data.fixedDemoChat
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class ChatLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun alignsBySenderAndKeepsTheAdjacentGroupCloserTogether() {
        val chat = fixedDemoChat
        val fromMe = "The atlas is ready."
        val fromSarah = "I'm in."
        val followUp = "See you soon."
        val startedAt = Instant.parse("2026-05-14T11:59:00Z")
        val replyAt = startedAt.plus(1, ChronoUnit.MINUTES)
        val messages =
            listOf(
                Message(MessageId(1), chat.conversation.id, chat.currentUser.id, fromMe, startedAt),
                Message(MessageId(2), chat.conversation.id, chat.otherUser.id, fromSarah, replyAt),
                Message(MessageId(3), chat.conversation.id, chat.otherUser.id, followUp, replyAt.plusSeconds(1)),
            )
        val state = ChatUiState(chat, ChatHistory.Content(mapChatTimeline(messages, chat.currentUser.id)))
        compose.setContent {
            ChatDemoTheme {
                ChatScreen(state)
            }
        }

        val outgoing = boundsOf(fromMe)
        val incoming = boundsOf(fromSarah)
        val nextIncoming = boundsOf(followUp)
        assertTrue("Outgoing bubble sits to the right", outgoing.left > incoming.left)
        assertTrue("Older bubble sits above the next one", outgoing.bottom < incoming.top)
        assertTrue("Grouped bubbles have less space between them", nextIncoming.top - incoming.bottom < incoming.top - outgoing.bottom)
    }

    @Test
    fun nearBottomFollowsTypingReplyAndViewportWhileDraftEditsKeepTheOffset() {
        val chat = fixedDemoChat
        val messages =
            List(24) { index ->
                Message(MessageId(index + 1L), chat.conversation.id, chat.otherUser.id, "Note ${index + 1}", Instant.EPOCH)
            }
        var state by mutableStateOf(ChatUiState(chat, ChatHistory.Content(mapChatTimeline(messages, chat.currentUser.id))))
        var height by mutableStateOf(340.dp)
        val list = LazyListState()
        compose.setContent {
            ChatDemoTheme {
                Box(Modifier.height(height)) {
                    ChatScreen(state, listState = list, animateTyping = false)
                }
            }
        }
        val liveRegion = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        val liveRegionId = liveRegion.fetchSemanticsNode().id
        compose.runOnIdle { list.requestScrollToItem(0, 14) }
        compose.runOnIdle {
            assertEquals(14, list.firstVisibleItemScrollOffset)
            state = state.copy(draft = "Keep reading")
        }
        compose.runOnIdle {
            assertEquals(14, list.firstVisibleItemScrollOffset)
            state = state.copy(isReplyPending = true)
        }
        compose.onAllNodesWithContentDescription("Sarah is typing…").assertCountEquals(1)
        assertEquals(liveRegionId, liveRegion.fetchSemanticsNode().id)
        compose.runOnIdle {
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
            val reply = messages.last().copy(id = MessageId(25), text = "New reply")
            state =
                state.copy(
                    history = ChatHistory.Content(mapChatTimeline(messages + reply, chat.currentUser.id)),
                    isReplyPending = false,
                )
        }
        compose.onNodeWithText("New reply").assertIsDisplayed()
        compose.runOnIdle { list.requestScrollToItem(0, 14) }
        compose.runOnIdle {
            assertEquals(14, list.firstVisibleItemScrollOffset)
            height = 280.dp
            state = state.copy(draft = "A first line\nA second line\nA third line")
        }
        compose.runOnIdle {
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
        }
        compose.runOnIdle {
            list.requestScrollToItem(8)
            height = 230.dp
            state = state.copy(draft = PREVIEW_TALL_DRAFT, isReplyPending = true)
        }
        assertCompactHistory(list)
        compose.onAllNodesWithContentDescription("Sarah is typing…").assertCountEquals(1)
    }

    @Test
    fun veryShortViewportKeepsAReadableComposerAndFullSendTarget() {
        var state by mutableStateOf(seededPreviewChat().copy(draft = PREVIEW_TALL_DRAFT))
        compose.setContent {
            ChatDemoTheme {
                Box(Modifier.height(165.dp)) {
                    ChatScreen(state, actions = ChatActions(onSend = {}))
                }
            }
        }
        assertReadableComposer()
        compose.runOnIdle { state = state.copy(sendError = true, canRecoverDraft = true) }
        assertReadableComposer()
    }

    private fun assertReadableComposer() {
        val minFieldHeight = with(compose.density) { 56.dp.roundToPx() }
        val minTouchHeight = with(compose.density) { 48.dp.roundToPx() }
        val field = compose.onNodeWithContentDescription("Message").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithContentDescription("Send message").fetchSemanticsNode().boundsInRoot
        assertTrue("The field keeps room for text and padding", field.height >= minFieldHeight - 0.5f)
        assertTrue("Send keeps its 48 dp height", send.height >= minTouchHeight - 0.5f)
    }

    private fun assertCompactHistory(list: LazyListState) {
        val minViewport = with(compose.density) { 48.dp.roundToPx() }
        compose.runOnIdle {
            assertTrue("Older messages remain visible in the short viewport", list.layoutInfo.viewportSize.height >= minViewport)
            assertTrue("Viewport resize preserves the older anchor", list.firstVisibleItemIndex >= 8)
        }
        compose.onNodeWithContentDescription("Scroll to latest message").assertIsDisplayed()
    }

    private fun boundsOf(text: String) =
        compose
            .onNodeWithText(text)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
}
