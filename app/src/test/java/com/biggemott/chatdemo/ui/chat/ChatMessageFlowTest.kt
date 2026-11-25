package com.biggemott.chatdemo.ui.chat

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.biggemott.chatdemo.data.fixedDemoChat
import com.biggemott.chatdemo.domain.ChatRepository
import com.biggemott.chatdemo.domain.ConversationId
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.domain.NewMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChatMessageFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = RecordingRepository()
    private val delays = ArrayDeque<Long>()
    private var delaySelections = 0
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val factory =
            viewModelFactory {
                initializer {
                    ChatViewModel(
                        repository,
                        ReplyDelay {
                            delaySelections++
                            delay(delays.removeFirstOrNull() ?: 1_000)
                        },
                    )
                }
            }
        viewModel = ViewModelProvider.create(store, factory)[ChatViewModel::class]
        dispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun trimsAndClearsOnceBeforePersistenceAndUsesOnlyObservedRecords() =
        runTest {
            val initial = viewModel.uiState.value.history
            val persist = CompletableDeferred<Unit>()
            repository.beforeCommit = { persist.await() }
            send(" \t\n")
            runCurrent()
            assertEquals(emptyList<NewMessage>(), repository.attempts)
            send("  Atlas 🗺️\n  Harbor  ")
            assertEquals("", viewModel.uiState.value.draft)
            viewModel.onSend()
            send("Second")
            runCurrent()
            assertEquals(listOf("Atlas 🗺️\n  Harbor"), repository.attempts.map { it.text })
            assertSame(initial, viewModel.uiState.value.history)
            persist.complete(Unit)
            runCurrent()
            assertEquals(listOf("Atlas 🗺️\n  Harbor", "Second"), repository.written.map { it.text })
            assertSame(initial, viewModel.uiState.value.history)
            repository.observed.value = repository.written.toList()
            runCurrent()
            val history = viewModel.uiState.value.history as ChatHistory.Content
            assertEquals(repository.written, history.items.filterIsInstance<ChatTimelineItem.Bubble>().map { it.message })
        }

    @Test
    fun aBurstReplacesTheDeadlineWithOneReplyAfterTheFinalWrite() =
        runTest {
            delays.addAll(listOf(1_000L, 3_000L))
            send("First")
            runCurrent()
            advanceTimeBy(900)
            send("Second")
            runCurrent()
            advanceTimeBy(2_999)
            runCurrent()
            assertEquals(listOf("First", "Second"), repository.written.map { it.text })
            assertEquals(2, delaySelections)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, repository.replies.size)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(1, repository.replies.size)
        }

    @Test
    fun aSendHoldingTheWriteAtTheDeadlineSupersedesTheOldReplyOnlyOnSuccess() =
        runTest {
            val persist = CompletableDeferred<Unit>()
            repository.beforeCommit = { if (it.text == "Second") persist.await() }
            send("First")
            runCurrent()
            advanceTimeBy(999)
            send("Second")
            runCurrent()
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("First", "Second"), repository.attempts.map { it.text })
            persist.complete(Unit)
            runCurrent()
            assertEquals(0, repository.replies.size)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(1, repository.replies.size)
        }

    @Test
    fun aReplyHoldingTheWriteAtTheDeadlineCommitsBeforeTheNextSend() =
        runTest {
            val persist = CompletableDeferred<Unit>()
            repository.beforeCommit = { if (it.senderId == fixedDemoChat.otherUser.id) persist.await() }
            send("First")
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            send("Second")
            runCurrent()
            assertEquals(listOf(fixedDemoChat.currentUser.id, fixedDemoChat.otherUser.id), repository.attempts.map { it.senderId })
            persist.complete(Unit)
            runCurrent()
            assertEquals("Second", repository.written.last().text)
            assertEquals(1, repository.replies.size)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, repository.replies.size)
        }

    @Test
    fun aFailedSendLeavesTheExistingDeadlineInPlace() =
        runTest {
            repository.beforeCommit = { if (it.text == "Second") throw IOException("Write failed") }
            send("First")
            runCurrent()
            advanceTimeBy(900)
            send("Second")
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(1, repository.replies.size)
            assertEquals(1, delaySelections)
            assertEquals("Second", viewModel.uiState.value.draft)
        }

    @Test
    fun clearingCancelsAnArmedReply() =
        runTest {
            send("First")
            runCurrent()
            assertEquals(1, delaySelections)
            store.clear()
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(listOf("First"), repository.written.map { it.text })
        }

    private fun send(text: String) {
        viewModel.onDraftChanged(text)
        viewModel.onSend()
    }

    private class RecordingRepository : ChatRepository {
        override val demoChat = fixedDemoChat
        val observed = MutableStateFlow<List<Message>>(emptyList())
        val attempts = mutableListOf<NewMessage>()
        val written = mutableListOf<Message>()
        val replies get() = written.filter { it.senderId == demoChat.otherUser.id }
        var beforeCommit: suspend (NewMessage) -> Unit = {}

        override fun observeMessages(conversationId: ConversationId) = observed

        override suspend fun sendMessage(message: NewMessage): Message {
            attempts += message
            beforeCommit(message)
            return Message(
                MessageId(100L + written.size),
                message.conversationId,
                message.senderId,
                message.text,
                Instant.EPOCH,
            ).also(written::add)
        }
    }
}
