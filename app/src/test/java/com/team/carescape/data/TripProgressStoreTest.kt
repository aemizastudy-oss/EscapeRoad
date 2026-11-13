package com.team.carescape.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.team.carescape.game.StoryPhase
import com.team.carescape.game.StoryCatalog
import com.team.carescape.game.StoryEngine
import com.team.carescape.game.StoryPuzzleInput
import com.team.carescape.game.StoryPuzzleProgress
import com.team.carescape.game.StoryState
import com.team.carescape.game.TourCatalog
import com.team.carescape.model.TourClock
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourStartRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripProgressStoreTest {
    private val clock = TourClock(1_800_000_000_000L, 1_000_000L, "boot-one")
    private fun initial(mode: TourMode = TourMode.MOCK, session: String = "session-one") = StoredTrip(
        mode = mode,
        sessionId = session,
        chapterIndex = 0,
        phase = StoryPhase.EN_ROUTE,
        startedAtEpochMillis = clock.wallTimeMillis,
        showMap = true,
    )
    private fun exploring(mode: TourMode = TourMode.MOCK, session: String = "session-one") = initial(mode, session).copy(
        phase = StoryPhase.EXPLORING,
        showMap = false,
        tourRecords = listOf(TourStartRecord(session, "suwon_01", mode, clock)),
    )

    private fun nativePuzzle(): StoredTrip {
        val base = exploring().copy(flowVersion = StoryCatalog.CURRENT_FLOW_VERSION)
        return base.copy(phase = StoryPhase.PUZZLE,
            puzzleProgress = listOf(StoryPuzzleProgress("suwon_01")))
    }

    private fun solvedNativePuzzle(): StoredTrip {
        val trip = nativePuzzle()
        val result = StoryEngine.submitPuzzle(StoryState(
            StoryCatalog.episodeFor(2), phase = StoryPhase.PUZZLE,
            flowVersion = 2, puzzleProgress = trip.puzzleProgress,
        ), StoryPuzzleInput.Numeric("0427")).state
        return trip.copy(phase = result.phase, items = result.items, clues = result.clues,
            puzzleProgress = result.puzzleProgress)
    }

    private fun legacyAt(chapterIndex: Int, phase: StoryPhase): StoredTrip {
        val episode = StoryCatalog.episodeFor(1)
        val completed = StoryCatalog.completedCount(chapterIndex, phase, episode.chapters.size)
        val starts = if (phase == StoryPhase.EXPLORING) chapterIndex + 1 else completed
        return initial().copy(chapterIndex = chapterIndex, phase = phase, showMap = phase == StoryPhase.EN_ROUTE,
            items = episode.chapters.take(completed).mapNotNull { it.item },
            clues = episode.chapters.take(completed).mapNotNull { it.clue },
            tourRecords = TourCatalog.checkpoints.take(starts).map {
                TourStartRecord("session-one", it.id, TourMode.MOCK, clock)
            })
    }

    @Test fun bothModesAndOriginalTourClockSurviveWholeJsonRoundTripWithoutCoordinates() = runBlocking {
        val state = TripStoreState(trips = listOf(exploring(), exploring(TourMode.LIVE, "session-live")))
        val output = ByteArrayOutputStream()
        TripStateSerializer.writeTo(state, output)
        assertEquals(state, TripStateSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
        val text = output.toString(Charsets.UTF_8.name())
        assertFalse(text.contains("latitude"))
        assertFalse(text.contains("longitude"))
        assertFalse(text.contains("accuracyMeters"))
    }

    @Test fun unknownSchemaMalformedJsonAndUnknownPhaseAreNotEmptyDefaults() = runBlocking {
        val newer = """{"schemaVersion":9,"trips":[]}"""
        val failure = runCatching { TripStateSerializer.readFrom(newer.byteInputStream()) }.exceptionOrNull()
        assertEquals(TripStorageFailure.UNSUPPORTED_SCHEMA, (failure as TripStorageException).reason)
        assertTrue(runCatching { TripStateSerializer.readFrom("not-json".byteInputStream()) }.exceptionOrNull() is CorruptionException)
        assertTrue(runCatching { TripStateSerializer.readFrom("{}".byteInputStream()) }.exceptionOrNull() is CorruptionException)
        val output = ByteArrayOutputStream()
        TripStateSerializer.writeTo(TripStoreState(trips = listOf(initial())), output)
        val badPhase = output.toString(Charsets.UTF_8.name()).replace("EN_ROUTE", "NO_SUCH_PHASE")
        assertTrue(runCatching { TripStateSerializer.readFrom(badPhase.byteInputStream()) }.exceptionOrNull() is CorruptionException)
    }

    @Test fun invalidScopeProgressDuplicatesAndLiveBypassAreRejected() {
        val trip = exploring()
        listOf(
            trip.copy(episodeId = "other"),
            trip.copy(chapterIndex = 5),
            trip.copy(sessionId = "wrong-session"),
            trip.copy(tourRecords = trip.tourRecords + trip.tourRecords),
            trip.copy(tourRecords = listOf(trip.tourRecords.single().copy(checkpointId = "suwon_05"))),
            trip.copy(items = listOf("unearned")),
            trip.copy(phase = StoryPhase.ENDED),
            exploring(TourMode.LIVE).let { it.copy(tourRecords = listOf(it.tourRecords.single().copy(debugTimeBypass = true))) },
        ).forEach { invalid ->
            val failure = runCatching { validateStoredTrip(invalid) }.exceptionOrNull()
            assertEquals(TripStorageFailure.INVALID_STATE, (failure as TripStorageException).reason)
        }
    }

    @Test fun duplicateStartCannotResetClockAndMockBypassOnlyMovesForward() {
        val trip = exploring()
        val original = upsertTrip(TripStoreState(), trip)
        assertEquals(original, upsertTrip(original, trip))
        val changedClock = trip.copy(tourRecords = listOf(trip.tourRecords.single().copy(startedAt = clock.copy(wallTimeMillis = clock.wallTimeMillis + 1))))
        assertTrue(runCatching { upsertTrip(original, changedClock) }.isFailure)
        assertTrue(runCatching { upsertTrip(original, trip.copy(tourRecords = emptyList())) }.isFailure)
        val bypassed = trip.copy(tourRecords = listOf(trip.tourRecords.single().copy(debugTimeBypass = true)))
        val simulated = upsertTrip(original, bypassed)
        assertEquals(clock, simulated.trips.single().tourRecords.single().startedAt)
        assertTrue(simulated.trips.single().tourRecords.single().debugTimeBypass)
        assertTrue(runCatching { upsertTrip(simulated, trip) }.isFailure)
    }

    @Test fun sameSessionStringStillCannotMixModesAndNewTripPreservesOtherMode() {
        val live = exploring(TourMode.LIVE)
        val mock = exploring()
        val both = upsertTrip(upsertTrip(TripStoreState(), live), mock)
        assertEquals(2, both.trips.size)
        val updated = upsertTrip(both, initial(session = "new-mock-session"))
        assertEquals(live, updated.trips.single { it.mode == TourMode.LIVE })
        assertEquals("new-mock-session", updated.trips.single { it.mode == TourMode.MOCK }.sessionId)
        val mixed = mock.copy(tourRecords = live.tourRecords)
        assertTrue(runCatching { upsertTrip(both, mixed) }.isFailure)
    }

    @Test fun dataStoreReopensDurableProgressAndResetPreservesOtherMode() = runBlocking {
        val directory = Files.createTempDirectory("trip-roundtrip-").toFile()
        val file = File(directory, "trip-progress.json")
        var job = SupervisorJob()
        try {
            var store = TripProgressStore(DataStoreFactory.create(TripStateSerializer, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            store.save(exploring())
            store.save(exploring(TourMode.LIVE, "live-session"))
            job.cancelAndJoin()
            job = SupervisorJob()
            store = TripProgressStore(DataStoreFactory.create(TripStateSerializer, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            assertEquals(2, store.load().trips.size)
            assertEquals(clock, store.load().trips.single { it.mode == TourMode.MOCK }.tourRecords.single().startedAt)
            store.remove(TourMode.MOCK)
            assertEquals(listOf(exploring(TourMode.LIVE, "live-session")), store.load().trips)
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    @Test fun failedAtomicWriteKeepsPreviouslyCommittedBytesAndProgressThenCanRetry() = runBlocking {
        val directory = Files.createTempDirectory("trip-failure-").toFile()
        val file = File(directory, "trip-progress.json")
        val job = SupervisorJob()
        val failWrites = AtomicBoolean(false)
        val serializer = object : Serializer<TripStoreState> by TripStateSerializer {
            override suspend fun writeTo(t: TripStoreState, output: OutputStream) {
                if (failWrites.get()) {
                    output.write("partial-write".toByteArray())
                    throw IOException("test disk failure")
                }
                TripStateSerializer.writeTo(t, output)
            }
        }
        try {
            val store = TripProgressStore(DataStoreFactory.create(serializer, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            val saved = store.save(initial())
            val originalBytes = file.readBytes()
            failWrites.set(true)
            val failure = runCatching { store.save(exploring()) }.exceptionOrNull()
            assertEquals(TripStorageFailure.READ_WRITE, (failure as TripStorageException).reason)
            assertEquals(listOf(saved), store.load().trips)
            assertTrue(originalBytes.contentEquals(file.readBytes()))
            failWrites.set(false)
            assertEquals(exploring(), store.save(exploring()))
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    @Test fun corruptAndNewerFilesSurviveLoadAndSaveAttemptsByteForByte() = runBlocking {
        listOf("{broken", """{"schemaVersion":99,"trips":[]}""").forEach { source ->
            val directory = Files.createTempDirectory("trip-corrupt-").toFile()
            val file = File(directory, "trip-progress.json").apply { writeText(source) }
            val originalBytes = file.readBytes()
            val job = SupervisorJob()
            try {
                val store = TripProgressStore(DataStoreFactory.create(TripStateSerializer, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
                assertTrue(runCatching { store.load() }.exceptionOrNull() is TripStorageException)
                assertTrue(runCatching { store.save(initial()) }.exceptionOrNull() is TripStorageException)
                assertTrue(originalBytes.contentEquals(file.readBytes()))
            } finally {
                job.cancelAndJoin()
                directory.deleteRecursively()
            }
        }
    }

    @Test fun schemaOneExplorationForksDeterministicallyAndArchivesTheOriginalWithoutFakeSolves() = runTest {
        val old = legacyAt(2, StoryPhase.EXPLORING)
        val source = TripStoreState(schemaVersion = 1, trips = listOf(old))
        val encoded = Json { encodeDefaults = true }.encodeToString(TripStoreState.serializer(), source)
        val migrated = TripStateSerializer.readFrom(encoded.byteInputStream())
        assertEquals(2, migrated.schemaVersion)
        assertEquals(listOf(old), migrated.archivedTrips)
        val active = migrated.trips.single()
        assertEquals(2, active.flowVersion)
        assertEquals(2, active.legacyCompletedCount)
        assertEquals(StoryPhase.ARRIVED, active.phase)
        assertEquals(old.sessionId, active.legacySourceSessionId)
        assertTrue(active.sessionId != old.sessionId)
        assertEquals(old.startedAtEpochMillis, active.startedAtEpochMillis)
        assertEquals(old.items, active.items)
        assertEquals(old.clues, active.clues)
        assertTrue(active.tourRecords.isEmpty())
        assertTrue(active.puzzleProgress.isEmpty())
        assertEquals(migrated, TripStateSerializer.readFrom(encoded.byteInputStream()))
    }

    @Test fun completedLegacyChaptersAndEndedTripRemainCompletedWithOriginalReceiptsOnlyInArchive() {
        listOf(legacyAt(1, StoryPhase.REVEALED), legacyAt(3, StoryPhase.FINAL_REVEAL),
            legacyAt(4, StoryPhase.ENDED)).forEach { old ->
            val migrated = migrateLegacyTripState(TripStoreState(schemaVersion = 1, trips = listOf(old)))
            val active = migrated.trips.single()
            assertEquals(old.phase, active.phase)
            assertEquals(old.items, active.items)
            assertEquals(old.clues, active.clues)
            assertEquals(StoryCatalog.completedCount(old.chapterIndex, old.phase, 5), active.legacyCompletedCount)
            assertEquals(old, migrated.archivedTrips.single())
            assertTrue(active.tourRecords.isEmpty())
            assertTrue(active.puzzleProgress.isEmpty())
            validateTripState(migrated)
        }
    }

    @Test fun migratedCurrentTourMustGetANewImmutableStartInTheForkedSession() {
        val migrated = migrateLegacyTripState(TripStoreState(schemaVersion = 1,
            trips = listOf(legacyAt(1, StoryPhase.EXPLORING))))
        val active = migrated.trips.single()
        val started = active.copy(phase = StoryPhase.EXPLORING,
            tourRecords = listOf(TourStartRecord(active.sessionId, "suwon_02", active.mode,
                clock.copy(wallTimeMillis = clock.wallTimeMillis + 1_000))))
        val saved = upsertTrip(migrated, started)
        assertEquals(migrated.archivedTrips, saved.archivedTrips)
        assertTrue(started.tourRecords.single().startedAt != migrated.archivedTrips.single().tourRecords.last().startedAt)
        assertTrue(runCatching { upsertTrip(saved, started.copy(tourRecords = emptyList())) }.isFailure)
        assertTrue(runCatching { upsertTrip(saved, started.copy(legacyCompletedCount = 2)) }.isFailure)
        assertTrue(runCatching { upsertTrip(saved, started.copy(legacySourceSessionId = null)) }.isFailure)
    }

    @Test fun newCompletionsRequireSolvedReceiptsAndTourStartsAndCannotRollback() {
        val puzzle = nativePuzzle()
        val solved = solvedNativePuzzle()
        val first = upsertTrip(TripStoreState(), puzzle)
        val saved = upsertTrip(first, solved)
        assertEquals(saved, upsertTrip(saved, solved))
        listOf(solved.copy(puzzleProgress = emptyList()),
            solved.copy(puzzleProgress = listOf(StoryPuzzleProgress("suwon_01"))),
            solved.copy(tourRecords = emptyList()),
            solved.copy(legacyCompletedCount = 1),
            solved.copy(legacySourceSessionId = "invented"),
        ).forEach { assertTrue(runCatching { validateStoredTrip(it) }.isFailure) }
        assertTrue(runCatching { upsertTrip(saved, puzzle) }.isFailure)
        val hinted = puzzle.copy(puzzleProgress = listOf(StoryPuzzleProgress("suwon_01", 2, 1)))
        val history = upsertTrip(first, hinted)
        assertTrue(runCatching { upsertTrip(history, puzzle) }.isFailure)
    }

    @Test fun malformedPuzzleReceiptsAndUnsupportedFlowAreNotAcceptedAsLegacyWaivers() {
        val puzzle = nativePuzzle()
        listOf(
            puzzle.copy(puzzleProgress = puzzle.puzzleProgress + puzzle.puzzleProgress),
            puzzle.copy(puzzleProgress = listOf(StoryPuzzleProgress("suwon_05"))),
            puzzle.copy(puzzleProgress = listOf(StoryPuzzleProgress("suwon_01", wrongAttempts = -1))),
            puzzle.copy(puzzleProgress = listOf(StoryPuzzleProgress("suwon_01", hintsShown = 100))),
            puzzle.copy(puzzleProgress = listOf(StoryPuzzleProgress("suwon_01", solved = true))),
            puzzle.copy(legacyCompletedCount = 1),
            puzzle.copy(flowVersion = 99),
        ).forEach { assertTrue(runCatching { validateStoredTrip(it) }.isFailure) }
    }

    @Test fun loadAdapterLeavesSchemaOneBytesUntouchedUntilTheNextExplicitAtomicWrite() = runTest {
        val directory = Files.createTempDirectory("trip-migration-").toFile()
        val file = File(directory, "trip-progress.json")
        val original = TripStoreState(schemaVersion = 1, trips = listOf(legacyAt(1, StoryPhase.EXPLORING)))
        val bytes = Json { encodeDefaults = true }.encodeToString(TripStoreState.serializer(), original).encodeToByteArray()
        file.writeBytes(bytes)
        val job = SupervisorJob()
        try {
            val store = TripProgressStore(DataStoreFactory.create(TripStateSerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            val loaded = store.load()
            assertTrue(bytes.contentEquals(file.readBytes()))
            assertEquals(original.trips, loaded.archivedTrips)
            val active = loaded.trips.single()
            val started = active.copy(phase = StoryPhase.EXPLORING,
                tourRecords = listOf(TourStartRecord(active.sessionId, "suwon_02", active.mode,
                    clock.copy(wallTimeMillis = clock.wallTimeMillis + 1_000))))
            store.save(started)
            assertTrue(!bytes.contentEquals(file.readBytes()))
            val reopened = file.inputStream().use { TripStateSerializer.readFrom(it) }
            assertEquals(loaded.copy(trips = listOf(started)), reopened)
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    @Test fun bothModesMigrateWithDistinctSessionsAndArchiveSurvivesModeReset() = runTest {
        val old = exploring()
        val live = exploring(TourMode.LIVE, "live-original")
        val migrated = migrateLegacyTripState(TripStoreState(schemaVersion = 1, trips = listOf(old, live)))
        assertEquals(2, migrated.trips.map { it.sessionId }.distinct().size)
        assertEquals(listOf(old, live), migrated.archivedTrips)
        val mock = migrated.trips.single { it.mode == TourMode.MOCK }
        val invalid = mock.copy(tourRecords = listOf(TourStartRecord(mock.sessionId,
            "suwon_01", TourMode.LIVE, clock)), phase = StoryPhase.EXPLORING)
        assertTrue(runCatching { upsertTrip(migrated, invalid) }.isFailure)
        val replacement = nativePuzzle().copy(sessionId = "new-mock",
            tourRecords = listOf(TourStartRecord("new-mock", "suwon_01", TourMode.MOCK, clock)))
        val updated = upsertTrip(migrated, replacement)
        assertEquals(migrated.archivedTrips, updated.archivedTrips)
        assertEquals(migrated.trips.single { it.mode == TourMode.LIVE }, updated.trips.single { it.mode == TourMode.LIVE })
    }

    @Test fun legacyWaiverCannotExpandToHideANewUnsolvedChapter() {
        val migrated = migrateLegacyTripState(TripStoreState(schemaVersion = 1,
            trips = listOf(legacyAt(1, StoryPhase.EXPLORING))))
        val episode = StoryCatalog.episodeFor(2)
        val forged = migrated.trips.single().copy(phase = StoryPhase.REVEALED,
            legacyCompletedCount = 2,
            items = episode.chapters.take(2).mapNotNull { it.item },
            clues = episode.chapters.take(2).mapNotNull { it.clue })
        assertTrue(runCatching { validateTripState(migrated.copy(trips = listOf(forged))) }.isFailure)
        assertTrue(runCatching { validateStoredTrip(migrated.trips.single()) }.isFailure)
    }

    @Test fun schemaTwoArchivesAndNativeHintAttemptReceiptsRoundTripTogether() = runTest {
        val migrated = migrateLegacyTripState(TripStoreState(schemaVersion = 1,
            trips = listOf(legacyAt(1, StoryPhase.EXPLORING))))
        val active = migrated.trips.single()
        val draft = active.copy(phase = StoryPhase.PUZZLE,
            tourRecords = listOf(TourStartRecord(active.sessionId, "suwon_02", active.mode, clock)),
            puzzleProgress = listOf(StoryPuzzleProgress("suwon_02", wrongAttempts = 2, hintsShown = 1)))
        val saved = upsertTrip(migrated, draft)
        val output = ByteArrayOutputStream()
        TripStateSerializer.writeTo(saved, output)
        assertEquals(saved, TripStateSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
        assertEquals(migrated.archivedTrips, saved.archivedTrips)
    }
}
