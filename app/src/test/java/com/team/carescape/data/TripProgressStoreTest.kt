package com.team.carescape.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.team.carescape.game.StoryPhase
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
}
