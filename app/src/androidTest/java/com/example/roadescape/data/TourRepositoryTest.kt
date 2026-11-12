package com.example.roadescape.data

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.roadescape.game.TourPolicy
import com.example.roadescape.game.TourTimeResult
import com.example.roadescape.model.TourClock
import com.example.roadescape.model.TourMode
import com.example.roadescape.model.TourStartRecord
import com.example.roadescape.model.TourState
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TourRepositoryTest {
    private val directory = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "tour-repository-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val file = File(directory, "tour-state.json")
    private val repositories = mutableListOf<TourRepository>()
    private val clock = TourClock(1_800_000_000_000, 1_000_000, "boot-a")
    private fun record(session: String = "session-a", chapter: String = "cp_01", mode: TourMode = TourMode.LIVE) = TourStartRecord(session, chapter, mode, clock)
    private fun openRepository(serializer: Serializer<TourState> = TourStateSerializer) = JsonTourRepository(file, serializer).also { repositories += it }

    @After
    fun cleanup() = runBlocking { repositories.forEach { it.close() }; directory.deleteRecursively(); Unit }

    @Test
    fun firstStartPersistsImmediatelyAndDuplicateDoesNotRestartClock() = runBlocking {
        val repository = openRepository()
        assertTrue(repository.load().records.isEmpty())
        val original = record()
        repository.start(original)
        val duplicate = original.copy(startedAt = clock.copy(wallTimeMillis = clock.wallTimeMillis + 800_000))
        assertEquals(original, repository.start(duplicate).find(original.key))
        assertEquals(original, repository.load().find(original.key))
        repository.close()
        assertEquals(original, openRepository().load().find(original.key))
    }

    @Test
    fun restoredStartCountsTimeAcrossAppStopWithoutPersistingLocationOrSafety() = runBlocking {
        val repository = openRepository()
        val original = record()
        repository.start(original)
        repository.close()
        val restored = openRepository().load().find(original.key)!!
        val now = clock.copy(wallTimeMillis = clock.wallTimeMillis + 900_000, elapsedRealtimeMillis = clock.elapsedRealtimeMillis + 900_000)
        assertEquals(TourTimeResult.Valid(900_000), TourPolicy.elapsed(restored, now))
        val bytes = file.readText()
        assertFalse(bytes.contains("latitude"))
        assertFalse(bytes.contains("carUxAllowed"))
    }

    @Test
    fun sessionsChaptersAndModesRemainSeparateAndNewGameDoesNotDeleteOldStarts() = runBlocking {
        val repository = openRepository()
        val records = listOf(record(), record(mode = TourMode.MOCK), record(session = "session-b"), record(chapter = "cp_02"))
        records.forEach { repository.start(it) }
        val state = repository.markDebugTimeElapsed(records[1].key, debugBuild = true)
        assertEquals(4, state.records.size)
        assertTrue(state.find(records[1].key)!!.debugTimeBypass)
        assertFalse(state.find(records[0].key)!!.debugTimeBypass)
        assertEquals(records[2], state.find(records[2].key))
        assertTrue(runCatching { repository.markDebugTimeElapsed(records[0].key, debugBuild = true) }.isFailure)
        assertTrue(runCatching { repository.markDebugTimeElapsed(records[1].key, debugBuild = false) }.isFailure)
    }

    @Test
    fun failedWriteNeverShowsAStartedTourAndBlocksChangesUntilReload() = runBlocking {
        val fail = AtomicBoolean(false)
        val serializer = object : Serializer<TourState> by TourStateSerializer {
            override suspend fun writeTo(t: TourState, output: OutputStream) {
                if (fail.get()) throw IOException("Injected tour write failure")
                TourStateSerializer.writeTo(t, output)
            }
        }
        val repository = openRepository(serializer)
        val original = record()
        repository.start(original)
        val previous = file.readBytes()
        fail.set(true)
        expectFailure(TourStorageFailure.READ_WRITE) { repository.start(record(chapter = "cp_02")) }
        assertTrue(previous.contentEquals(file.readBytes()))
        fail.set(false)
        expectFailure(TourStorageFailure.READ_WRITE) { repository.start(record(chapter = "cp_03")) }
        assertEquals(listOf(original), repository.load().records)
        assertEquals(2, repository.start(record(chapter = "cp_02")).records.size)
    }

    @Test
    fun corruptAndFutureTourStoresArePreservedWithoutTouchingGameStore() = runBlocking {
        val gameFile = File(directory, "game-session.json").apply { writeText("unrelated existing game bytes") }
        for ((text, failure) in listOf("{ broken" to TourStorageFailure.CORRUPT, """{"schemaVersion":99,"records":[]}""" to TourStorageFailure.UNSUPPORTED_SCHEMA)) {
            file.writeText(text)
            val repository = openRepository()
            expectFailure(failure) { repository.load() }
            expectFailure(failure) { repository.start(record()) }
            assertEquals(text, file.readText())
            assertEquals("unrelated existing game bytes", gameFile.readText())
            repository.close()
        }
    }

    private suspend fun expectFailure(reason: TourStorageFailure, action: suspend () -> Unit) {
        val failure = try { action(); null } catch (error: TourStorageException) { error }
        assertEquals(reason, failure?.reason)
    }
}
