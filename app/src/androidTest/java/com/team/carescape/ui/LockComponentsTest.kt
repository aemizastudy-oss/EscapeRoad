package com.team.carescape.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.team.carescape.game.DialColor
import com.team.carescape.game.LockDirection
import com.team.carescape.ui.locks.ColorDialLock
import com.team.carescape.ui.locks.DirectionSwipeLock
import com.team.carescape.ui.locks.EnglishDialLock
import com.team.carescape.ui.locks.NumberDialLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Controlled component tests: no SDK runtime, trip store, or puzzle answer is involved. */
@RunWith(AndroidJUnit4::class)
class LockComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun colorDialCyclesRedYellowGreenBlueAndReadsAnswerOnlyOnSubmit() {
        val value = mutableStateOf(List(4) { DialColor.BLUE })
        var submitted: List<DialColor>? = null
        var submitCount = 0
        rule.setContent {
            MaterialTheme {
                ColorDialLock(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onSubmit = { submitted = it; submitCount++ },
                )
            }
        }

        val first = rule.onNodeWithContentDescription("색상 다이얼 1")
        for (expected in listOf(DialColor.RED, DialColor.YELLOW, DialColor.GREEN, DialColor.BLUE)) {
            first.performTouchInput { click() }
            rule.runOnIdle {
                assertEquals(listOf(expected, DialColor.BLUE, DialColor.BLUE, DialColor.BLUE), value.value)
                assertNull(submitted)
                assertEquals(0, submitCount)
            }
        }
        rule.onNodeWithContentDescription("색상 다이얼 2").performTouchInput { click() }
        rule.onNodeWithText("제출").performClick()
        rule.runOnIdle {
            assertEquals(listOf(DialColor.BLUE, DialColor.RED, DialColor.BLUE, DialColor.BLUE), submitted)
            assertEquals(1, submitCount)
        }
    }

    @Test
    fun numberDialWrapsNineToZeroAndSwipesWithoutSubmitting() {
        val value = mutableStateOf(listOf(9, 0, 0, 0))
        var submitted: List<Int>? = null
        rule.setContent {
            MaterialTheme {
                NumberDialLock(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onSubmit = { submitted = it },
                )
            }
        }

        val first = rule.onNodeWithContentDescription("숫자 다이얼 1")
        first.performTouchInput { click() }
        rule.runOnIdle { assertEquals(listOf(0, 0, 0, 0), value.value); assertNull(submitted) }
        first.performTouchInput {
            swipe(start = Offset(center.x, height * 0.8f), end = Offset(center.x, height * 0.2f), durationMillis = 300)
        }
        rule.runOnIdle {
            assertEquals(listOf(1, 0, 0, 0), value.value)
            assertNull(submitted)
        }
        first.performTouchInput {
            swipe(start = Offset(center.x, height * 0.2f), end = Offset(center.x, height * 0.8f), durationMillis = 300)
        }
        rule.runOnIdle { assertEquals(listOf(0, 0, 0, 0), value.value); assertNull(submitted) }
        first.performTouchInput {
            swipe(start = Offset(center.x, height * 0.2f), end = Offset(center.x, height * 0.8f), durationMillis = 300)
        }
        rule.runOnIdle { assertEquals(listOf(9, 0, 0, 0), value.value); assertNull(submitted) }
        val expected = rule.runOnIdle { value.value.toList() }
        rule.onNodeWithText("제출").performClick()
        rule.runOnIdle { assertEquals(expected, submitted) }
    }

    @Test
    fun colorDialVerticalSwipesMoveOneStepWithoutSubmitting() {
        val value = mutableStateOf(List(4) { DialColor.GREEN })
        var submitCount = 0
        rule.setContent {
            MaterialTheme {
                ColorDialLock(value.value, { value.value = it }, { submitCount++ })
            }
        }
        val first = rule.onNodeWithContentDescription("색상 다이얼 1")
        first.performTouchInput {
            swipe(start = Offset(center.x, height * 0.8f), end = Offset(center.x, height * 0.2f), durationMillis = 300)
        }
        rule.runOnIdle {
            assertEquals(listOf(DialColor.BLUE, DialColor.GREEN, DialColor.GREEN, DialColor.GREEN), value.value)
            assertEquals(0, submitCount)
        }
        first.performTouchInput {
            swipe(start = Offset(center.x, height * 0.2f), end = Offset(center.x, height * 0.8f), durationMillis = 300)
        }
        rule.runOnIdle {
            assertEquals(List(4) { DialColor.GREEN }, value.value)
            assertEquals(0, submitCount)
        }
    }

    @Test
    fun centralDirectionSwipesAccumulateInOrderAndResetClearsTheWholeAnswer() {
        val value = mutableStateOf(emptyList<LockDirection>())
        var submitted: List<LockDirection>? = null
        rule.setContent {
            MaterialTheme {
                DirectionSwipeLock(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onSubmit = { submitted = it },
                )
            }
        }

        rule.onNodeWithText("제출").assertIsNotEnabled()
        val control = rule.onNodeWithContentDescription("방향 자물쇠 조작부")
        val expected = listOf(LockDirection.UP, LockDirection.DOWN, LockDirection.RIGHT, LockDirection.LEFT)
        expected.forEachIndexed { index, direction ->
            control.performTouchInput {
                val end = when (direction) {
                    LockDirection.UP -> Offset(center.x, height * 0.05f)
                    LockDirection.DOWN -> Offset(center.x, height * 0.95f)
                    LockDirection.RIGHT -> Offset(width * 0.95f, center.y)
                    LockDirection.LEFT -> Offset(width * 0.05f, center.y)
                }
                swipe(start = center, end = end, durationMillis = 300)
            }
            rule.runOnIdle { assertEquals(expected.take(index + 1), value.value); assertNull(submitted) }
        }
        rule.onNodeWithText("제출").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(expected, submitted) }

        rule.onNodeWithText("초기화").performClick()
        rule.runOnIdle { assertTrue(value.value.isEmpty()) }
        rule.onNodeWithText("제출").assertIsNotEnabled()
        rule.onNodeWithText("초기화").assertIsNotEnabled()
    }

    @Test
    fun directionTapSmallMotionOutsideStartAndCancelledGestureDoNotAppendAnAnswer() {
        val value = mutableStateOf(emptyList<LockDirection>())
        var submitCount = 0
        rule.setContent {
            MaterialTheme {
                DirectionSwipeLock(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onSubmit = { submitCount++ },
                )
            }
        }

        val control = rule.onNodeWithContentDescription("방향 자물쇠 조작부")
        control.performTouchInput { click() }
        control.performTouchInput { swipe(start = center, end = center + Offset(2f, 2f), durationMillis = 200) }
        control.performTouchInput {
            swipe(start = Offset(1f, 1f), end = Offset(width * 0.95f, height * 0.95f), durationMillis = 300)
        }
        control.performTouchInput {
            down(pointerId = 0, position = center)
            moveTo(Offset(width * 0.95f, center.y))
            cancel()
        }
        rule.runOnIdle { assertTrue(value.value.isEmpty()); assertEquals(0, submitCount) }
        rule.onNodeWithText("제출").assertIsNotEnabled()
    }

    @Test
    fun englishKeyboardInputNormalizesAsciiAndImeDoneDoesNotSubmit() {
        val value = mutableStateOf("")
        var submitted: String? = null
        rule.setContent {
            MaterialTheme {
                EnglishDialLock(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onSubmit = { submitted = it },
                )
            }
        }

        rule.onNodeWithText("제출").assertIsNotEnabled()
        val input = rule.onNodeWithContentDescription("영어 자물쇠 입력")
        input.performClick()
        input.performTextReplacement("ab1가c")
        rule.runOnIdle { assertEquals("ABC", value.value); assertNull(submitted) }
        rule.onNodeWithText("제출").assertIsNotEnabled()
        input.performTextReplacement("li!g한h1tmore")
        rule.runOnIdle { assertEquals("LIGHT", value.value); assertNull(submitted) }
        input.performImeAction()
        rule.runOnIdle { assertNull(submitted) }
        rule.onNodeWithText("제출").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("LIGHT", submitted) }
    }

    @Test
    fun disabledColorDialRejectsPointerAndSubmit() {
        val initial = List(4) { DialColor.RED }
        val value = mutableStateOf(initial)
        var submitCount = 0
        rule.setContent {
            MaterialTheme {
                ColorDialLock(value.value, { value.value = it }, { submitCount++ }, enabled = false)
            }
        }
        rule.onNodeWithContentDescription("색상 다이얼 1").performTouchInput { click() }
        rule.onNodeWithText("제출").assertIsNotEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(initial, value.value); assertEquals(0, submitCount) }
    }

    @Test
    fun disabledNumberDialRejectsSwipeAndSubmit() {
        val initial = listOf(1, 2, 3, 4)
        val value = mutableStateOf(initial)
        var submitCount = 0
        rule.setContent {
            MaterialTheme {
                NumberDialLock(value.value, { value.value = it }, { submitCount++ }, enabled = false)
            }
        }
        rule.onNodeWithContentDescription("숫자 다이얼 1").performTouchInput {
            swipe(start = Offset(center.x, height * 0.8f), end = Offset(center.x, height * 0.2f), durationMillis = 300)
        }
        rule.onNodeWithText("제출").assertIsNotEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(initial, value.value); assertEquals(0, submitCount) }
    }

    @Test
    fun disabledDirectionControlRejectsSwipeResetAndSubmit() {
        val initial = listOf(LockDirection.UP)
        val value = mutableStateOf(initial)
        var submitCount = 0
        rule.setContent {
            MaterialTheme {
                DirectionSwipeLock(value.value, { value.value = it }, { submitCount++ }, enabled = false)
            }
        }
        rule.onNodeWithContentDescription("방향 자물쇠 조작부").performTouchInput {
            swipe(start = center, end = Offset(width * 0.95f, center.y), durationMillis = 300)
        }
        rule.onNodeWithText("초기화").assertIsNotEnabled().performTouchInput { click() }
        rule.onNodeWithText("제출").assertIsNotEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(initial, value.value); assertEquals(0, submitCount) }
    }

    @Test
    fun disabledEnglishFieldAndSubmitCannotDeliverAnAnswer() {
        val value = mutableStateOf("LIGHT")
        var submitCount = 0
        rule.setContent {
            MaterialTheme {
                EnglishDialLock(value.value, { value.value = it }, { submitCount++ }, enabled = false)
            }
        }
        rule.onNodeWithContentDescription("영어 자물쇠 입력").assertIsNotEnabled()
            .performTouchInput { click() }
        rule.onNodeWithText("제출").assertIsNotEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals("LIGHT", value.value); assertEquals(0, submitCount) }
    }
}
