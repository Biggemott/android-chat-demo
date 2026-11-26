package com.biggemott.chatdemo.ui.chat

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w393dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatScreenshotTest {
    @Test
    fun seededLight() = capture("seeded-light", seededPreviewChat())

    @Test
    fun empty() = capture("empty-light", emptyPreviewChat)

    @Test
    fun multilineComposer() = capture("composer-light", seededPreviewChat().copy(draft = PREVIEW_DRAFT))

    @Test
    fun seededDark() = capture("seeded-dark", seededPreviewChat(), darkTheme = true)

    @Test
    fun largeText() = capture("large-text", seededPreviewChat(lastMessageText = PREVIEW_LONG_MESSAGE), fontScale = 1.5f)

    @Test
    fun pendingReply() = capture("pending-reply", pendingReplyPreviewChat())

    @Test
    @Config(qualifiers = "+h440dp")
    fun olderHistoryWithMultilineDraft() =
        capture(
            "older-history",
            seededPreviewChat().copy(draft = PREVIEW_TALL_DRAFT),
            firstVisibleIndex = 2,
        )

    @Test
    fun sendError() = capture("send-error", seededPreviewChat().copy(sendError = true, canRecoverDraft = true))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ar-rEG-w393dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArabicChatScreenshotTest {
    @Test
    fun arabicMixedContent() = capture("arabic-mixed", arabicPreviewChat())

    @Test
    @Config(qualifiers = "ar-rEG-w320dp-h760dp-night-mdpi")
    fun arabicDarkLargeText() =
        capture("arabic-dark-large", arabicPreviewChat().copy(isReplyPending = true), darkTheme = true, fontScale = 2f)

    @Test
    @Config(qualifiers = "ar-rEG-w540dp-h220dp-land-mdpi")
    fun arabicCompactRecovery() =
        capture(
            "arabic-compact-recovery",
            arabicPreviewChat().copy(sendError = true, canRecoverDraft = true),
            fontScale = 2f,
            firstVisibleIndex = 2,
        )
}

private fun capture(
    name: String,
    state: ChatUiState,
    darkTheme: Boolean = false,
    fontScale: Float = 1f,
    firstVisibleIndex: Int = 0,
) {
    captureRoboImage("src/test/screenshots/$name.png") {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            ChatDemoTheme(darkTheme = darkTheme) {
                ChatScreen(
                    state = state,
                    actions = ChatActions(onSend = {}),
                    zone = ZoneOffset.UTC,
                    listState = rememberLazyListState(initialFirstVisibleItemIndex = firstVisibleIndex),
                    animateTyping = false,
                )
            }
        }
    }
}
