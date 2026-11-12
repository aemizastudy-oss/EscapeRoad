package com.example.roadescape.game

import com.example.roadescape.model.Direction
import com.example.roadescape.model.LockDefinition
import com.example.roadescape.model.LockInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceAnswerParserTest {
    private val numeric = LockDefinition.Numeric(4, "0427")
    private val directions = LockDefinition.Sequence(listOf(Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT))

    @Test
    fun koreanDigitNamesKeepTheLeadingZero() {
        assertEquals(LockInput.Numeric("0427"), VoiceAnswerParser.parse(numeric, "공사이칠"))
        assertEquals(LockInput.Numeric("0427"), VoiceAnswerParser.parse(numeric, "영 사이 칠"))
    }

    @Test
    fun asciiDigitsWhitespaceAndExplicitSeparatorsProduceTheSameProposal() {
        for (text in listOf("0427", "0 4 2 7", " 0, 4, 2, 7 ", "0\t4\n2 7")) {
            assertEquals(text, LockInput.Numeric("0427"), VoiceAnswerParser.parse(numeric, text))
        }
    }

    @Test
    fun aWellFormedWrongAnswerIsNotReplacedByTheKnownSolution() {
        val proposal = VoiceAnswerParser.parse(numeric, "공사이팔")
        assertEquals(LockInput.Numeric("0428"), proposal)
        assertEquals(LockEvaluator.Result.Incorrect, LockEvaluator.evaluate(numeric, proposal!!))
    }

    @Test
    fun ambiguousSignsDecimalsUnknownWordsAndWrongLengthsAreRejected() {
        for (text in listOf("-0427", "+0427", "04.27", "사십이칠", "한둘셋넷", "정답은 공사이칠", "427", "00427", "０４２７", "")) {
            assertNull(text, VoiceAnswerParser.parse(numeric, text))
        }
    }

    @Test
    fun directionTokensKeepTheirExactOrderAndRejectUnknownOrMissingTokens() {
        val expected = LockInput.Sequence(directions.answer)
        assertEquals(expected, VoiceAnswerParser.parse(directions, "위 오른쪽 아래 왼쪽"))
        assertEquals(expected, VoiceAnswerParser.parse(directions, "up, right → down left"))
        val wrong = VoiceAnswerParser.parse(directions, "왼쪽 아래 오른쪽 위")
        assertEquals(LockInput.Sequence(directions.answer.reversed()), wrong)
        assertEquals(LockEvaluator.Result.Incorrect, LockEvaluator.evaluate(directions, wrong!!))
        assertNull(VoiceAnswerParser.parse(directions, "위 오른쪽 그리고 아래 왼쪽"))
        assertNull(VoiceAnswerParser.parse(directions, "위 오른쪽 아래"))
        val repeated = LockDefinition.Sequence(listOf(Direction.UP, Direction.UP))
        assertEquals(LockInput.Sequence(repeated.answer), VoiceAnswerParser.parse(repeated, "위 위"))
    }

    @Test
    fun unsupportedStateCombinationNeverGuessesValuesFromSpeech() {
        val combination = LockDefinition.StateCombination(channel = 3, level = 2)
        for (text in listOf("채널 삼 레벨 이", "3 2", "channel=3 level=2", "삼이", "")) {
            assertNull(text, VoiceAnswerParser.parse(combination, text))
        }
    }
}
