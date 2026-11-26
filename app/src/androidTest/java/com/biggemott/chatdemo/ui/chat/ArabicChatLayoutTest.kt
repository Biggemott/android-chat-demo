package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ArabicChatLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mirrorsControlsButKeepsIncomingLeftAndOutgoingRight() {
        val state = seededPreviewChat(lastMessageText = "٣٠ دقيقة؟ Lighthouse 12 🗺️\nSee you عند الميناء!")
        val list = LazyListState()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("ar-EG"))) {
                ChatDemoTheme { ChatScreen(state, zone = ZoneOffset.UTC, listState = list) }
            }
        }

        val back = compose.onNodeWithContentDescription("رجوع").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val editor = compose.onNodeWithContentDescription("رسالة").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithContentDescription("إرسال الرسالة").assertIsNotEnabled().fetchSemanticsNode().boundsInRoot
        assertTrue(send.right < editor.left)
        assertTrue(back.left > editor.center.x)

        val incoming = compose.onNodeWithText("٣٠ دقيقة؟ Lighthouse 12 🗺️\nSee you عند الميناء!").fetchSemanticsNode().boundsInRoot
        val outgoing = (state.history as ChatHistory.Content).items.filterIsInstance<ChatTimelineItem.Bubble>().last { it.isOutgoing }
        val outgoingBounds = compose.onNodeWithText(outgoing.message.text).fetchSemanticsNode().boundsInRoot
        assertTrue(incoming.left < outgoingBounds.left)
        assertTrue(outgoingBounds.bottom < incoming.top)
        compose.onNodeWithText("الخميس ١٤:٢٤").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun compactArabicRecoveryKeepsWholeTextAndFullTargetsAtTwoHundredPercent() {
        var state by mutableStateOf(seededPreviewChat().copy(draft = "أطلس ١٢\nخريطة", sendError = true, canRecoverDraft = true))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("ar-EG"))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    ChatDemoTheme {
                        Box(Modifier.size(320.dp, 205.dp)) {
                            ChatScreen(state, actions = ChatActions(onSend = {}), zone = ZoneOffset.UTC)
                        }
                    }
                }
            }
        }

        val errorText = compose.onNodeWithText("تعذّر حفظ رسالة. أفرغ حقل الكتابة لاستعادتها.", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        errorText.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("The whole recovery instruction is visible", layouts.single().hasVisualOverflow)

        val recover = compose.onNodeWithText("استعادة المسودة").assertIsDisplayed().assertIsNotEnabled()
        val send = compose.onNodeWithContentDescription("إرسال الرسالة").assertIsEnabled()
        val minTarget = with(compose.density) { 48.dp.roundToPx() }
        assertTrue(recover.fetchSemanticsNode().boundsInRoot.height >= minTarget)
        assertTrue(send.fetchSemanticsNode().boundsInRoot.height >= minTarget)
        compose.runOnIdle { state = state.copy(draft = "") }
        recover.assertIsEnabled()
        send.assertIsNotEnabled()
    }
}
