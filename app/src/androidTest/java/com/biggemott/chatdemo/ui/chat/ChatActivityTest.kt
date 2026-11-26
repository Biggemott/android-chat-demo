package com.biggemott.chatdemo.ui.chat

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import androidx.compose.ui.test.performSemanticsAction
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
import com.biggemott.chatdemo.data.DraftStore
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
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    private val draftStore =
        object : DraftStore {
            override suspend fun restore() = ""

            override fun save(text: String) = Unit
        }

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
    fun arabicAppConfigurationSelectsArabicForNewRepliesWithoutChangingTheSeed() {
        val arabic = Locale.forLanguageTag("ar-EG")
        render(arabic)
        waitForText("See you at the steps.")
        val seed = storedMessages()
        val outgoing = "أراك عند المنارة! Lighthouse 12 🗺️\nهل أحضر الأطلس؟"
        val activity = compose.activity
        val configuration = Configuration(activity.resources.configuration).apply { setLocale(arabic) }
        val context = activity.createConfigurationContext(configuration)
        compose.onNodeWithContentDescription(context.getString(R.string.chat_message_hint)).performTextInput(outgoing)
        compose.onNodeWithContentDescription(context.getString(R.string.chat_send)).performSemanticsAction(SemanticsActions.OnClick)
        releaseReply.complete(Unit)

        val stored =
            runBlocking {
                withTimeout(5.seconds) {
                    repository.observeMessages(repository.demoChat.conversation.id).first { it.size == seed.size + 2 }
                }
            }
        assertEquals(seed, stored.dropLast(2))
        assertEquals(outgoing, stored[seed.size].text)
        assertEquals("أنا عند أسفل درج المنارة. وجدت مقعدًا في مكان محمي من الريح.", stored.last().text)
    }

    @Test
    fun messageExposesOneSpokenLabelWithSpeakerAndText() {
        render()
        waitForText("See you at the steps.")

        compose
            .onNodeWithContentDescription("Sarah. See you at the steps.")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }

    @Test
    fun sendsThroughRoomScrollsFromOlderHistoryAndKeepsReplyAcrossActivityRecreation() = exerciseConversation(recreate = true)

    @Test
    fun keepsOldHistoryThroughReplyAndRecreationUntilBottomIsRequested() {
        seedLongHistory(noteCount = 48)
        val viewModel = render()
        waitForText("Earlier note 48")
        compose.runOnIdle {
            viewModel.onDraftChanged("Waiting for Sarah")
            viewModel.onSend()
        }
        waitForText("Waiting for Sarah")
        val anchor = "Earlier note 18"
        val history = compose.onNode(hasScrollToIndexAction())

        fun anchorOffset(): Float = history.fetchSemanticsNode().boundsInRoot.bottom - assertFullyVisible(anchor).bottom

        history.performScrollToNode(hasText(anchor))
        val before = anchorOffset()
        bottomButton().assertIsDisplayed()

        releaseReply.complete(Unit)
        waitForStoredReply()
        assertEquals(before, anchorOffset(), 0.5f)

        compose.activityRule.scenario.recreate()
        assertSame(viewModel, render())
        waitForText(anchor)
        assertEquals(before, anchorOffset(), 0.5f)
        bottomButton().assertIsDisplayed().performClick()
        waitForText(scriptedReplies.first())
        assertFullyVisible(scriptedReplies.first())
        bottomButton().assertDoesNotExist()
    }

    @Test
    fun rapidSendsReachTheLatestPersistedMessageEvenWhenItIsTallerThanTheViewport() {
        seedLongHistory()
        val viewModel = render()
        waitForText("Earlier note 24")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Did the library have that atlas?"))
        val tall = (1..70).joinToString("\n") { "Long message line $it" }
        compose.runOnIdle {
            viewModel.onDraftChanged("First in the burst")
            viewModel.onSend()
            viewModel.onDraftChanged(tall)
            viewModel.onSend()
        }
        waitForText(tall)
        compose.onNodeWithText(tall).assertIsDisplayed()
        compose.waitUntil(5_000) { viewModel.uiState.value.outgoingScrollKey == null }
        assertEquals(listOf("First in the burst", tall), storedMessages().takeLast(2).map { it.text })
    }

    private fun exerciseConversation(recreate: Boolean) {
        seedLongHistory()
        val chat = repository.demoChat
        val viewModel = render()
        waitForText("Earlier note 24")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Did the library have that atlas?"))
        compose.onNodeWithText("Did the library have that atlas?").assertIsDisplayed()

        val outgoing = "I have the atlas.\nSee you by the wall!"
        composer().performTextInput("  $outgoing  ")
        sendButton().performSemanticsAction(SemanticsActions.OnClick)
        sendButton().assertIsNotEnabled()
        waitForText(outgoing)
        assertFullyVisible(outgoing)
        typingIndicator().assertIsDisplayed()

        if (recreate) {
            compose.activityRule.scenario.recreate()
            assertSame(viewModel, render())
            typingIndicator().assertIsDisplayed()
        }
        releaseReply.complete(Unit)
        val reply = scriptedReplies.first()
        waitForText(reply)
        val outgoingBounds = assertFullyVisible(outgoing)
        val replyBounds = assertFullyVisible(reply)
        assertTrue("Outgoing bubble is on the physical right", outgoingBounds.left > replyBounds.left)
        assertTrue("The reply follows the outgoing message", outgoingBounds.bottom < replyBounds.top)
        typingIndicator().assertDoesNotExist()
        val stored = storedMessages()
        assertEquals(34, stored.size)
        assertEquals(listOf(outgoing, reply), stored.takeLast(2).map { it.text })
        assertEquals(listOf(chat.currentUser.id, chat.otherUser.id), stored.takeLast(2).map { it.senderId })
    }

    private fun seedLongHistory(noteCount: Int = 24) {
        val chat = repository.demoChat
        runBlocking {
            withTimeout(5.seconds) {
                repeat(noteCount) {
                    repository.sendMessage(NewMessage(chat.conversation.id, chat.currentUser.id, "Earlier note ${it + 1}"))
                }
            }
        }
    }

    private fun waitForStoredReply() {
        runBlocking {
            withTimeout(5.seconds) {
                repository.observeMessages(repository.demoChat.conversation.id).first { it.last().text == scriptedReplies.first() }
            }
        }
        compose.waitForIdle()
    }

    private fun storedMessages() =
        runBlocking { withTimeout(5.seconds) { repository.observeMessages(repository.demoChat.conversation.id).first() } }

    private fun render(locale: Locale = Locale.ENGLISH): ChatViewModel {
        lateinit var viewModel: ChatViewModel
        compose.activityRule.scenario.onActivity { activity ->
            val factory = viewModelFactory { initializer { ChatViewModel(repository, draftStore, replyDelay) } }
            viewModel = ViewModelProvider.create(activity, factory)[ChatViewModel::class]
            val configuration = Configuration(activity.resources.configuration).apply { setLocale(locale) }
            val context = activity.createConfigurationContext(configuration)
            activity.setContent {
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                    ChatDemoTheme { ChatRoute(viewModel, onBack = {}) }
                }
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

    private fun typingIndicator() = compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_typing, "Sarah"))

    private fun bottomButton() = compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_scroll_to_bottom))
}
