package com.example.roadescape.data

import androidx.datastore.core.Serializer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.roadescape.model.AcquiredClue
import com.example.roadescape.model.AcquisitionType
import com.example.roadescape.model.CheckpointProgress
import com.example.roadescape.model.CheckpointStatus
import com.example.roadescape.model.GameSession
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameRepositoryTest {
    private val directory = File(
        ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
        "game-repository-test-${UUID.randomUUID()}",
    ).apply { mkdirs() }
    private val file = File(directory, "progress.json")
    private val openRepositories = mutableListOf<GameRepository>()

    private fun repository(
        serializer: Serializer<GameSnapshot> = GameSnapshotSerializer,
        validator: (GameSession) -> Unit = {},
    ): JsonGameRepository = JsonGameRepository(file, validator, serializer)
        .also { openRepositories += it }

    private fun session(id: String = "saved-session") = GameSession(
        sessionId = id,
        packageId = "demo",
        contentVersion = 1,
        progress = listOf(
            CheckpointProgress(
                checkpointId = "cp_01",
                status = CheckpointStatus.SKIPPED,
                arrivedAt = 12345L,
                wrongAttempts = 2,
                revealedHintCount = 2,
                answerRevealed = true,
            ),
            CheckpointProgress(checkpointId = "cp_02"),
            CheckpointProgress(checkpointId = "cp_03"),
        ),
        acquiredClues = listOf(AcquiredClue("clue_01", "cp_01", AcquisitionType.SKIPPED)),
    )

    @After
    fun cleanUp() = runBlocking {
        openRepositories.forEach { it.close() }
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun firstLoadIsEmptyAndAtomicSnapshotSurvivesStoreRestart() = runBlocking {
        val original = repository()
        assertNull(original.load())
        val progress = session()
        assertEquals(progress, original.save(progress))
        original.close()
        val reopened = repository()
        assertEquals(progress, reopened.load())
        assertEquals(1, reopened.load()!!.acquiredClues.size)
    }

    @Test
    fun failedWriteKeepsPriorSnapshotAndBlocksFurtherChangesUntilReload() = runBlocking {
        val failWrites = AtomicBoolean(false)
        val failingSerializer = object : Serializer<GameSnapshot> by GameSnapshotSerializer {
            override suspend fun writeTo(t: GameSnapshot, output: OutputStream) {
                if (failWrites.get()) throw IOException("Injected disk write failure")
                GameSnapshotSerializer.writeTo(t, output)
            }
        }
        val repository = repository(failingSerializer)
        val original = session("before-failure")
        repository.save(original)
        val originalBytes = file.readBytes()
        failWrites.set(true)
        expectFailure(StorageFailure.READ_WRITE) { repository.save(session("failed-save")) }
        assertTrue(originalBytes.contentEquals(file.readBytes()))
        failWrites.set(false)
        expectFailure(StorageFailure.READ_WRITE) { repository.save(session("blocked")) }
        assertEquals(original, repository.load())
        assertEquals(session("retry"), repository.save(session("retry")))
    }

    @Test
    fun failedResetRestoresPreviousSnapshotInsteadOfBecomingAnEmptyGame() = runBlocking {
        val failWrites = AtomicBoolean(false)
        val failingSerializer = object : Serializer<GameSnapshot> by GameSnapshotSerializer {
            override suspend fun writeTo(t: GameSnapshot, output: OutputStream) {
                if (failWrites.get()) throw IOException("Injected reset write failure")
                GameSnapshotSerializer.writeTo(t, output)
            }
        }
        val repository = repository(failingSerializer)
        val original = session("before-reset")
        repository.save(original)
        failWrites.set(true)
        expectFailure(StorageFailure.READ_WRITE) { repository.reset(session("failed-reset")) }
        assertEquals(original, repository.load())
        assertTrue(directory.listFiles()!!.any { it.extension == "bak" })
    }

    @Test
    fun interruptedResetWithOnlyBackupRecoversLastCompletedSnapshot() = runBlocking {
        val previous = repository()
        val original = session("before-interruption")
        previous.save(original)
        previous.close()
        assertTrue(file.renameTo(File(directory, "${file.name}.interrupted.bak")))
        val reopened = repository()
        assertEquals(original, reopened.load())
        assertTrue(file.exists())
    }

    @Test
    fun corruptJsonIsPreservedAndOnlyExplicitResetCreatesBackup() = runBlocking {
        val damaged = "{ interrupted write".encodeToByteArray()
        file.writeBytes(damaged)
        val repository = repository()
        expectFailure(StorageFailure.CORRUPT) { repository.load() }
        expectFailure(StorageFailure.CORRUPT) { repository.save(session()) }
        assertTrue(damaged.contentEquals(file.readBytes()))
        assertTrue(directory.listFiles()!!.none { it.extension == "bak" })

        val fresh = session("explicit-reset")
        assertEquals(fresh, repository.reset(fresh))
        assertEquals(fresh, repository.load())
        val backup = directory.listFiles()!!.single { it.extension == "bak" }
        assertTrue(damaged.contentEquals(backup.readBytes()))
    }

    @Test
    fun futureEnvelopeAndSessionVersionsAreNotOverwritten() = runBlocking {
        val futureVersions = listOf(
            """{"schemaVersion":99,"session":null}""",
            Json { encodeDefaults = true }.encodeToString(
                GameSnapshot.serializer(), GameSnapshot(session = session().copy(schemaVersion = 99)),
            ),
        )
        for (text in futureVersions) {
            file.writeText(text)
            val repository = repository()
            expectFailure(StorageFailure.UNSUPPORTED_SCHEMA) { repository.load() }
            expectFailure(StorageFailure.UNSUPPORTED_SCHEMA) { repository.save(session()) }
            assertEquals(text, file.readText())
            repository.close()
        }
    }

    @Test
    fun semanticProgressErrorDoesNotEraseSavedData() = runBlocking {
        val normal = repository()
        normal.save(session())
        normal.close()
        val savedBytes = file.readBytes()
        val validating = repository(validator = { throw IllegalArgumentException("Invalid clue reference") })
        expectFailure(StorageFailure.INVALID_PROGRESS) { validating.load() }
        expectFailure(StorageFailure.INVALID_PROGRESS) { validating.save(session("replacement")) }
        assertTrue(savedBytes.contentEquals(file.readBytes()))
    }

    @Test
    fun packagedContentHasThreeValidatedCheckpoints() = runBlocking {
        val content = PackageRepository(ApplicationProvider.getApplicationContext()).load()
        assertEquals(3, content.checkpoints.size)
        assertEquals(listOf("cp_01", "cp_02", "cp_03"), content.checkpoints.map { it.id })
        assertEquals(3, content.puzzles.size)
        assertEquals(3, content.clues.size)
    }

    private suspend fun expectFailure(reason: StorageFailure, block: suspend () -> Unit) {
        val error = try {
            block()
            null
        } catch (error: GameStorageException) {
            error
        }
        assertNotNull("Expected $reason failure", error)
        assertEquals(reason, error!!.reason)
    }
}
