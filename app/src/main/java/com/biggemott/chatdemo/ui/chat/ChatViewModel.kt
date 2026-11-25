package com.biggemott.chatdemo.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biggemott.chatdemo.domain.ChatRepository
import com.biggemott.chatdemo.domain.DemoChat
import com.biggemott.chatdemo.domain.Message
import com.biggemott.chatdemo.domain.NewMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
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
import java.util.concurrent.CancellationException
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

        fun onDraftChanged(text: String) {
            if (text == mutableUiState.value.draft) return
            draftRevision++
            mutableUiState.update { it.copy(draft = text) }
        }

        fun onSend() {
            val state = mutableUiState.value
            if (!state.canSend) return
            val draft = state.draft
            val acceptedRevision = ++draftRevision
            mutableUiState.update { it.copy(draft = "", sendError = failedDrafts.isNotEmpty()) }
            viewModelScope.launch {
                writeMutex.withLock {
                    persistOutgoing(draft, acceptedRevision)
                }
            }
        }

        fun onRecoverDraft() {
            if (mutableUiState.value.draft.isNotEmpty()) return
            val draft = failedDrafts.removeFirstOrNull() ?: return
            draftRevision++
            val hasMore = failedDrafts.isNotEmpty()
            mutableUiState.update { it.copy(draft = draft, sendError = hasMore, canRecoverDraft = hasMore) }
        }

        fun onOutgoingScrolled(key: String) {
            mutableUiState.update { if (it.outgoingScrollKey == key) it.copy(outgoingScrollKey = null) else it }
        }

        private suspend fun persistOutgoing(
            draft: String,
            acceptedRevision: Long,
        ) {
            val message = NewMessage(chat.conversation.id, chat.currentUser.id, draft.trim())
            val stored =
                persist(message) ?: run {
                    recoverFailedSend(draft, acceptedRevision)
                    return
                }
            mutableUiState.update { it.copy(outgoingScrollKey = messageKey(stored.id)) }
            replaceReply()
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
        }

        // Called while holding writeMutex, so a cancelled reply can only be delaying or waiting for the lock.
        private fun replaceReply() {
            val generation = ++replyGeneration
            replyJob?.cancel()
            mutableUiState.update { it.copy(isReplyPending = true) }
            replyJob =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        replyDelay.await()
                        writeMutex.withLock {
                            val reply = NewMessage(chat.conversation.id, chat.otherUser.id, scriptedReplies[replyIndex])
                            if (persist(reply) != null) {
                                replyIndex = (replyIndex + 1) % scriptedReplies.size
                            }
                        }
                    } finally {
                        if (generation == replyGeneration) {
                            mutableUiState.update { it.copy(isReplyPending = false) }
                        }
                    }
                }
        }

        private suspend fun persist(message: NewMessage): Message? =
            try {
                repository.sendMessage(message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
    }
