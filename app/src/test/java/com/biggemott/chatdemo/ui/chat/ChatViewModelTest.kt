package com.biggemott.chatdemo.ui.chat

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.biggemott.chatdemo.domain.ChatRepository
import com.biggemott.chatdemo.domain.Conversation
import com.biggemott.chatdemo.domain.ConversationId
import com.biggemott.chatdemo.domain.DemoChat
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.domain.NewMessage
import com.biggemott.chatdemo.domain.User
import com.biggemott.chatdemo.domain.UserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val demoChat =
        DemoChat(
            Conversation(ConversationId("demo")),
            User(UserId("self"), "Self"),
            User(UserId("other"), "Other"),
        )
    private val first =
        Message(
            MessageId(1),
            demoChat.conversation.id,
            demoChat.otherUser.id,
            "Hello",
            Instant.EPOCH,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun loadingBecomesEmptyContentThenFollowsRepositoryUpdates() =
        runTest {
            val messages = MutableSharedFlow<List<Message>>(replay = 1)
            val viewModel = viewModel { messages }

            assertEquals(ChatHistory.Loading, viewModel.uiState.value.history)
            assertEquals(demoChat, viewModel.uiState.value.chat)

            messages.emit(emptyList())
            runCurrent()
            assertEquals(ChatHistory.Content(emptyList()), viewModel.uiState.value.history)

            messages.emit(listOf(first))
            runCurrent()
            assertEquals(
                listOf(
                    ChatTimelineItem.TimeSection(first.id, first.sentAt),
                    ChatTimelineItem.Bubble(first, isOutgoing = false, GroupPosition.Single),
                ),
                (viewModel.uiState.value.history as ChatHistory.Content).items,
            )
        }

    @Test
    fun editingPreservesTextAndReusesTheMappedHistory() =
        runTest {
            val viewModel = viewModel { flow { emit(listOf(first)) } }
            runCurrent()
            val history = viewModel.uiState.value.history
            val text = "  مرحبا\n👋  "

            assertFalse(viewModel.uiState.value.isDraftNonBlank)
            viewModel.onDraftChanged(text)
            assertEquals(text, viewModel.uiState.value.draft)
            assertTrue(viewModel.uiState.value.isDraftNonBlank)
            assertSame(history, viewModel.uiState.value.history)

            viewModel.onDraftChanged(" \n\t ")
            assertEquals(" \n\t ", viewModel.uiState.value.draft)
            assertFalse(viewModel.uiState.value.isDraftNonBlank)
            assertSame(history, viewModel.uiState.value.history)
        }

    @Test
    fun observationFailurePublishesAnErrorAndKeepsTheDraft() =
        runTest {
            val viewModel = viewModel { flow { throw IOException("Cannot read messages") } }
            viewModel.onDraftChanged("Keep this")

            runCurrent()

            assertEquals(ChatHistory.Unavailable, viewModel.uiState.value.history)
            assertEquals("Keep this", viewModel.uiState.value.draft)
        }

    @Test
    fun failureToOpenTheObservationPublishesAnError() =
        runTest {
            val viewModel = viewModel { throw IOException("Cannot open messages") }

            runCurrent()

            assertEquals(ChatHistory.Unavailable, viewModel.uiState.value.history)
        }

    @Test
    fun cancellationDoesNotPublishAnError() =
        runTest {
            var observationStarted = false
            val viewModel =
                viewModel {
                    flow {
                        observationStarted = true
                        throw CancellationException("Observation ended")
                    }
                }

            runCurrent()

            assertTrue(observationStarted)
            assertEquals(ChatHistory.Loading, viewModel.uiState.value.history)
        }

    @Test
    fun clearingTheViewModelCancelsObservation() =
        runTest {
            var observing = false
            val viewModel =
                viewModel {
                    flow {
                        observing = true
                        try {
                            emit(emptyList())
                            awaitCancellation()
                        } finally {
                            observing = false
                        }
                    }
                }
            runCurrent()
            assertTrue(observing)

            store.clear()
            runCurrent()

            assertFalse(observing)
            assertEquals(ChatHistory.Content(emptyList()), viewModel.uiState.value.history)
        }

    private fun viewModel(observe: () -> Flow<List<Message>>): ChatViewModel {
        val repository =
            object : ChatRepository {
                override val demoChat = this@ChatViewModelTest.demoChat

                override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> {
                    require(conversationId == demoChat.conversation.id)
                    return observe()
                }

                override suspend fun sendMessage(message: NewMessage): Message = error("Presentation must not send messages")
            }
        val factory = viewModelFactory { initializer { ChatViewModel(repository) { error("Unexpected reply") } } }
        return ViewModelProvider.create(store, factory)[ChatViewModel::class]
    }
}
