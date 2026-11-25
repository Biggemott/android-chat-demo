package com.biggemott.chatdemo.ui.chat

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biggemott.chatdemo.MainActivity
import com.biggemott.chatdemo.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensAtLatestStoredMessageAndKeepsSendDisabledWhileEditing() {
        val latest = compose.onNodeWithText("See you at the steps.")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("See you at the steps.").fetchSemanticsNodes().isNotEmpty()
        }
        latest.assertIsDisplayed()
        compose.onNodeWithText("Sarah").assertIsDisplayed()

        val sectionHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and !hasText("Sarah")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(sectionHeading)

        val draft = "A first line\nA second line"
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_message_hint)).performTextInput(draft)
        compose.onNodeWithText(draft).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_send)).assertIsNotEnabled()
    }
}
