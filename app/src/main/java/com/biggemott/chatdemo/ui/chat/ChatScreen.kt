package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biggemott.chatdemo.R
import java.time.ZoneId

@Composable
internal fun ChatRoute(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        state = state,
        onBack = onBack,
        onDraftChanged = viewModel::onDraftChanged,
    )
}

@Composable
internal fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSend: (() -> Unit)? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ChatHeader(state.chat.otherUser.displayName, onBack)
            ChatHistoryPane(
                history = state.history,
                chat = state.chat,
                locale = LocalConfiguration.current.locales[0],
                zone = zone,
                modifier = Modifier.weight(1f),
            )
            ChatComposer(
                draft = state.draft,
                onDraftChanged = onDraftChanged,
                onSend = onSend?.takeIf { state.isDraftNonBlank },
            )
        }
    }
}

@Composable
private fun ChatHeader(
    displayName: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_chat_back),
                contentDescription = stringResource(R.string.chat_back),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayName.take(1),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
