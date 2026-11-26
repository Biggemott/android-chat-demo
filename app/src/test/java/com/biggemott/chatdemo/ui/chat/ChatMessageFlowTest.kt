package com.biggemott.chatdemo.ui.chat

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.biggemott.chatdemo.data.DraftStore
import com.biggemott.chatdemo.data.fixedDemoChat
import com.biggemott.chatdemo.domain.ChatRepository
import com.biggemott.chatdemo.domain.ConversationId
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.domain.NewMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChatMessageFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = RecordingRepository()
    private val draftStore = ControlledDraftStore()
    private val delays = ArrayDeque<Long>()
    private var delaySelections = 0
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val factory =
            viewModelFactory {
                initializer {
                    ChatViewModel(repository, draftStore) {
                        delaySelections++
                        (delays.removeFirstOrNull() ?: 1_000).milliseconds
                    }
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
    fun restoresExactTextWithoutWritingItBack() =
        runTest {
            val draft = "  مرحبا\n👋  "
            draftStore.restored.complete(draft)
            runCurrent()
            assertEquals(draft, viewModel.uiState.value.draft)
            assertTrue(viewModel.uiState.value.canSend)
            assertTrue(draftStore.updates.isEmpty())
        }

    @Test
    fun whitespaceRestoresButCannotBeSent() =
        runTest {
            draftStore.restored.complete(" \n\t")
            runCurrent()
            viewModel.onSend()
            runCurrent()
            assertEquals(" \n\t", viewModel.uiState.value.draft)
            assertFalse(viewModel.uiState.value.canSend)
            assertTrue(repository.attempts.isEmpty())
        }

    @Test
    fun lateRestoreCannotReplaceAnEditOrResurrectAnAcceptedSend() =
        runTest {
            val commit = CompletableDeferred<Unit>()
            repository.beforeCommit = { commit.await() }
            send("  First  ")
            send("Second")
            assertEquals("", viewModel.uiState.value.draft)
            draftStore.restored.complete("Old draft")
            runCurrent()
            assertEquals("", viewModel.uiState.value.draft)
            assertEquals(listOf("  First  ", "", "Second", ""), draftStore.updates)
            commit.complete(Unit)
            runCurrent()
            assertEquals(listOf("First", "Second"), repository.written.map { it.text })
        }

    @Test
    fun restoreFailureKeepsNewInputUsable() =
        runTest {
            viewModel.onDraftChanged("New input")
            draftStore.restored.completeExceptionally(IOException("Read failed"))
            runCurrent()
            assertEquals("New input", viewModel.uiState.value.draft)
            assertTrue(viewModel.uiState.value.canSend)
            assertEquals(listOf("New input"), draftStore.updates)
        }

    @Test
    fun onlyVisibleFailedDraftsAreSaved() =
        runTest {
            repository.beforeCommit = { throw IOException("Write failed") }
            send("  Recover immediately  ")
            runCurrent()
            assertEquals(listOf("  Recover immediately  ", "", "  Recover immediately  "), draftStore.updates)

            send("Hold this")
            viewModel.onDraftChanged("New edit")
            runCurrent()
            assertEquals("New edit", viewModel.uiState.value.draft)
            assertEquals(listOf("Hold this", "", "New edit"), draftStore.updates.takeLast(3))
            viewModel.onDraftChanged("")
            viewModel.onRecoverDraft()
            assertEquals(listOf("", "Hold this"), draftStore.updates.takeLast(2))
        }

    @Test
    fun clearingViewModelCancelsPendingRestore() =
        runTest {
            assertTrue(draftStore.isRestoring)
            store.clear()
            runCurrent()
            assertFalse(draftStore.isRestoring)
            draftStore.restored.complete("Too late")
            assertEquals("", viewModel.uiState.value.draft)
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
            val expectedScrollKey = messageKey(repository.written.last().id)
            assertEquals(expectedScrollKey, viewModel.uiState.value.outgoingScrollKey)
            viewModel.onOutgoingScrolled(messageKey(repository.written.first().id))
            assertEquals(expectedScrollKey, viewModel.uiState.value.outgoingScrollKey)
            repository.observed.value = repository.written.toList()
            runCurrent()
            val history = viewModel.uiState.value.history as ChatHistory.Content
            assertEquals(repository.written, history.items.filterIsInstance<ChatTimelineItem.Bubble>().map { it.message })
        }

    @Test
    fun aBurstReplacesTheDeadlineWithOneReplyAfterTheFinalWrite() =
        runTest {
            delays.addAll(listOf(1_000L, 3_000L))
            val typing = mutableListOf<Boolean>()
            val history = viewModel.uiState.value.history
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {
                    if (typing.lastOrNull() != it.isReplyPending) typing += it.isReplyPending
                    assertSame(history, it.history)
                }
            }
            send("First")
            runCurrent()
            advanceTimeBy(900.milliseconds)
            send("Second")
            runCurrent()
            advanceTimeBy(2_999.milliseconds)
            runCurrent()
            assertEquals(listOf("First", "Second"), repository.written.map { it.text })
            assertEquals(2, delaySelections)
            assertEquals(listOf(false, true), typing)
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(1, repository.replies.size)
            assertEquals(listOf(false, true, false), typing)
            advanceTimeBy(3_000.milliseconds)
            runCurrent()
            assertEquals(1, repository.replies.size)
        }

    @Test
    fun rapidSuccessfulWritesEachSelectADelayAndOnlyTheLastReplies() =
        runTest {
            delays.addAll(listOf(1_000L, 3_000L))
            send("First")
            send("Second")
            runCurrent()

            assertEquals(listOf("First", "Second"), repository.written.map { it.text })
            assertEquals(2, delaySelections)
            advanceTimeBy(2_999.milliseconds)
            runCurrent()
            assertTrue(repository.replies.isEmpty())
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(listOf(scriptedReplies.first()), repository.replies.map { it.text })
        }

    @Test
    fun aSendHoldingTheWriteAtTheDeadlineSupersedesTheOldReplyOnlyOnSuccess() =
        runTest {
            val persist = CompletableDeferred<Unit>()
            repository.beforeCommit = { if (it.text == "Second") persist.await() }
            send("First")
            runCurrent()
            advanceTimeBy(999.milliseconds)
            send("Second")
            runCurrent()
            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(listOf("First", "Second"), repository.attempts.map { it.text })
            persist.complete(Unit)
            runCurrent()
            assertEquals(0, repository.replies.size)
            advanceTimeBy(1_000.milliseconds)
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
            advanceTimeBy(1_000.milliseconds)
            runCurrent()
            send("Second")
            runCurrent()
            assertEquals(listOf(fixedDemoChat.currentUser.id, fixedDemoChat.otherUser.id), repository.attempts.map { it.senderId })
            persist.complete(Unit)
            runCurrent()
            assertEquals("Second", repository.written.last().text)
            assertEquals(1, repository.replies.size)
            advanceTimeBy(1_000.milliseconds)
            runCurrent()
            assertEquals(2, repository.replies.size)
        }

    @Test
    fun aFailedSendLeavesTheExistingDeadlineInPlace() =
        runTest {
            repository.beforeCommit = { if (it.text == "Second") throw IOException("Write failed") }
            send("First")
            runCurrent()
            advanceTimeBy(900.milliseconds)
            send("Second")
            runCurrent()
            advanceTimeBy(100.milliseconds)
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
            advanceTimeBy(3_000.milliseconds)
            runCurrent()
            assertEquals(listOf("First"), repository.written.map { it.text })
        }

    @Test
    fun clearingBeforeTheQueuedReplyStartsEndsTypingWithoutWritingAReply() =
        runTest {
            send("First")
            launch(dispatcher) {
                assertEquals(1, delaySelections)
                assertTrue(viewModel.uiState.value.isReplyPending)
                store.clear()
            }
            runCurrent()
            advanceTimeBy(3_000.milliseconds)
            runCurrent()

            assertEquals(listOf("First"), repository.written.map { it.text })
            assertFalse(viewModel.uiState.value.isReplyPending)
        }

    @Test
    fun clearingCancelsAReplySuspendedBeforeCommit() =
        runTest {
            val commit = CompletableDeferred<Unit>()
            repository.beforeCommit = { if (it.senderId == fixedDemoChat.otherUser.id) commit.await() }
            send("First")
            runCurrent()
            advanceTimeBy(1_000.milliseconds)
            runCurrent()
            assertEquals(fixedDemoChat.otherUser.id, repository.attempts.last().senderId)
            assertTrue(viewModel.uiState.value.isReplyPending)
            store.clear()
            runCurrent()
            commit.complete(Unit)
            runCurrent()
            assertTrue(repository.replies.isEmpty())
            assertFalse(viewModel.uiState.value.isReplyPending)
        }

    @Test
    fun clearingAfterCommitLeavesTheStoredReplyInPlace() =
        runTest {
            val resume = CompletableDeferred<Unit>()
            repository.afterCommit = { if (it.senderId == fixedDemoChat.otherUser.id) resume.await() }
            send("First")
            runCurrent()
            advanceTimeBy(1_000.milliseconds)
            runCurrent()
            assertTrue(viewModel.uiState.value.isReplyPending)
            assertEquals(listOf(scriptedReplies.first()), repository.replies.map { it.text })
            store.clear()
            runCurrent()
            resume.complete(Unit)
            runCurrent()
            assertEquals(listOf(scriptedReplies.first()), repository.replies.map { it.text })
            assertFalse(viewModel.uiState.value.isReplyPending)
        }

    @Test
    fun multipleFailedSendsCanBeRecoveredInOrderWithoutReplacingANewerDraft() =
        runTest {
            val persist = CompletableDeferred<Unit>()
            repository.beforeCommit = {
                persist.await()
                throw IOException("Write failed")
            }
            send("  First 🗺️\n")
            send("Second")
            viewModel.onDraftChanged("New draft")
            persist.complete(Unit)
            runCurrent()
            assertTrue(viewModel.uiState.value.sendError)
            assertTrue(viewModel.uiState.value.canRecoverDraft)
            viewModel.onRecoverDraft()
            assertEquals("New draft", viewModel.uiState.value.draft)
            viewModel.onDraftChanged("")
            viewModel.onRecoverDraft()
            assertEquals("  First 🗺️\n", viewModel.uiState.value.draft)
            assertTrue(viewModel.uiState.value.canRecoverDraft)
            viewModel.onDraftChanged("")
            viewModel.onRecoverDraft()
            assertEquals("Second", viewModel.uiState.value.draft)
            assertFalse(viewModel.uiState.value.canRecoverDraft)
            advanceTimeBy(3_000.milliseconds)
            runCurrent()
            assertEquals(emptyList<Message>(), repository.written)
            assertEquals(0, delaySelections)
        }

    @Test
    fun outgoingCancellationIsNotAnOrdinaryFailureAndDoesNotBlockLaterSends() =
        runTest {
            repository.beforeCommit = { if (it.text == "Cancelled") throw CancellationException("Write cancelled") }
            send("Cancelled")
            runCurrent()
            assertEquals("", viewModel.uiState.value.draft)
            assertFalse(viewModel.uiState.value.sendError)
            assertEquals(0, delaySelections)
            send("Next")
            runCurrent()
            assertEquals(listOf("Next"), repository.written.map { it.text })
        }

    @Test
    fun failedReplyDoesNotConsumeTheLineAndSuccessfulRepliesCycle() =
        runTest {
            repository.beforeCommit = { if (it.senderId == fixedDemoChat.otherUser.id) throw IOException("Write failed") }
            send("First attempt")
            runCurrent()
            advanceTimeBy(1_000.milliseconds)
            runCurrent()
            assertFalse(viewModel.uiState.value.isReplyPending)
            assertTrue(repository.replies.isEmpty())
            repository.beforeCommit = {}
            repeat(scriptedReplies.size + 1) {
                send("Next")
                runCurrent()
                advanceTimeBy(1_000.milliseconds)
                runCurrent()
            }
            assertEquals(scriptedReplies + scriptedReplies.first(), repository.replies.map { it.text })
        }

    @Test
    fun seedAndRepeatedStateReadsNeverArmAReply() =
        runTest {
            repository.observed.value =
                listOf(
                    Message(
                        MessageId(1),
                        fixedDemoChat.conversation.id,
                        fixedDemoChat.currentUser.id,
                        "Stored before launch",
                        Instant.EPOCH,
                    ),
                )
            runCurrent()
            assertTrue(viewModel.uiState.value.history is ChatHistory.Content)
            assertEquals(viewModel.uiState.first(), viewModel.uiState.first())
            advanceTimeBy(3_000.milliseconds)
            runCurrent()
            assertEquals(emptyList<NewMessage>(), repository.attempts)
            assertEquals(1, repository.observationCount)
        }

    @Test
    fun productionDelayRequestsBothInclusiveEndpoints() {
        val endpoints =
            object : Random() {
                private var nextIsMin = true

                override fun nextInt(
                    from: Int,
                    until: Int,
                ): Int = (if (nextIsMin) from else until - 1).also { nextIsMin = false }

                override fun nextBits(bitCount: Int): Int = error("Use the bounded draw")
            }
        assertEquals(1_000.milliseconds, randomReplyDelay(endpoints))
        assertEquals(3_000.milliseconds, randomReplyDelay(endpoints))
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
        var observationCount = 0
            private set
        var beforeCommit: suspend (NewMessage) -> Unit = {}
        var afterCommit: suspend (Message) -> Unit = {}

        override fun observeMessages(conversationId: ConversationId): MutableStateFlow<List<Message>> {
            observationCount++
            return observed
        }

        override suspend fun sendMessage(message: NewMessage): Message {
            attempts += message
            beforeCommit(message)
            val stored =
                Message(
                    MessageId(100L + written.size),
                    message.conversationId,
                    message.senderId,
                    message.text,
                    Instant.EPOCH,
                )
            written += stored
            afterCommit(stored)
            return stored
        }
    }
}

private class ControlledDraftStore : DraftStore {
    val restored = CompletableDeferred<String>()
    val updates = mutableListOf<String>()
    var isRestoring = false
        private set

    override suspend fun restore(): String {
        isRestoring = true
        try {
            return restored.await()
        } finally {
            isRestoring = false
        }
    }

    override fun save(text: String) {
        updates += text
    }
}
