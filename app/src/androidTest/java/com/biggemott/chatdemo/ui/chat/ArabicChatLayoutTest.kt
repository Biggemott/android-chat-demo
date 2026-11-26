package com.biggemott.chatdemo.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import org.junit.Assert.assertEquals
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
        val state = seededPreviewChat(lastMessageText = PREVIEW_MIXED_MESSAGE)
        val list = LazyListState()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("ar-EG"))) {
                ChatDemoTheme { ChatScreen(state, zone = ZoneOffset.UTC, listState = list) }
            }
        }

        val back =
            compose
                .onNodeWithContentDescription("رجوع")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val editor =
            compose
                .onNodeWithContentDescription("رسالة")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val send =
            compose
                .onNodeWithContentDescription("إرسال الرسالة")
                .assertIsNotEnabled()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(send.right < editor.left)
        assertTrue(back.left > editor.center.x)

        val incoming =
            compose
                .onNodeWithText(PREVIEW_MIXED_MESSAGE)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val outgoing = (state.history as ChatHistory.Content).items.filterIsInstance<ChatTimelineItem.Bubble>().last { it.isOutgoing }
        val outgoingBounds = compose.onNodeWithText(outgoing.message.text).fetchSemanticsNode().boundsInRoot
        assertTrue(incoming.left < outgoingBounds.left)
        assertTrue(outgoingBounds.bottom < incoming.top)
        val grouped = compose.onNodeWithText("I'll bring the thermos. It looks windy out there.").fetchSemanticsNode().boundsInRoot
        assertEquals(incoming.left, grouped.left, 0.5f)
        assertTrue(incoming.top - grouped.bottom < grouped.top - outgoingBounds.bottom)
        val textLayout = textLayoutOf(PREVIEW_MIXED_MESSAGE)
        assertEquals(ResolvedTextDirection.Rtl, textLayout.getParagraphDirection(0))
        assertEquals(ResolvedTextDirection.Ltr, textLayout.getParagraphDirection(PREVIEW_MIXED_MESSAGE.indexOf('\n') + 1))
        compose.onNodeWithText("الخميس ١٤:٢٤").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun compactArabicRecoveryKeepsWholeTextAndFullTargetsAtTwoHundredPercent() {
        var state by mutableStateOf(seededPreviewChat().copy(draft = "أطلس ١٢\nخريطة", sendError = true, canRecoverDraft = true))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("ar-EG"))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    ChatDemoTheme {
                        // Keep the tested content area independent of the device's system bars and cutout.
                        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).size(320.dp, 205.dp)) {
                            ChatScreen(state, actions = ChatActions(onSend = {}), zone = ZoneOffset.UTC)
                        }
                    }
                }
            }
        }

        val errorLayout = textLayoutOf("تعذّر الحفظ. أفرغ الحقل.")
        assertFalse("The whole recovery instruction is visible", errorLayout.hasVisualOverflow)

        val recoveryLabel = "استعادة المسودة"
        val recover =
            compose.onNode(hasText(recoveryLabel) or hasContentDescription(recoveryLabel)).assertIsDisplayed().assertIsNotEnabled()
        val send = compose.onNodeWithContentDescription("إرسال الرسالة").assertIsEnabled()
        val minTarget = with(compose.density) { 48.dp.roundToPx() }
        assertTrue(recover.fetchSemanticsNode().boundsInRoot.height >= minTarget)
        assertTrue(send.fetchSemanticsNode().boundsInRoot.height >= minTarget)
        compose.runOnIdle { state = state.copy(draft = "") }
        recover.assertIsEnabled()
        send.assertIsNotEnabled()
    }

    @Test
    fun latestMessageControlFloatsOnTheLeftWithAFullTarget() {
        val list = LazyListState(firstVisibleItemIndex = 4)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("ar-EG"))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    ChatDemoTheme {
                        Box(Modifier.size(320.dp, 350.dp)) {
                            ChatScreen(arabicPreviewChat(), zone = ZoneOffset.UTC, listState = list)
                        }
                    }
                }
            }
        }

        val latest = compose.onNodeWithContentDescription("انتقال إلى أحدث رسالة").assertIsDisplayed()
        val button = latest.fetchSemanticsNode().boundsInRoot
        val editor = compose.onNodeWithContentDescription("رسالة").fetchSemanticsNode().boundsInRoot
        val minTarget = with(compose.density) { 48.dp.roundToPx() }
        assertTrue(button.width >= minTarget && button.height >= minTarget)
        assertTrue(button.right < editor.left && button.bottom <= editor.top)
        latest.performClick().assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, list.firstVisibleItemIndex) }
    }

    @Test
    fun shortArabicImeWindowKeepsRoomForOneLineAndSend() {
        var readableFieldHeight = 0f
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("ar-EG"))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    ChatDemoTheme {
                        val lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        val padding = OutlinedTextFieldDefaults.contentPaddingWithoutLabel()
                        val minHeight =
                            with(LocalDensity.current) {
                                lineHeight.toPx() + padding.calculateTopPadding().toPx() + padding.calculateBottomPadding().toPx()
                            }
                        SideEffect { readableFieldHeight = minHeight }
                        Box(Modifier.size(width = 320.dp, height = 81.dp)) {
                            ChatComposer(draft = PREVIEW_ARABIC_DRAFT, onDraftChanged = {}, onSend = {})
                        }
                    }
                }
            }
        }

        val editor = compose.onNodeWithContentDescription("رسالة").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithContentDescription("إرسال الرسالة").fetchSemanticsNode().boundsInRoot
        val minTarget = with(compose.density) { 48.dp.roundToPx() }
        assertTrue(
            "Editor height ${editor.height} px must fit a $readableFieldHeight px text line with padding",
            editor.height + 1 >= readableFieldHeight,
        )
        assertTrue("Send retains its target in the short viewport", send.height >= minTarget && send.width >= minTarget)
    }

    private fun textLayoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(text, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}
