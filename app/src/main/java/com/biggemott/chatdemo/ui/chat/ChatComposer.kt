package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.biggemott.chatdemo.R

@Composable
internal fun ChatComposer(
    draft: String,
    onDraftChanged: (String) -> Unit,
    onSend: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val hint = stringResource(R.string.chat_message_hint)
    HorizontalDivider(color = colors.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChanged,
            modifier = Modifier.weight(1f).semantics { contentDescription = hint },
            placeholder = { Text(hint) },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
        )
        FilledIconButton(
            onClick = { onSend?.invoke() },
            enabled = onSend != null,
            modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
            shape = CircleShape,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.tertiaryContainer,
                    disabledContentColor = colors.onTertiaryContainer,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chat_send),
                contentDescription = stringResource(R.string.chat_send),
            )
        }
    }
}
