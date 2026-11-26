package com.biggemott.chatdemo.ui.chat

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biggemott.chatdemo.data.DraftStore
import com.biggemott.chatdemo.domain.ChatRepository
import com.biggemott.chatdemo.domain.DemoChat
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.NewMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.logging.Logger
import javax.inject.Inject

data class ChatUiState(
    val chat: DemoChat,
    val history: ChatHistory = ChatHistory.Loading,
    val draft: String = "",
    val sendError: Boolean = false,
    val canRecoverDraft: Boolean = false,
    val isReplyPending: Boolean = false,
    val outgoingScrollKey: String? = null,
) {
    val isDraftNonBlank: Boolean get() = draft.isNotBlank()
    val canSend: Boolean get() = history is ChatHistory.Content && isDraftNonBlank
}

sealed interface ChatHistory {
    data object Loading : ChatHistory

    data class Content(
        val items: List<ChatTimelineItem>,
    ) : ChatHistory

    data object Unavailable : ChatHistory
}

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val repository: ChatRepository,
        private val draftStore: DraftStore,
        private val replyDelay: ReplyDelay,
    ) : ViewModel() {
        private val chat = repository.demoChat
        private val mutableUiState = MutableStateFlow(ChatUiState(chat))
        val uiState: StateFlow<ChatUiState> = mutableUiState.asStateFlow()

        // UI callbacks and reply scheduling are confined to Main. The mutex covers suspension inside Room writes.
        private val writeMutex = Mutex()
        private val failedDrafts = ArrayDeque<String>()
        private var draftRevision = 0L
        private var replyGeneration = 0L
        private var replyJob: Job? = null
        private var replyIndex = 0

        init {
            viewModelScope.launch {
                try {
                    val restored = draftStore.restore()
                    if (draftRevision == 0L) mutableUiState.update { it.copy(draft = restored) }
                } catch (failure: IOException) {
                    logger.warning("Could not restore chat draft (${failure.javaClass.simpleName})")
                }
            }
            viewModelScope.launch {
                flow { emitAll(repository.observeMessages(chat.conversation.id)) }
                    .map<List<Message>, ChatHistory> { messages ->
                        ChatHistory.Content(mapChatTimeline(messages, chat.currentUser.id))
                    }.catch { failure ->
                        if (failure is CancellationException || failure !is Exception) throw failure
                        emit(ChatHistory.Unavailable)
                    }.collect { history ->
                        mutableUiState.update { it.copy(history = history) }
                    }
            }
        }

        override fun onCleared() {
            // A reply cancelled before launch never enters its finally block.
            mutableUiState.update { it.copy(isReplyPending = false) }
        }

        @MainThread
        fun onDraftChanged(text: String) {
            if (text == mutableUiState.value.draft) return
            draftRevision++
            mutableUiState.update { it.copy(draft = text) }
            draftStore.save(text)
        }

        @MainThread
        fun onSend(locale: Locale) {
            val state = mutableUiState.value
            if (!state.canSend) return
            val draft = state.draft
            val acceptedRevision = ++draftRevision
            mutableUiState.update { it.copy(draft = "", sendError = failedDrafts.isNotEmpty()) }
            draftStore.save("")
            viewModelScope.launch {
                writeMutex.withLock {
                    persistOutgoing(draft, acceptedRevision, locale)
                }
            }
        }

        @MainThread
        fun onRecoverDraft() {
            if (mutableUiState.value.draft.isNotEmpty()) return
            val draft = failedDrafts.removeFirstOrNull() ?: return
            draftRevision++
            val hasMore = failedDrafts.isNotEmpty()
            mutableUiState.update { it.copy(draft = draft, sendError = hasMore, canRecoverDraft = hasMore) }
            draftStore.save(draft)
        }

        @MainThread
        fun onOutgoingScrolled(key: String) {
            mutableUiState.update { if (it.outgoingScrollKey == key) it.copy(outgoingScrollKey = null) else it }
        }

        private suspend fun persistOutgoing(
            draft: String,
            acceptedRevision: Long,
            locale: Locale,
        ) {
            val message = NewMessage(chat.conversation.id, chat.currentUser.id, draft.trim())
            val stored =
                persist(message) ?: run {
                    recoverFailedSend(draft, acceptedRevision)
                    return
                }
            mutableUiState.update { it.copy(outgoingScrollKey = messageKey(stored.id)) }
            replaceReply(scriptedRepliesFor(locale))
        }

        private fun recoverFailedSend(
            draft: String,
            acceptedRevision: Long,
        ) {
            val restoreNow = draftRevision == acceptedRevision && failedDrafts.isEmpty()
            if (!restoreNow) failedDrafts.addLast(draft)
            mutableUiState.update {
                it.copy(draft = if (restoreNow) draft else it.draft, sendError = true, canRecoverDraft = failedDrafts.isNotEmpty())
            }
            if (restoreNow) draftStore.save(draft)
        }

        // Holding writeMutex lets us cancel the previous reply before it enters its write.
        private fun replaceReply(script: List<String>) {
            val generation = ++replyGeneration
            replyJob?.cancel()
            val duration = replyDelay.nextDuration()
            mutableUiState.update { it.copy(isReplyPending = true) }
            replyJob =
                viewModelScope.launch {
                    try {
                        replyDelay.await(duration)
                        writeMutex.withLock {
                            val reply = NewMessage(chat.conversation.id, chat.otherUser.id, script[replyIndex])
                            if (persist(reply) != null) {
                                replyIndex = (replyIndex + 1) % script.size
                            }
                        }
                    } finally {
                        if (generation == replyGeneration) {
                            mutableUiState.update { it.copy(isReplyPending = false) }
                        }
                    }
                }
        }

        // The repository can surface storage exceptions without a common base type.
        @Suppress("TooGenericExceptionCaught")
        private suspend fun persist(message: NewMessage): Message? =
            try {
                repository.sendMessage(message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val operation = if (message.senderId == chat.currentUser.id) "send" else "reply"
                logger.warning("Could not save chat $operation (${failure.javaClass.simpleName})")
                null
            }

        private companion object {
            val logger: Logger = Logger.getLogger(ChatViewModel::class.java.name)
        }
    }
