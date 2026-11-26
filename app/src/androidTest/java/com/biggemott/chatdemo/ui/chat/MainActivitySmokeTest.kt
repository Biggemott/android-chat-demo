package com.biggemott.chatdemo.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.biggemott.chatdemo.MainActivity
import com.biggemott.chatdemo.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    private val isolatedPackage =
        object : ExternalResource() {
            override fun before() {
                val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
                assertEquals(
                    "Use -PisolatedInstrumentedTests so the smoke test opens an isolated app installation",
                    "com.biggemott.chatdemo.instrumentation",
                    packageName,
                )
            }
        }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(isolatedPackage).around(compose)

    @Test
    fun productionActivityShowsSeededConversationAndEmptyComposer() {
        val latestMessage = "See you at the steps."
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(latestMessage).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(latestMessage).assertIsDisplayed()
        compose.onNodeWithText("Sarah").assertIsDisplayed()
        compose
            .onNodeWithContentDescription(compose.activity.getString(R.string.chat_send))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }
}
