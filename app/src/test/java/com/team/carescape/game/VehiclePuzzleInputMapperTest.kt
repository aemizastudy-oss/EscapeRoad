package com.team.carescape.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiclePuzzleInputMapperTest {
    private val started = 100_000L
    private fun context(
        scope: String? = "session/chapter/puzzle/reset-0",
        eligible: Boolean = true,
        at: Long = started,
        generation: Long = 1L,
        floor: Long = 100L,
    ) = VehiclePuzzleInputContext(scope, eligible, at, generation, floor)
    private fun mapper() = VehiclePuzzleInputMapper().also { it.reset(context()) }
    private fun sample(
        active: Boolean?,
        sequence: Long,
        control: CarControl = CarControl.WIPER,
        at: Long = started,
        generation: Long = 1L,
    ) = VehiclePuzzleSample(control, active, generation, sequence, at)

    /** The reader emits the inactive direction first from one LEFT/RIGHT/OFF SDK value. */
    private fun turnSamples(
        direction: CarControl?,
        firstSequence: Long,
        at: Long = started,
        generation: Long = 1L,
    ): List<VehiclePuzzleSample> {
        require(direction == null || direction == CarControl.TURN_LEFT || direction == CarControl.TURN_RIGHT)
        val controls = if (direction == CarControl.TURN_LEFT) {
            listOf(CarControl.TURN_RIGHT, CarControl.TURN_LEFT)
        } else listOf(CarControl.TURN_LEFT, CarControl.TURN_RIGHT)
        return controls.mapIndexed { index, control ->
            sample(direction == control, firstSequence + index, control, at, generation)
        }
    }

    private fun acceptTurn(
        mapper: VehiclePuzzleInputMapper,
        direction: CarControl?,
        firstSequence: Long,
        at: Long = started,
        generation: Long = 1L,
    ): List<CarControl> = turnSamples(direction, firstSequence, at, generation).mapNotNull { mapper.accept(it, at) }

    @Test fun initialOnOrOffIsABaselineRatherThanAnAnswer() {
        listOf(false, true).forEach { initial ->
            val mapper = mapper()
            assertNull(mapper.accept(sample(initial, 101L), started))
            assertNull(mapper.accept(sample(initial, 102L), started))
            assertTrue(mapper.snapshot(CarControl.WIPER, started).isReady)
        }
    }

    @Test fun aFreshSeedBeforeTheFloorAllowsTheNextActualEdge() {
        val mapper = mapper()
        assertTrue(mapper.seedBaseline(sample(false, 99L, at = started - 100L)))
        assertNull(mapper.accept(sample(true, 100L), started))
        assertEquals(CarControl.WIPER, mapper.accept(sample(true, 101L), started))
        assertNull(mapper.accept(sample(true, 101L), started))
    }

    @Test fun onModeChangesAndRepeatedWindowMovementDoNotCountAgain() {
        listOf(CarControl.WIPER, CarControl.WINDOW, CarControl.HAZARD).forEach { control ->
            val mapper = mapper()
            mapper.seedBaseline(sample(false, 99L, control))
            assertEquals(control, mapper.accept(sample(true, 101L, control), started))
            repeat(6) { assertNull(mapper.accept(sample(true, 102L + it, control), started)) }
            assertNull(mapper.accept(sample(false, 108L, control), started))
            assertEquals(control, mapper.accept(sample(true, 109L, control), started))
        }
    }

    @Test fun seedOfAnAlreadyActiveDeviceDoesNotReplayIt() {
        val mapper = mapper()
        mapper.seedBaseline(sample(true, 99L))
        assertNull(mapper.accept(sample(true, 101L), started))
        assertNull(mapper.accept(sample(false, 102L), started))
        assertEquals(CarControl.WIPER, mapper.accept(sample(true, 103L), started))
    }

    @Test fun interleavedControlsKeepTheirOwnBaselineAndCallbackOrder() {
        val mapper = mapper()
        mapper.seedBaseline(sample(false, 97L, CarControl.WIPER))
        mapper.seedBaseline(sample(false, 98L, CarControl.HAZARD))
        mapper.seedBaseline(sample(false, 99L, CarControl.WINDOW))
        val callbacks = listOf(
            sample(true, 101L, CarControl.WIPER),
            sample(true, 102L, CarControl.HAZARD),
            sample(false, 103L, CarControl.WIPER),
            sample(true, 104L, CarControl.WIPER),
            sample(true, 105L, CarControl.WINDOW),
        )
        assertEquals(
            listOf(CarControl.WIPER, CarControl.HAZARD, CarControl.WIPER, CarControl.WINDOW),
            callbacks.mapNotNull { mapper.accept(it, started) },
        )
    }

    @Test fun unknownReadingsRequireAnotherBaselineBeforeCounting() {
        val mapper = mapper()
        mapper.seedBaseline(sample(false, 99L))
        assertNull(mapper.accept(sample(null, 101L), started))
        assertEquals(VehiclePuzzleInputStatus.UNKNOWN, mapper.snapshot(CarControl.WIPER, started).status)
        assertNull(mapper.accept(sample(true, 102L), started))
        assertNull(mapper.accept(sample(false, 103L), started))
        assertEquals(CarControl.WIPER, mapper.accept(sample(true, 104L), started))
    }

    @Test fun staleAndFutureCallbacksCannotCreateAnEdge() {
        listOf(started - 9_001L, started + 1L, -1L).forEach { invalidTimestamp ->
            val mapper = mapper()
            mapper.seedBaseline(sample(false, 99L))
            assertNull(mapper.accept(sample(true, 101L, at = invalidTimestamp), started))
            assertFalse(mapper.snapshot(CarControl.WIPER, started).isReady)
            assertNull(mapper.accept(sample(true, 102L), started))
        }
    }

    @Test fun oldSeedIsNotRefreshedAndTheNineSecondBoundaryIsInclusive() {
        val mapper = mapper()
        assertTrue(mapper.seedBaseline(sample(false, 99L, at = started - 9_000L)))
        assertTrue(mapper.snapshot(CarControl.WIPER, started).isReady)
        assertFalse(mapper.snapshot(CarControl.WIPER, started + 1L).isReady)
        val old = mapper()
        assertFalse(old.seedBaseline(sample(false, 99L, at = started - 9_001L)))
        assertEquals(VehiclePuzzleInputStatus.STALE, old.snapshot(CarControl.WIPER, started).status)
    }

    @Test fun aLongSamplingGapEstablishesABaselineInsteadOfInventingAnAction() {
        val mapper = mapper()
        mapper.seedBaseline(sample(false, 99L))
        assertNull(mapper.accept(sample(true, 101L, at = started + 9_001L), started + 9_001L))
        assertNull(mapper.accept(sample(false, 102L, at = started + 9_002L), started + 9_002L))
        assertEquals(CarControl.WIPER, mapper.accept(sample(true, 103L, at = started + 9_003L), started + 9_003L))
    }

    @Test fun duplicateOrOutOfOrderSequenceCannotRearmAnActiveDevice() {
        val mapper = mapper()
        mapper.seedBaseline(sample(false, 99L))
        assertEquals(CarControl.WIPER, mapper.accept(sample(true, 102L), started))
        assertNull(mapper.accept(sample(false, 101L), started))
        assertNull(mapper.accept(sample(false, 102L), started))
        assertNull(mapper.accept(sample(true, 103L), started))
    }

    @Test fun theSequenceIsGlobalAcrossControls() {
        val mapper = mapper()
        mapper.seedBaseline(sample(false, 98L, CarControl.HAZARD))
        mapper.seedBaseline(sample(false, 99L, CarControl.WIPER))
        assertEquals(CarControl.WIPER, mapper.accept(sample(true, 102L), started))
        assertNull(mapper.accept(sample(true, 101L, CarControl.HAZARD), started))
        assertEquals(CarControl.HAZARD, mapper.accept(sample(true, 103L, CarControl.HAZARD), started))
    }

    @Test fun aCallbackWhoseTimestampMovesBackwardsInvalidatesTheBaseline() {
        val mapper = mapper()
        mapper.seedBaseline(sample(false, 99L))
        assertNull(mapper.accept(sample(false, 101L, at = started + 10L), started + 10L))
        assertNull(mapper.accept(sample(true, 102L, at = started + 9L), started + 10L))
        assertEquals(VehiclePuzzleInputStatus.INVALID_TIMESTAMP, mapper.snapshot(CarControl.WIPER, started + 10L).status)
        assertNull(mapper.accept(sample(true, 103L, at = started + 11L), started + 11L))
    }

    @Test fun changingPuzzleOrResettingRejectsPendingSamplesAtTheCapturedFloor() {
        val mapper = mapper()
        mapper.seedBaseline(sample(false, 99L))
        mapper.reset(context(scope = "another-puzzle", at = started + 10L, floor = 104L))
        assertNull(mapper.accept(sample(false, 103L, at = started + 10L), started + 10L))
        assertNull(mapper.accept(sample(true, 104L, at = started + 10L), started + 10L))
        assertNull(mapper.accept(sample(true, 105L, at = started + 11L), started + 11L))
        assertNull(mapper.accept(sample(false, 106L, at = started + 12L), started + 12L))
        assertEquals(CarControl.WIPER, mapper.accept(sample(true, 107L, at = started + 13L), started + 13L))
    }

    @Test fun sameMillisecondPendingSamplesAreRejectedBySequenceRatherThanClockGuessing() {
        val mapper = mapper()
        mapper.reset(context(floor = 103L))
        mapper.seedBaseline(sample(true, 103L))
        assertNull(mapper.accept(sample(false, 102L), started))
        assertNull(mapper.accept(sample(true, 103L), started))
        assertNull(mapper.accept(sample(true, 104L), started))
        assertNull(mapper.accept(sample(false, 105L), started))
        assertEquals(CarControl.WIPER, mapper.accept(sample(true, 106L), started))
    }

    @Test fun newlyNumberedCallbacksFromBeforeTheContextCannotBecomeInputs() {
        val mapper = mapper()
        mapper.reset(context(at = started + 100L))
        assertNull(mapper.accept(sample(false, 101L, at = started + 99L), started + 100L))
        assertEquals(VehiclePuzzleInputStatus.BEFORE_CONTEXT, mapper.snapshot(CarControl.WIPER, started + 100L).status)
        assertNull(mapper.accept(sample(true, 102L, at = started + 100L), started + 100L))
    }

    @Test fun safetyTourGateForegroundAndModeLossCannotReplayThePreviousEdge() {
        listOf("not-P", "moving", "UX-blocked", "tour-not-ready", "background", "mock-mode").forEach { boundary ->
            val mapper = mapper()
            mapper.seedBaseline(sample(false, 99L))
            mapper.reset(context(scope = boundary, eligible = false, floor = 100L))
            assertNull(mapper.accept(sample(false, 101L), started))
            assertNull(mapper.accept(sample(true, 102L), started))
            assertFalse(mapper.isReady(listOf(CarControl.WIPER), started))
            mapper.reset(context(scope = "returned-$boundary", at = started + 1L, floor = 102L))
            mapper.seedBaseline(sample(true, 102L))
            assertNull(mapper.accept(sample(true, 103L, at = started + 1L), started + 1L))
        }
    }

    @Test fun reconnectRequiresANewContextAndNeverReplaysAnAlreadyActiveDevice() {
        val mapper = mapper()
        mapper.seedBaseline(sample(false, 99L))
        assertNull(mapper.accept(sample(true, 101L, generation = 2L), started))
        assertEquals(VehiclePuzzleInputStatus.CONNECTION_CHANGED, mapper.snapshot(CarControl.WIPER, started).status)
        assertNull(mapper.accept(sample(true, 102L), started))
        mapper.reset(context(generation = 2L, floor = 102L))
        assertNull(mapper.accept(sample(true, 103L, generation = 2L), started))
        assertNull(mapper.accept(sample(false, 104L, generation = 1L), started))
        assertNull(mapper.accept(sample(true, 105L, generation = 2L), started))
        assertNull(mapper.accept(sample(false, 106L, generation = 2L), started))
        assertEquals(CarControl.WIPER, mapper.accept(sample(true, 107L, generation = 2L), started))
    }

    @Test fun resettingToAnOldGenerationCannotResurrectItsBaseline() {
        val mapper = mapper()
        mapper.accept(sample(false, 101L, generation = 2L), started)
        mapper.reset(context(generation = 1L))
        assertFalse(mapper.seedBaseline(sample(false, 99L)))
        assertNull(mapper.accept(sample(true, 102L), started))
    }

    @Test fun aSeedCannotReplaceANewerObservationOrAcceptTheWrongGeneration() {
        val mapper = mapper()
        mapper.seedBaseline(sample(false, 99L))
        assertEquals(CarControl.WIPER, mapper.accept(sample(true, 101L), started))
        assertFalse(mapper.seedBaseline(sample(false, 98L)))
        assertFalse(mapper.seedBaseline(sample(false, 102L, generation = 0L)))
        assertNull(mapper.accept(sample(true, 103L), started))
    }

    @Test fun readinessIsPerControlAndUnavailableHazardIsNotInvented() {
        val mapper = mapper()
        mapper.seedBaseline(sample(false, 98L, CarControl.WIPER))
        mapper.seedBaseline(sample(null, 99L, CarControl.HAZARD))
        assertTrue(mapper.isReady(listOf(CarControl.WIPER), started))
        assertFalse(mapper.isReady(listOf(CarControl.WIPER, CarControl.HAZARD), started))
        assertFalse(mapper.isReady(emptyList(), started))
        assertEquals(VehiclePuzzleInputStatus.WAITING_FOR_BASELINE, mapper.snapshots(started).getValue(CarControl.WINDOW).status)
        assertEquals(VehiclePuzzleInputStatus.UNSUPPORTED_CONTROL, mapper.snapshot(CarControl.HEADLIGHT, started).status)
        assertNull(mapper.accept(sample(true, 101L, CarControl.HEADLIGHT), started))
    }

    @Test fun turnSignalFirstLeftRightOrOffNeverReplaysAnAnswer() {
        listOf(null, CarControl.TURN_LEFT, CarControl.TURN_RIGHT).forEach { direction ->
            val mapper = mapper()
            assertTrue(acceptTurn(mapper, direction, 101L).isEmpty())
            assertTrue(acceptTurn(mapper, direction, 103L).isEmpty())
            assertTrue(mapper.isReady(listOf(CarControl.TURN_LEFT, CarControl.TURN_RIGHT), started))
        }
    }

    @Test fun turnSignalDirectSwitchCountsOnlyTheNewDirectionAndOffRearmsIt() {
        val mapper = mapper()
        turnSamples(null, 98L).forEach { assertTrue(mapper.seedBaseline(it)) }
        assertEquals(listOf(CarControl.TURN_RIGHT), acceptTurn(mapper, CarControl.TURN_RIGHT, 101L))
        assertTrue(acceptTurn(mapper, CarControl.TURN_RIGHT, 103L).isEmpty())
        assertEquals(listOf(CarControl.TURN_LEFT), acceptTurn(mapper, CarControl.TURN_LEFT, 105L))
        assertTrue(acceptTurn(mapper, CarControl.TURN_LEFT, 107L).isEmpty())
        assertTrue(acceptTurn(mapper, null, 109L).isEmpty())
        assertEquals(listOf(CarControl.TURN_LEFT), acceptTurn(mapper, CarControl.TURN_LEFT, 111L))
        assertTrue(acceptTurn(mapper, null, 113L).isEmpty())
        assertEquals(listOf(CarControl.TURN_RIGHT), acceptTurn(mapper, CarControl.TURN_RIGHT, 115L))
    }

    @Test fun unknownTurnSignalClearsBothBaselinesBeforeRecovery() {
        val mapper = mapper()
        turnSamples(null, 98L).forEach { mapper.seedBaseline(it) }
        assertNull(mapper.accept(sample(null, 101L, CarControl.TURN_LEFT), started))
        assertNull(mapper.accept(sample(null, 102L, CarControl.TURN_RIGHT), started))
        listOf(CarControl.TURN_LEFT, CarControl.TURN_RIGHT).forEach { control ->
            assertEquals(VehiclePuzzleInputStatus.UNKNOWN, mapper.snapshot(control, started).status)
        }
        assertTrue(acceptTurn(mapper, CarControl.TURN_RIGHT, 103L).isEmpty())
        assertTrue(acceptTurn(mapper, CarControl.TURN_RIGHT, 105L).isEmpty())
        assertTrue(acceptTurn(mapper, null, 107L).isEmpty())
        assertEquals(listOf(CarControl.TURN_RIGHT), acceptTurn(mapper, CarControl.TURN_RIGHT, 109L))
    }

    @Test fun reconnectedTurnSignalCannotUseAnOldBaselineOrReplayAnActiveDirection() {
        val mapper = mapper()
        turnSamples(null, 98L).forEach { mapper.seedBaseline(it) }
        assertTrue(acceptTurn(mapper, CarControl.TURN_RIGHT, 101L, generation = 2L).isEmpty())
        assertEquals(VehiclePuzzleInputStatus.CONNECTION_CHANGED, mapper.snapshot(CarControl.TURN_RIGHT, started).status)
        mapper.reset(context(generation = 2L, floor = 102L))
        assertTrue(acceptTurn(mapper, CarControl.TURN_RIGHT, 103L, generation = 2L).isEmpty())
        assertTrue(acceptTurn(mapper, null, 105L, generation = 1L).isEmpty())
        assertTrue(acceptTurn(mapper, CarControl.TURN_RIGHT, 107L, generation = 2L).isEmpty())
        assertEquals(listOf(CarControl.TURN_LEFT), acceptTurn(mapper, CarControl.TURN_LEFT, 109L, generation = 2L))
    }

    @Test fun turnSignalAfterLongGapRequiresABaselineBeforeANewActivation() {
        val mapper = mapper()
        turnSamples(null, 98L).forEach { mapper.seedBaseline(it) }
        assertTrue(acceptTurn(mapper, CarControl.TURN_LEFT, 101L, at = started + 9_001L).isEmpty())
        assertTrue(acceptTurn(mapper, CarControl.TURN_LEFT, 103L, at = started + 9_002L).isEmpty())
        assertTrue(acceptTurn(mapper, null, 105L, at = started + 9_003L).isEmpty())
        assertEquals(listOf(CarControl.TURN_LEFT), acceptTurn(mapper, CarControl.TURN_LEFT, 107L, at = started + 9_004L))
    }

    @Test fun duplicateOrDelayedTurnSignalPairsCannotRearmAnActiveDirection() {
        val mapper = mapper()
        turnSamples(null, 98L).forEach { mapper.seedBaseline(it) }
        assertEquals(listOf(CarControl.TURN_RIGHT), acceptTurn(mapper, CarControl.TURN_RIGHT, 103L))
        assertTrue(acceptTurn(mapper, null, 101L).isEmpty())
        assertTrue(acceptTurn(mapper, null, 103L).isEmpty())
        assertTrue(acceptTurn(mapper, CarControl.TURN_RIGHT, 105L).isEmpty())
    }

    @Test fun directionalReadinessNeverInventsHazardAndSafetyLossRequiresANewBaseline() {
        val mapper = mapper()
        turnSamples(null, 98L).forEach { mapper.seedBaseline(it) }
        assertTrue(mapper.isReady(listOf(CarControl.TURN_LEFT, CarControl.TURN_RIGHT), started))
        assertFalse(mapper.isReady(listOf(CarControl.HAZARD), started))
        mapper.reset(context(eligible = false))
        assertTrue(acceptTurn(mapper, CarControl.TURN_RIGHT, 101L).isEmpty())
        assertFalse(mapper.isReady(listOf(CarControl.TURN_LEFT, CarControl.TURN_RIGHT), started))
        mapper.reset(context(at = started + 1L, floor = 102L))
        assertTrue(acceptTurn(mapper, CarControl.TURN_RIGHT, 103L, at = started + 1L).isEmpty())
        assertTrue(acceptTurn(mapper, null, 105L, at = started + 2L).isEmpty())
        assertEquals(listOf(CarControl.TURN_RIGHT), acceptTurn(mapper, CarControl.TURN_RIGHT, 107L, at = started + 3L))
    }
}
