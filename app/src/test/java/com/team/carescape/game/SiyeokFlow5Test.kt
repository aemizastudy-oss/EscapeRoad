package com.team.carescape.game

import com.team.carescape.data.StoredTrip
import com.team.carescape.data.TripStoreState
import com.team.carescape.data.upsertTrip
import com.team.carescape.data.validateStoredTrip
import com.team.carescape.model.TourClock
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourStartRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiyeokFlow5Test {
    private val episode = StoryCatalog.episodeFor(StoryCatalog.EXPLORE_FLOW_VERSION)
    private val scene = episode.chapters[0].scene!!
    private val clock = TourClock(1_800_000_000_000L, 1_000_000L, "boot-five")

    private fun answer(lock: StoryLockDefinition): StoryPuzzleInput = when (lock) {
        is StoryLockDefinition.Numeric -> StoryPuzzleInput.Numeric(lock.answer)
        is StoryLockDefinition.Directions -> StoryPuzzleInput.Directions(lock.answer)
        is StoryLockDefinition.Device -> StoryPuzzleInput.Device(lock.channel, lock.level)
        is StoryLockDefinition.Choice -> StoryPuzzleInput.Choice(lock.answer)
        is StoryLockDefinition.Order -> StoryPuzzleInput.Order(lock.answer)
        is StoryLockDefinition.CarSequence -> StoryPuzzleInput.CarSequence(lock.answer)
        is StoryLockDefinition.SymbolDial -> StoryPuzzleInput.Dial(lock.answer)
        is StoryLockDefinition.UseItem -> StoryPuzzleInput.UseItem(lock.answer)
        is StoryLockDefinition.Sequence -> StoryPuzzleInput.Sequence(lock.answer)
        is StoryLockDefinition.InteriorLightCount -> StoryPuzzleInput.InteriorLightCount(lock.requiredLights)
    }
    private fun atPuzzle(state: StoryState) = StoryEngine.returnToCar(StoryEngine.leaveCar(StoryEngine.verifiedArrival(state)))
    private fun start() = atPuzzle(StoryEngine.start(episode, StoryCatalog.EXPLORE_FLOW_VERSION))
    private fun spot(id: String) = scene.hotspots.single { it.id == id }

    /** 탐색 장면 하나를 (힌트처럼) 손댈 수 있는 곳부터 차례로 풀어 낸다. */
    private fun clearScene(start: StoryState, onState: (StoryState) -> Unit = {}): StoryState {
        var s = start
        while (s.phase == StoryPhase.PUZZLE && s.isExplore) {
            val target = s.actionableHotspots.first()
            s = if (target.locks.isEmpty()) StoryEngine.examine(s, target.id) else {
                val focused = s.copy(exploreFocus = s.activeLock(target))
                val r = StoryEngine.submitPuzzle(focused, answer(focused.currentPuzzle!!.lock))
                assertEquals(target.id, StoryPuzzleResult.CORRECT, r.result)
                r.state.copy(exploreFocus = null)
            }
            onState(s)
        }
        return s
    }

    private fun play(onState: (StoryState) -> Unit = {}): StoryState {
        var state = StoryEngine.start(episode, StoryCatalog.EXPLORE_FLOW_VERSION)
        episode.chapters.indices.forEach { index ->
            state = atPuzzle(state)
            state = if (state.isExplore) clearScene(state, onState) else {
                var s = state
                while (s.phase == StoryPhase.PUZZLE) { s = StoryEngine.submitPuzzle(s, answer(s.currentPuzzle!!.lock)).state; onState(s) }
                s
            }
            if (index < episode.finalChapterIndex) {
                state = StoryEngine.next(state)
                if (state.phase == StoryPhase.FINAL_REVEAL) state = StoryEngine.next(state)
            }
        }
        return state
    }

    @Test fun everyPlaceIsAnExploreSceneWithEachLockOnOneSpot() {
        assertEquals(StoryCatalog.EXPLORE_FLOW_VERSION, StoryCatalog.CURRENT_FLOW_VERSION)
        assertTrue(episode.chapters.all { it.scene != null })
        val s = start()
        assertTrue(s.isExplore)
        assertNull("탐색 장면은 자물쇠를 먼저 보여 주지 않는다", s.currentPuzzle)
        val allSpotIds = episode.chapters.flatMap { c -> c.scene!!.hotspots.map { it.id } }
        assertEquals(allSpotIds.distinct(), allSpotIds)
        episode.chapters.forEach { c ->
            val spots = c.scene!!.hotspots
            val locks = spots.flatMap { it.locks }
            // 모든 자물쇠는 그 장소의 조사할 곳 딱 하나에 걸려 있다.
            assertEquals(c.place, c.puzzleSteps.map { it.id }.sorted(), locks.sorted())
            val known = c.puzzleSteps.map { it.id } + spots.map { it.id }
            spots.forEach { spot ->
                assertTrue(spot.id, spot.requires == null || spot.requires in known)
                assertTrue(spot.id, spot.locks.isEmpty() || !spot.record)
                // 살펴보기 상자에 가리지 않도록 장면 아래쪽(570 이하)에 둔다.
                assertTrue(spot.id, spot.y + spot.h <= 572f)
                assertTrue(spot.id, spot.speaker == null || episode.speakerFor(spot.speaker) != null)
            }
        }
        // 문서·소지품·인물 기록은 모두 존재하는 단계나 조사할 곳에서 열린다.
        val sources = episode.chapters.flatMap { c -> c.puzzleSteps.map { it.id } + c.scene!!.hotspots.map { it.id } }.toSet()
        episode.documents.forEach { assertTrue(it.id, it.afterStep == null || it.afterStep in sources) }
        episode.caseItems.forEach { assertTrue(it.id, it.fromStep in sources && (it.usedBy == null || it.usedBy in sources)) }
        episode.people.flatMap { it.notes }.forEach { assertTrue(it.afterStep, it.afterStep in sources) }
    }

    @Test fun wrongVerdictInTheSceneStillFinishesWithTheNormalEnding() {
        var state = StoryEngine.start(episode, StoryCatalog.EXPLORE_FLOW_VERSION)
        episode.chapters.indices.forEach { index ->
            state = atPuzzle(state)
            while (state.phase == StoryPhase.PUZZLE) {
                val target = state.actionableHotspots.first()
                state = if (target.locks.isEmpty()) StoryEngine.examine(state, target.id) else {
                    val focused = state.copy(exploreFocus = state.activeLock(target))
                    val lock = focused.currentPuzzle!!.lock
                    val input = if (focused.exploreFocus == "c5_truth_why") StoryPuzzleInput.Choice("king") else answer(lock)
                    StoryEngine.submitPuzzle(focused, input).state.copy(exploreFocus = null)
                }
            }
            if (index < episode.finalChapterIndex) {
                state = StoryEngine.next(state)
                if (state.phase == StoryPhase.FINAL_REVEAL) state = StoryEngine.next(state)
            }
        }
        assertEquals(StoryPhase.ENDED, state.phase)
        assertEquals(listOf("c5_truth_why"), state.puzzleProgressForCurrent!!.verdictMisses)
        assertEquals(EndingKind.NORMAL, state.endingKind)
    }

    @Test fun hiddenSpotsAppearOnlyAfterTheirClue() {
        var s = start()
        assertFalse(s.isHotspotVisible(spot("x_wall")))
        assertFalse(s.isHotspotVisible(spot("x_behind")))
        assertNull("숨은 곳의 자물쇠는 열 수 없다", s.copy(exploreFocus = "c1_diary_lock").currentPuzzle)
        s = StoryEngine.examine(s, "x_bowl")
        assertTrue(s.isHotspotVisible(spot("x_wall")))
        val plaque = s.copy(exploreFocus = "c1_plaque")
        s = StoryEngine.submitPuzzle(plaque, answer(plaque.currentPuzzle!!.lock)).state
        assertTrue(s.isHotspotVisible(spot("x_behind")))
    }

    @Test fun examiningRecordsCluesAndUnlocksTheDiaryPage() {
        var s = start()
        assertFalse(s.visibleDocuments.any { it.id == "doc_physician_1" })
        s = StoryEngine.examine(s, "x_lantern")
        assertTrue(s.visibleDocuments.any { it.id == "doc_physician_1" })
        assertEquals(s, StoryEngine.examine(s, "x_lantern"))
        assertEquals("살펴보기만 하는 곳은 기록하지 않는다", s, StoryEngine.examine(s, "x_door"))
        assertEquals("자물쇠 자리는 살펴보기로 풀리지 않는다", s, StoryEngine.examine(s, "x_plaque"))
    }

    @Test fun locksSolveInAnyOrderAndHintsCountPerLock() {
        var s = StoryEngine.examine(StoryEngine.examine(start(), "x_bowl"), "x_lantern")
        // 수첩부터 연다(현판보다 먼저).
        s = s.copy(exploreFocus = "c1_diary_lock")
        s = StoryEngine.showPuzzleHint(s)
        s = StoryEngine.showPuzzleHint(s)
        assertEquals(mapOf("c1_diary_lock" to 2), s.puzzleProgressForCurrent!!.stepHints)
        assertEquals(1, s.awakening)
        val wrong = StoryEngine.submitPuzzle(s, StoryPuzzleInput.Numeric("0210"))
        assertEquals(StoryPuzzleResult.WRONG, wrong.result)
        s = StoryEngine.submitPuzzle(wrong.state, StoryPuzzleInput.Numeric("0213")).state
        assertEquals(listOf("c1_diary_lock"), s.puzzleProgressForCurrent!!.solvedSteps)
        assertTrue(s.inventory.any { it.id == "key_eul" })
        assertEquals(StoryPhase.PUZZLE, s.phase)
        // 다른 자물쇠는 힌트가 0부터 시작한다.
        assertEquals(0, s.copy(exploreFocus = "c1_plaque").puzzleProgressForCurrent!!.stepHints["c1_plaque"] ?: 0)
        val done = clearScene(s.copy(exploreFocus = null))
        assertEquals(StoryPhase.REVEALED, done.phase)
        assertTrue(done.puzzleProgressForCurrent!!.solved)
        assertEquals(listOfNotNull(episode.chapters[0].clue), done.clues)
    }

    @Test fun sceneHintCostsOneAndNeedsSomethingToPoint() {
        val s = start()
        val hinted = StoryEngine.sceneHint(s)
        assertEquals(1, hinted.awakening)
        assertEquals(1, hinted.hintsUsed)
        assertTrue(s.actionableHotspots.isNotEmpty())
    }

    @Test fun fullRunReachesTheTrueEnding() {
        val end = play()
        assertEquals(StoryPhase.ENDED, end.phase)
        assertEquals(EndingKind.TRUE, end.endingKind)
        assertEquals(episode.documents.map { it.id }, end.visibleDocuments.map { it.id })
    }

    @Test fun storedExploreTripsValidateAndCannotRollBack() {
        var trip = StoredTrip(TourMode.MOCK, "flow5", chapterIndex = 0, phase = StoryPhase.EN_ROUTE,
            startedAtEpochMillis = clock.wallTimeMillis, flowVersion = StoryCatalog.EXPLORE_FLOW_VERSION)
        var store = upsertTrip(TripStoreState(), trip)
        var snapshots = mutableListOf<StoredTrip>()
        play { next ->
            val records = TourCatalog.checkpoints.take(next.chapterIndex + 1).map { TourStartRecord("flow5", it.id, TourMode.MOCK, clock) }
            trip = trip.copy(chapterIndex = next.chapterIndex, phase = next.phase, items = next.items, clues = next.clues,
                puzzleProgress = next.puzzleProgress, tourRecords = records)
            validateStoredTrip(trip)
            store = upsertTrip(store, trip)
            if (next.chapterIndex == 0) snapshots.add(trip)
        }
        // 찾은 곳·푼 자물쇠를 지운 기록은 거부한다.
        val mid = snapshots.first { it.puzzleProgress.single().found.isNotEmpty() }
        val midStore = upsertTrip(TripStoreState(), mid)
        val forgot = mid.copy(puzzleProgress = mid.puzzleProgress.map { it.copy(found = emptyList()) })
        assertTrue(runCatching { upsertTrip(midStore, forgot) }.isFailure)
        val fake = mid.copy(puzzleProgress = mid.puzzleProgress.map { it.copy(found = it.found + "x_door") })
        assertTrue(runCatching { validateStoredTrip(fake) }.isFailure)
    }

    @Test fun currentSluiceUsesTurnSignalsWhileHistoricalFlowsKeepHazard() {
        val wiper = CarControl.WIPER
        val right = CarControl.TURN_RIGHT
        val left = CarControl.TURN_LEFT
        val hazard = CarControl.HAZARD
        val current = episode.chapters[1].puzzleSteps.single { it.id == "c2_gates" }
        val currentLock = current.lock as StoryLockDefinition.CarSequence
        val currentAnswer = listOf(right, right, right, left, right, right, right)
        val historicalAnswer = listOf(wiper, wiper, wiper, hazard, wiper, wiper, wiper)
        assertEquals(currentAnswer, currentLock.answer)
        assertEquals(setOf(right, left), currentLock.controls.toSet())
        assertEquals(7, currentLock.maxLength)
        assertEquals(StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(current, StoryPuzzleInput.CarSequence(currentAnswer)))
        assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(current, StoryPuzzleInput.CarSequence(historicalAnswer)))
        listOf(StoryCatalog.CASE_FLOW_VERSION, StoryCatalog.ITEM_FLOW_VERSION).forEach { version ->
            val historical = StoryCatalog.episodeFor(version).chapters[1].puzzleSteps.single { it.id == "c2_gates" }
            val lock = historical.lock as StoryLockDefinition.CarSequence
            assertEquals("flow $version", historicalAnswer, lock.answer)
            assertEquals("flow $version", setOf(wiper, hazard), lock.controls.toSet())
            assertEquals(StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(historical, StoryPuzzleInput.CarSequence(historicalAnswer)))
        }
    }

    @Test fun sluiceKeepsTheAllClosedTrapAndRejectsOpeningTwoGates() {
        val gates = episode.chapters[1].puzzleSteps.single { it.id == "c2_gates" }
        val allClosed = StoryPuzzleInput.CarSequence(List(7) { CarControl.TURN_RIGHT })
        assertEquals(StoryPuzzleResult.WRONG, StoryPuzzles.evaluate(gates, allClosed))
        assertEquals(2, gates.feedback(StoryPuzzles.answerKey(allClosed))!!.penalty)
        val twoOpen = StoryPuzzleInput.CarSequence(listOf(
            CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_LEFT,
            CarControl.TURN_RIGHT, CarControl.TURN_LEFT, CarControl.TURN_RIGHT,
        ))
        assertEquals(StoryPuzzleResult.WRONG, StoryPuzzles.evaluate(gates, twoOpen))
        val note = gates.feedback(StoryPuzzles.answerKey(twoOpen))!!
        assertEquals(0, note.penalty)
        assertTrue(note.message.contains("한 칸"))
    }

    @Test fun reservoirHeightGridHasTheApprovedBoardAndElevenMoveAnswer() {
        val channel = episode.chapters[2].puzzleSteps.single { it.id == "c3_channel" }
        val lock = channel.lock as StoryLockDefinition.Directions
        val grid = lock.heightGrid!!
        assertEquals(listOf(
            listOf(1, 16, 17, 15, 13), listOf(2, 18, 20, 19, 11), listOf(12, 24, 25, 21, 9),
            listOf(14, 23, 22, 6, 7), listOf(10, 3, 4, 5, 8),
        ), grid.rows)
        assertEquals(25, grid.start)
        assertEquals(listOf(25, 20, 17, 15, 13, 11, 9, 7, 6, 5, 4, 3), grid.path)
        val answer = listOf(
            LockDirection.UP, LockDirection.UP, LockDirection.RIGHT, LockDirection.RIGHT,
            LockDirection.DOWN, LockDirection.DOWN, LockDirection.DOWN, LockDirection.LEFT,
            LockDirection.DOWN, LockDirection.LEFT, LockDirection.LEFT,
        )
        assertEquals(answer, lock.answer)
        assertEquals(11, lock.answer.size)
        assertEquals(StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(channel, StoryPuzzleInput.Directions(answer)))
        assertEquals(StoryPuzzleResult.WRONG, StoryPuzzles.evaluate(channel, StoryPuzzleInput.Directions(answer.reversed())))
        assertEquals(3, grid.path.last())
        // Stop at the local minimum 3 rather than jumping to the unrelated global minimum 1.
        assertEquals(1, grid.rows.flatten().minOrNull())
        assertEquals(listOf(10, 23, 4), listOf(grid.rows[4][0], grid.rows[3][1], grid.rows[4][2]))
    }

    @Test fun reservoirRejectsThePreviousThreeMoveAnswerAndKeepsHistoricalFlows() {
        val channel = episode.chapters[2].puzzleSteps.single { it.id == "c3_channel" }
        val oldDirections = listOf(LockDirection.LEFT, LockDirection.RIGHT, LockDirection.RIGHT)
        assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(channel, StoryPuzzleInput.Directions(oldDirections)))
        assertEquals(StoryPuzzleResult.INVALID_INPUT, StoryPuzzles.evaluate(channel, StoryPuzzleInput.Sequence(listOf("L", "R", "R"))))
        assertEquals(StepKind.DEDUCE, channel.kind)
        assertTrue(channel.feedback("LEFT,RIGHT,RIGHT")!!.message.contains("이동"))
        val oldFlowFour = StoryCatalog.episodeFor(StoryCatalog.ITEM_FLOW_VERSION)
        val oldStep = oldFlowFour.chapters[2].puzzleSteps.single { it.id == "c3_channel" }
        assertEquals(listOf("L", "R", "R"), (oldStep.lock as StoryLockDefinition.Sequence).answer)
        assertEquals(StoryPuzzleResult.CORRECT, StoryPuzzles.evaluate(oldStep, StoryPuzzleInput.Sequence(listOf("L", "R", "R"))))
        assertEquals(listOf(CarControl.TURN_LEFT, CarControl.TURN_RIGHT, CarControl.TURN_RIGHT),
            (StoryCatalog.episodeFor(StoryCatalog.CASE_FLOW_VERSION).chapters[2].puzzleSteps.single { it.id == "c3_channel" }.lock as StoryLockDefinition.CarSequence).answer)
        val oldFarm = oldFlowFour.documents.single { it.id == "doc_farm" }
        val currentFarm = episode.documents.single { it.id == "doc_farm" }
        assertFalse(oldFarm.body.contains("25"))
        assertTrue(currentFarm.body.contains("가운데 25"))
        assertTrue(currentFarm.body.contains("곡식은 마지막이 아니다"))
        assertEquals("x3_farmbook", currentFarm.afterStep)
    }

    @Test fun completedReservoirStepKeepsItsIdentityAndRemainsSolved() {
        var state = atPuzzle(StoryEngine.start(episode, StoryCatalog.EXPLORE_FLOW_VERSION).copy(chapterIndex = 2))
        state = StoryEngine.examine(state, "x3_farmbook").copy(exploreFocus = "c3_channel")
        val solved = StoryEngine.submitPuzzle(state, answer(state.currentPuzzle!!.lock))
        assertEquals(StoryPuzzleResult.CORRECT, solved.result)
        assertTrue("c3_channel" in solved.state.puzzleProgressForCurrent!!.solvedSteps)
        assertEquals(StoryPuzzleResult.LOCKED, StoryEngine.submitPuzzle(solved.state, answer(state.currentPuzzle!!.lock)).result)
        assertEquals("c3_channel", episode.chapters[2].scene!!.hotspots.single { it.id == "x3_sluice" }.lockStep)
    }

    @Test fun locksCannotBeGuessedCheaply() {
        val steps = episode.chapters.flatMap { it.puzzleSteps }
        steps.forEach { step ->
            when (val lock = step.lock) {
                is StoryLockDefinition.Choice -> assertTrue("${step.id}: 보기 4개 이상", lock.options.size >= 4)
                is StoryLockDefinition.SymbolDial -> assertTrue("${step.id}: 처음 위치가 정답이면 안 됨", lock.answer.any { it != 0 })
                else -> Unit
            }
        }
        // 장소마다 첫 오답은 무료, 그다음부터 오답마다 각성도 +1.
        val start = start().copy(exploreFocus = "c1_plaque")
        val first = StoryEngine.submitPuzzle(start, StoryPuzzleInput.Choice("feng"))
        assertEquals(0, first.state.awakening)
        val second = StoryEngine.submitPuzzle(first.state, StoryPuzzleInput.Choice("tang"))
        assertEquals(1, second.state.awakening)
        assertTrue(second.note!!.contains("각성도 +1"))
    }
}
