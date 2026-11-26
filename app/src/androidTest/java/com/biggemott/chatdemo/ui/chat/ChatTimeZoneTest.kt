package com.biggemott.chatdemo.ui.chat

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.biggemott.chatdemo.domain.MessageId
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class ChatTimeZoneTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refreshesVisibleTimeOnBroadcastAndAfterReturningToTheScreen() {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val section = ChatTimelineItem.TimeSection(MessageId(1), Instant.parse("2026-01-01T11:59:00Z"))
            val state = emptyPreviewChat.copy(history = ChatHistory.Content(listOf(section)))
            compose.setContent { ChatDemoTheme { ChatScreen(state) } }
            compose.onNodeWithText("Thursday 11:59").assertExists()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setSystemTimeZone("Pacific/Kiritimati")
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Friday 01:59").fetchSemanticsNodes().isNotEmpty() }
            }

            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText("Thursday 06:59").assertExists()
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setSystemTimeZone(originalZone.id)
            TimeZone.setDefault(originalZone)
        }
    }

    private fun setSystemTimeZone(zone: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val output = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cmd alarm set-timezone $zone"))
        val response = output.bufferedReader().use { it.readText() }
        assertTrue(response, response.isBlank())
    }
}
