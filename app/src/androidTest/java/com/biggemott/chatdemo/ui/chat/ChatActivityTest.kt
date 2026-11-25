package com.biggemott.chatdemo.ui.chat

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.biggemott.chatdemo.R
import com.biggemott.chatdemo.data.ChatDatabase
import com.biggemott.chatdemo.data.DemoChatSeed
import com.biggemott.chatdemo.data.RoomChatRepository
import com.biggemott.chatdemo.domain.NewMessage
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration

@RunWith(AndroidJUnit4::class)
class ChatActivityTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val clock = Clock.fixed(Instant.parse("2026-05-14T14:29:00Z"), ZoneOffset.UTC)
    private val releaseReply = CompletableDeferred<Unit>()
    private val replyDelay =
        object : ReplyDelay {
            override fun nextDuration(): Duration = Duration.ZERO

            override suspend fun await(duration: Duration) = releaseReply.await()
        }
    private lateinit var database: ChatDatabase
    private lateinit var repository: RoomChatRepository

    private val room =
        object : ExternalResource() {
            override fun before() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                database =
                    Room
                        .inMemoryDatabaseBuilder(context, ChatDatabase::class.java)
                        .addCallback(DemoChatSeed(clock))
                        .build()
                repository = RoomChatRepository(database.messages(), clock)
            }

            override fun after() {
                database.close()
            }
        }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(room).around(compose)

    @Test
    fun opensAtLatestSeededMessageAndEnablesSendOnlyForNonblankDraft() {
        render()
        waitForText("See you at the steps.")
        compose.onNodeWithText("See you at the steps.").assertIsDisplayed()
        compose.onNodeWithText("Sarah").assertIsDisplayed()

        val sectionHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and !hasText("Sarah")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(sectionHeading)

        sendButton().assertIsNotEnabled()
        composer().performTextInput(" ")
        sendButton().assertIsNotEnabled()
        composer().performTextInput("A first line\nA second line")
        compose.onNodeWithText(" A first line\nA second line").assertIsDisplayed()
        sendButton().assertIsEnabled()
    }

    @Test
    fun sendsThroughRoomAndShowsTheReplyAtTheBottomOfLongHistory() = exerciseConversation(recreate = false)

    @Test
    fun sendsThroughRoomScrollsFromOlderHistoryAndKeepsReplyAcrossActivityRecreation() = exerciseConversation(recreate = true)

    private fun exerciseConversation(recreate: Boolean) {
        val chat = repository.demoChat
        runBlocking {
            withTimeout(5_000) {
                repeat(24) {
                    repository.sendMessage(NewMessage(chat.conversation.id, chat.currentUser.id, "Earlier note ${it + 1}"))
                }
            }
        }
        val viewModel = render()
        waitForText("Earlier note 24")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Did the library have that atlas?"))
        compose.onNodeWithText("Did the library have that atlas?").assertIsDisplayed()

        val outgoing = "I have the atlas.\nSee you by the wall!"
        composer().performTextInput("  $outgoing  ")
        sendButton().performClick()
        sendButton().assertIsNotEnabled()
        waitForText(outgoing)
        assertFullyVisible(outgoing)
        waitForText("Sarah is typing…")

        if (recreate) {
            compose.activityRule.scenario.recreate()
            assertSame(viewModel, render())
            waitForText("Sarah is typing…")
        }
        releaseReply.complete(Unit)
        val reply = scriptedReplies.first()
        waitForText(reply)
        val outgoingBounds = assertFullyVisible(outgoing)
        val replyBounds = assertFullyVisible(reply)
        assertTrue("Outgoing bubble is on the physical right", outgoingBounds.left > replyBounds.left)
        assertTrue("The reply follows the outgoing message", outgoingBounds.bottom < replyBounds.top)
        compose.onNodeWithText("Sarah is typing…").assertDoesNotExist()
        val stored = runBlocking { withTimeout(5_000) { repository.observeMessages(chat.conversation.id).first() } }
        assertEquals(34, stored.size)
        assertEquals(listOf(outgoing, reply), stored.takeLast(2).map { it.text })
        assertEquals(listOf(chat.currentUser.id, chat.otherUser.id), stored.takeLast(2).map { it.senderId })
    }

    private fun render(): ChatViewModel {
        lateinit var viewModel: ChatViewModel
        compose.activityRule.scenario.onActivity { activity ->
            val factory = viewModelFactory { initializer { ChatViewModel(repository, replyDelay) } }
            viewModel = ViewModelProvider.create(activity, factory)[ChatViewModel::class]
            activity.setContent {
                ChatDemoTheme { ChatRoute(viewModel, onBack = {}) }
            }
        }
        return viewModel
    }

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun assertFullyVisible(text: String): Rect {
        val node = compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
        return compose.runOnIdle {
            val bounds = node.boundsInRoot
            assertEquals("Message must fit within the viewport", node.size.height.toFloat(), bounds.height, 0.5f)
            bounds
        }
    }

    private fun composer() = compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_message_hint))

    private fun sendButton() = compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_send))
}
