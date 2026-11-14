package com.team.carescape.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Controlled card tests; PNGs are review artifacts, not recorded screenshot goldens. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ThemeCardFeedbackTest {
    private class TestMotionScale : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(1f)
    }

    private val motionScale = TestMotionScale()
    @get:Rule val rule = createComposeRule(effectContext = motionScale)

    @Test fun selectedCardCallsBackImmediatelyAndReplaysFeedbackWithoutLosingItsStamp() {
        freezeClock()
        var calls = 0
        rule.setContent { CardHost { SampleCard(SelectedTag, selected = true, onClick = { calls++ }) } }
        rule.mainClock.advanceTimeByFrame()
        val card = selectableCard(SelectedTag)
        card.assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        assertStampVisible()
        val before = capture("selected-before")

        card.performClick()
        // The callback must not wait for any animation frame or an artificial navigation delay.
        assertEquals(1, calls)
        rule.mainClock.advanceTimeBy(80L)
        card.assertIsSelected()
        val during = capture("selected-during")
        assertFalse("A click should produce visible feedback", before.sameAs(during))

        card.performClick()
        assertEquals(2, calls)
        rule.mainClock.advanceTimeBy(32L)
        val restarted = capture("selected-restarted")
        assertFalse("An already selected card should replay its feedback", during.sameAs(restarted))

        rule.mainClock.advanceTimeBy(400L)
        card.assertIsSelected()
        assertStampVisible()
        val after = capture("selected-after")
        assertFalse("The temporary feedback should settle", during.sameAs(after))
        assertEquals(2, calls)
    }

    @Test fun unavailableCardDoesNotSelectOrCallThePlayableCardCallback() {
        freezeClock()
        var calls = 0
        rule.setContent {
            CardHost {
                SampleCard(SelectedTag, selected = true, onClick = { calls++ })
                SampleCard(UnavailableTag, selected = false, onClick = null)
            }
        }
        rule.mainClock.advanceTimeByFrame()
        val unavailable = selectableCard(UnavailableTag)
        unavailable.assertIsDisplayed().assertIsNotEnabled().assertIsNotSelected()
        unavailable.performTouchInput { click() }
        rule.mainClock.advanceTimeBy(400L)
        assertEquals(0, calls)
        unavailable.assertIsNotEnabled().assertIsNotSelected()
        selectableCard(SelectedTag).assertIsSelected()
        assertStampVisible()
    }

    @Test fun zeroMotionScaleCompletesAndKeepsSelectionAndStampVisibleAfterRepeatedClicks() {
        freezeClock(scale = 0f)
        var calls = 0
        rule.setContent { CardHost { SampleCard(SelectedTag, selected = true, onClick = { calls++ }) } }
        rule.mainClock.advanceTimeByFrame()
        val before = capture("zero-motion-before")
        val card = selectableCard(SelectedTag)

        card.performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertEquals(1, calls)
        // Invoke the selection action without a pointer ripple; test only the card motion.
        // MotionDurationScale(0f) finishes on the next frame callback.
        rule.mainClock.advanceTimeBy(64L)
        card.assertIsSelected()
        assertStampVisible()
        val after = capture("zero-motion-after")
        assertTrue("Reduced motion should leave the same settled selected card", before.sameAs(after))

        card.performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertEquals(2, calls)
        rule.mainClock.advanceTimeBy(64L)
        card.assertIsSelected()
        assertStampVisible()
        val repeated = capture("zero-motion-after-repeat")
        assertTrue("Repeated clicks must not leave reduced-motion feedback unfinished", after.sameAs(repeated))
    }

    private fun freezeClock(scale: Float = 1f) {
        motionScale.scaleFactor = scale
        rule.mainClock.autoAdvance = false
    }

    private fun selectableCard(tag: String): SemanticsNodeInteraction = rule.onNode(
        hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
        useUnmergedTree = true,
    )

    private fun assertStampVisible() {
        rule.onNodeWithText("선택 완료", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun capture(name: String): Bitmap {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "case-selection-feedback")
        assertTrue("The screenshot directory must exist", directory.isDirectory || directory.mkdirs())
        val file = File(directory, "$name.png")
        file.outputStream().use { output -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
        assertTrue("The screenshot must be written", file.isFile && file.length() > 0L)
        Log.i("ThemeCardFeedbackTest", "Review screenshot: ${file.absolutePath}")
        return bitmap
    }

    @Composable
    private fun CardHost(content: @Composable () -> Unit) {
        MaterialTheme {
            Box(Modifier.fillMaxSize().background(Color(0xFF211B17)), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { content() }
            }
        }
    }

    @Composable
    private fun SampleCard(tag: String, selected: Boolean, onClick: (() -> Unit)?) {
        ThemeCard(
            modifier = Modifier.size(280.dp, 440.dp).testTag(tag),
            selected = selected,
            tilt = -0.8f,
            pin = Esc.oxblood,
            artBg = Color(0xFFD8C9A6),
            art = {},
            label = "Case No. 03 · 시간여행 미스터리",
            title = if (onClick != null) "시역 · 선택 가능한 사건" else "준비 중인 사건",
            rows = listOf("난이도" to "◆◆◆◇◇", "코스" to "수원 · 장소 5곳"),
            onClick = onClick,
        )
    }

    private companion object {
        const val SelectedTag = "feedback-selected-case"
        const val UnavailableTag = "feedback-unavailable-case"
    }
}
