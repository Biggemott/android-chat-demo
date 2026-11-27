package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biggemott.chatdemo.data.DraftStore
import com.biggemott.chatdemo.data.fixedDemoChat
import com.biggemott.chatdemo.domain.ChatRepository
import com.biggemott.chatdemo.domain.ConversationId
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.NewMessage
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ChatHistoryRecoveryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun retryFromACompactUnavailablePaneRestoresHistoryAndKeepsTheDraft() {
        val recovered = CompletableDeferred<List<Message>>()
        val repository =
            object : ChatRepository {
                override val demoChat = fixedDemoChat
                private var attempts = 0

                override fun observeMessages(conversationId: ConversationId) =
                    flow {
                        if (++attempts == 1) throw IOException("Cannot read history")
                        emit(recovered.await())
                    }

                override suspend fun sendMessage(message: NewMessage): Message = error("Unexpected send")
            }
        val draft = "Keep this draft"
        val draftStore =
            object : DraftStore {
                override suspend fun restore() = draft

                override fun save(text: String) = Unit
            }
        val viewModel = compose.runOnUiThread { ChatViewModel(repository, draftStore) { error("Unexpected reply") } }
        try {
            compose.setContent {
                DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("en-US"))) {
                    ChatDemoTheme {
                        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).height(165.dp)) {
                            ChatRoute(viewModel, onBack = {})
                        }
                    }
                }
            }

            val send = compose.onNodeWithContentDescription("Send message")
            val retry = compose.onNodeWithText("Retry")
            compose.onNodeWithText("Couldn’t load the conversation").assertIsDisplayed()
            compose.onNodeWithText(draft).assertIsDisplayed()
            send.assertIsNotEnabled()
            val touchHeight =
                retry
                    .assertIsDisplayed()
                    .assertHasClickAction()
                    .fetchSemanticsNode()
                    .boundsInRoot.height
            assertTrue(touchHeight >= with(compose.density) { 48.dp.roundToPx() } - 0.5f)
            retry.performClick()

            compose.onNodeWithText("Loading conversation…").assertIsDisplayed()
            retry.assertDoesNotExist()
            send.assertIsNotEnabled()

            compose.runOnIdle { recovered.complete(emptyList()) }
            compose.onNodeWithText("No messages yet").assertIsDisplayed()
            compose.onNodeWithText(draft).assertIsDisplayed()
            send.assertIsEnabled()
        } finally {
            compose.runOnUiThread { viewModel.viewModelScope.cancel() }
        }
    }
}
