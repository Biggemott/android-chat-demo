package com.biggemott.chatdemo.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biggemott.chatdemo.data.fixedDemoChat
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
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
                ChatScreen(state, onBack = {}, onDraftChanged = {})
            }
        }

        val outgoing = boundsOf(fromMe)
        val incoming = boundsOf(fromSarah)
        val nextIncoming = boundsOf(followUp)
        assertTrue("Outgoing bubble sits to the right", outgoing.left > incoming.left)
        assertTrue("Older bubble sits above the next one", outgoing.bottom < incoming.top)
        assertTrue("Grouped bubbles have less space between them", nextIncoming.top - incoming.bottom < incoming.top - outgoing.bottom)
    }

    private fun boundsOf(text: String) =
        compose
            .onNodeWithText(text)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
}
