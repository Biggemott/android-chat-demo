package com.biggemott.chatdemo.ui.chat

import android.app.Application
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
    fun multilineComposer() = capture("composer-light", seededPreviewChat().copy(draft = PREVIEW_DRAFT), onSend = {})

    @Test
    fun seededDark() = capture("seeded-dark", seededPreviewChat(), darkTheme = true)

    @Test
    fun largeText() = capture("large-text", seededPreviewChat(lastMessageText = PREVIEW_LONG_MESSAGE), fontScale = 1.5f)

    private fun capture(
        name: String,
        state: ChatUiState,
        darkTheme: Boolean = false,
        onSend: (() -> Unit)? = null,
        fontScale: Float = 1f,
    ) {
        captureRoboImage("src/test/screenshots/$name.png") {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                ChatDemoTheme(darkTheme = darkTheme) {
                    ChatScreen(
                        state = state,
                        onBack = {},
                        onDraftChanged = {},
                        onSend = onSend,
                        zone = ZoneOffset.UTC,
                    )
                }
            }
        }
    }
}
