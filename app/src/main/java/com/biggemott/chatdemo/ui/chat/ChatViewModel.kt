package com.biggemott.chatdemo.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biggemott.chatdemo.domain.ChatRepository
import com.biggemott.chatdemo.domain.DemoChat
import com.biggemott.chatdemo.domain.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject

data class ChatUiState(
    val chat: DemoChat,
    val history: ChatHistory = ChatHistory.Loading,
    val draft: String = "",
) {
    val isDraftNonBlank: Boolean get() = draft.isNotBlank()
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
        repository: ChatRepository,
    ) : ViewModel() {
        private val chat = repository.demoChat
        private val mutableUiState = MutableStateFlow(ChatUiState(chat))
        val uiState: StateFlow<ChatUiState> = mutableUiState.asStateFlow()

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
            mutableUiState.update { it.copy(draft = text) }
        }
    }
