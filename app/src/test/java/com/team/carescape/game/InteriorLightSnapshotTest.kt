package com.team.carescape.game

import org.junit.Assert.*
import org.junit.Test

class InteriorLightSnapshotTest {
    private fun snapshot(on: Int) = InteriorLightSnapshot(
        InteriorLightSnapshot.AREAS.mapIndexed { i, area -> area to (i < on) }.toMap(),
        receivedElapsedMillis = 1_000, ready = true,
    )

    @Test fun alreadyOnLightsCountOnceAndTurningOffReducesTheCurrentCount() {
        val all = snapshot(4)
        assertEquals(4, all.validCount(1_000))
        repeat(10) { assertEquals(4, all.validCount(2_000)) }
        assertEquals(3, all.copy(states = all.states + ("rear_right" to false)).validCount(2_000))
        assertEquals(0, snapshot(0).validCount(2_000))
    }

    @Test fun missingUnknownUnsupportedStaleFutureAndDuplicateGlobalDataCannotBecomeAnAnswer() {
        val all = snapshot(4)
        assertNull(InteriorLightSnapshot().validCount(2_000))
        assertNull(all.copy(ready = false).validCount(2_000))
        assertNull(all.copy(states = all.states - "rear_right").validCount(2_000))
        assertNull(all.copy(states = all.states + ("cabin" to true)).validCount(2_000))
        assertNull(all.validCount(10_001))
        assertNull(all.validCount(999))
    }

    @Test fun oneBaseFireMakesAllFiveSignalsReachableAndFeedbackUsesBeaconCount() {
        val current = SiyeokFlow5.episode.chapters[3].puzzleSteps.single { it.id == "c4_beacon" }
        for (lights in 0..4) {
            val beacons = lights + 1
            assertEquals(beacons, BeaconLightMapping.beaconCount(lights))
            assertEquals(lights, StoryLockDefinition.InteriorLightCount(beacons).requiredLights)
            assertEquals(beacons.toString(), StoryPuzzles.answerKey(StoryPuzzleInput.InteriorLightCount(lights)))
        }
        assertEquals(3, (current.lock as StoryLockDefinition.InteriorLightCount).requiredLights)
        assertNotNull(current.feedback("5"))
        assertNotEquals(current.feedback("3"), current.feedback("5"))
    }

    @Test fun currentFlowUsesCountWhileLegacyBeaconAndCompletedIdsStayCompatible() {
        val current = SiyeokFlow5.episode.chapters[3].puzzleSteps.single { it.id == "c4_beacon" }
        val old = SiyeokFlow4.episode.chapters[3].puzzleSteps.single { it.id == "c4_beacon" }
        assertTrue(current.lock is StoryLockDefinition.InteriorLightCount)
        assertTrue(old.lock is StoryLockDefinition.Sequence)
        assertEquals(current.id, old.id)
        assertEquals(StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(current, StoryPuzzleInput.InteriorLightCount(3)))
        for (count in listOf(0, 1, 2, 4)) assertEquals(StoryPuzzleResult.WRONG, StoryPuzzles.evaluate(current, StoryPuzzleInput.InteriorLightCount(count)))
        assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(current, StoryPuzzleInput.InteriorLightCount(-1)))
        assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(current, StoryPuzzleInput.InteriorLightCount(5)))
        assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(current, StoryPuzzleInput.Sequence(List(4) { "FIRE" })))
        assertEquals(StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(old, StoryPuzzleInput.Sequence(List(4) { "FIRE" })))
    }
}
