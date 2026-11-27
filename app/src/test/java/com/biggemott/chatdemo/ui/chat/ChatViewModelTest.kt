package com.biggemott.chatdemo.ui.chat

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.biggemott.chatdemo.data.DraftStore
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
import kotlinx.coroutines.CompletableDeferred
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
import java.util.Locale

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
    fun retriesInitialFailureWithoutLosingTheDraftOrAcceptingSendsBeforeRecovery() =
        runTest {
            val result = CompletableDeferred<List<Message>>()
            val sent = mutableListOf<NewMessage>()
            var attempts = 0
            var activeCollectors = 0
            val viewModel =
                viewModel(onSend = sent::add) {
                    val attempt = ++attempts
                    flow {
                        activeCollectors++
                        try {
                            assertEquals(1, activeCollectors)
                            if (attempt == 1) throw IOException("Cannot read messages")
                            emit(result.await())
                            awaitCancellation()
                        } finally {
                            activeCollectors--
                        }
                    }
                }
            val draft = "  Keep this\nمرحبا "
            viewModel.onDraftChanged(draft)

            runCurrent()
            assertEquals(ChatHistory.Unavailable, viewModel.uiState.value.history)
            assertEquals(1, attempts)
            assertEquals(0, activeCollectors)
            assertFalse(viewModel.uiState.value.canSend)
            viewModel.onSend(Locale.US)
            assertEquals(draft, viewModel.uiState.value.draft)

            viewModel.onRetryHistory()
            viewModel.onRetryHistory()
            assertEquals(ChatHistory.Loading, viewModel.uiState.value.history)
            assertFalse(viewModel.uiState.value.canSend)
            viewModel.onSend(Locale.US)
            assertEquals(draft, viewModel.uiState.value.draft)
            runCurrent()
            viewModel.onRetryHistory()
            runCurrent()
            assertEquals(2, attempts)
            assertEquals(1, activeCollectors)
            assertTrue(sent.isEmpty())

            result.complete(listOf(first))
            runCurrent()
            assertEquals(ChatHistory.Content(mapChatTimeline(listOf(first), demoChat.currentUser.id)), viewModel.uiState.value.history)
            assertEquals(draft, viewModel.uiState.value.draft)
            assertTrue(viewModel.uiState.value.canSend)
            viewModel.onRetryHistory()
            viewModel.onSend(Locale.US)
            runCurrent()
            assertEquals(2, attempts)
            assertEquals(listOf(NewMessage(demoChat.conversation.id, demoChat.currentUser.id, draft.trim())), sent)
        }

    @Test
    fun retriesAfterContentFailsAndCanRecoverEmptyHistoryAfterASecondFailure() =
        runTest {
            val failAfterContent = CompletableDeferred<Unit>()
            var attempts = 0
            val viewModel =
                viewModel {
                    when (++attempts) {
                        1 -> {
                            flow {
                                emit(listOf(first))
                                failAfterContent.await()
                                throw IOException("Lost history")
                            }
                        }

                        2 -> {
                            throw IOException("Cannot reopen history")
                        }

                        else -> {
                            flow { emit(emptyList()) }
                        }
                    }
                }
            runCurrent()
            assertTrue(viewModel.uiState.value.history is ChatHistory.Content)
            failAfterContent.complete(Unit)
            runCurrent()
            assertEquals(ChatHistory.Unavailable, viewModel.uiState.value.history)
            assertEquals(1, attempts)

            viewModel.onRetryHistory()
            runCurrent()
            assertEquals(ChatHistory.Unavailable, viewModel.uiState.value.history)
            assertEquals(2, attempts)

            viewModel.onRetryHistory()
            runCurrent()
            assertEquals(ChatHistory.Content(emptyList()), viewModel.uiState.value.history)
            assertEquals(3, attempts)
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
    fun clearingTheViewModelCancelsARetryWithoutPublishingAnErrorOrRestarting() =
        runTest {
            var attempts = 0
            var observing = false
            val viewModel =
                viewModel {
                    val attempt = ++attempts
                    flow {
                        if (attempt == 1) throw IOException("Cannot read messages")
                        observing = true
                        try {
                            awaitCancellation()
                        } finally {
                            observing = false
                        }
                    }
                }
            runCurrent()
            viewModel.onRetryHistory()
            runCurrent()
            assertTrue(observing)

            store.clear()
            runCurrent()

            assertFalse(observing)
            assertEquals(ChatHistory.Loading, viewModel.uiState.value.history)
            assertEquals(2, attempts)
        }

    private fun viewModel(
        onSend: (NewMessage) -> Unit = { error("Unexpected send") },
        observe: () -> Flow<List<Message>>,
    ): ChatViewModel {
        val repository =
            object : ChatRepository {
                override val demoChat = this@ChatViewModelTest.demoChat

                override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> {
                    require(conversationId == demoChat.conversation.id)
                    return observe()
                }

                override suspend fun sendMessage(message: NewMessage): Message {
                    onSend(message)
                    throw IOException("Test send attempt")
                }
            }
        val draftStore =
            object : DraftStore {
                override suspend fun restore() = ""

                override fun save(text: String) = Unit
            }
        val factory = viewModelFactory { initializer { ChatViewModel(repository, draftStore) { error("Unexpected reply") } } }
        return ViewModelProvider.create(store, factory)[ChatViewModel::class]
    }
}
