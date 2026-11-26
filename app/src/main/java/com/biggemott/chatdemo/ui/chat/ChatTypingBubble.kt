package com.biggemott.chatdemo.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.biggemott.chatdemo.R
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import kotlin.math.PI
import kotlin.math.sin

internal val typingRowHeight = 43.dp
private val typingTopPadding = 9.dp
private const val DOT_COUNT = 3
private const val DOT_DELAY_FRACTION = 0.22f
private const val DOT_PULSE_FRACTION = 0.45f

internal enum class Typing {
    Static,
    Animated,
}

@Composable
internal fun ChatTypingAnnouncement(
    senderName: String,
    isTyping: Boolean,
    modifier: Modifier = Modifier,
) {
    val announcement = if (isTyping) stringResource(R.string.chat_typing, senderName) else null
    // Keep the live region mounted so a pending reply changes its content even when the row is offscreen.
    Box(
        modifier.size(1.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            if (announcement != null) contentDescription = announcement
        },
    )
}

@Composable
internal fun ChatTypingBubble(animate: Boolean) {
    val colors = MaterialTheme.colorScheme
    // InfiniteTransition stops requesting frames when Compose's system motion scale is zero.
    val phase =
        if (animate) {
            rememberInfiniteTransition(label = "Typing").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 1_200, easing = LinearEasing)),
                label = "Dot sequence",
            )
        } else {
            null
        }
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = typingTopPadding),
        contentAlignment = AbsoluteAlignment.CenterLeft,
    ) {
        Canvas(
            modifier =
                Modifier
                    .background(colors.secondaryContainer, RoundedCornerShape(18.dp))
                    .size(width = 56.dp, height = typingRowHeight - typingTopPadding),
        ) {
            val progress = phase?.value ?: 0f
            for (dot in 0 until DOT_COUNT) {
                val pulse = ((progress - dot * DOT_DELAY_FRACTION) / DOT_PULSE_FRACTION).coerceIn(0f, 1f)
                val lift = sin(PI * pulse).toFloat()
                drawCircle(
                    color = colors.onSecondaryContainer.copy(alpha = 0.65f + 0.35f * lift),
                    radius = 3.dp.toPx(),
                    center = Offset(size.width / 2 + (dot - 1) * 11.dp.toPx(), size.height / 2 - 3.dp.toPx() * lift),
                )
            }
        }
    }
}

@Preview(name = "Static typing", widthDp = 88, locale = "en")
@Composable
private fun TypingBubblePreview() {
    ChatDemoTheme { ChatTypingBubble(animate = false) }
}
