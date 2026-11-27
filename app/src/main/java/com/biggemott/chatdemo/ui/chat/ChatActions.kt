package com.biggemott.chatdemo.ui.chat

internal class ChatActions(
    val onDraftChanged: (String) -> Unit = {},
    val onSend: (() -> Unit)? = null,
    val onRecoverDraft: () -> Unit = {},
    val onOutgoingScrolled: (String) -> Unit = {},
    val onBack: () -> Unit = {},
    val onRetryHistory: () -> Unit = {},
)
