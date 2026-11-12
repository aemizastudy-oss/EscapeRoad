package com.example.roadescape.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.example.roadescape.model.GameSession
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface GameRepository {
    suspend fun load(): GameSession?
    suspend fun save(session: GameSession): GameSession

    /** Call only after the user explicitly confirms starting a new game. */
    suspend fun reset(session: GameSession): GameSession
    suspend fun close()
}

enum class StorageFailure {
    CORRUPT, UNSUPPORTED_SCHEMA, CONTENT_MISMATCH, INVALID_PROGRESS, READ_WRITE,
}

class GameStorageException(
    val reason: StorageFailure,
    cause: Throwable? = null,
) : IOException(reason.name, cause)

@Serializable
data class GameSnapshot(
    val schemaVersion: Int = 1,
    val session: GameSession? = null,
)

/** No corruption handler is installed: an unreadable save must never become an empty save. */
object GameSnapshotSerializer : Serializer<GameSnapshot> {
    private val json = Json { encodeDefaults = true }
    override val defaultValue = GameSnapshot()

    override suspend fun readFrom(input: InputStream): GameSnapshot {
        val text = input.readBytes().decodeToString()
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull
                ?: throw CorruptionException("저장 형식 버전이 없습니다.")
            if (version != 1) {
                throw GameStorageException(StorageFailure.UNSUPPORTED_SCHEMA)
            }
            if (!root.containsKey("session")) {
                throw CorruptionException("저장된 세션 항목이 없습니다.")
            }
            val sessionElement = root["session"]
            if (sessionElement != null && sessionElement.toString() != "null") {
                val sessionVersion = sessionElement.jsonObject["schemaVersion"]
                    ?.jsonPrimitive?.intOrNull
                    ?: throw CorruptionException("저장된 세션 버전이 없습니다.")
                if (sessionVersion != 1) {
                    throw GameStorageException(StorageFailure.UNSUPPORTED_SCHEMA)
                }
            }
            return json.decodeFromString<GameSnapshot>(text)
        } catch (error: GameStorageException) {
            throw error
        } catch (error: SerializationException) {
            throw CorruptionException("저장된 게임 JSON을 읽을 수 없습니다.", error)
        } catch (error: IllegalArgumentException) {
            throw CorruptionException("저장된 게임 구조가 올바르지 않습니다.", error)
        }
    }

    override suspend fun writeTo(t: GameSnapshot, output: OutputStream) {
        output.write(json.encodeToString(GameSnapshot.serializer(), t).encodeToByteArray())
    }
}

/**
 * The application owns one instance. Reads, updates and explicit resets are serialized.
 * A failed write blocks further changes until load() succeeds or an explicit reset completes.
 */
class JsonGameRepository(
    private val file: File,
    private val validator: (GameSession) -> Unit = {},
    private val serializer: Serializer<GameSnapshot> = GameSnapshotSerializer,
) : GameRepository {
    private val mutex = Mutex()
    private var storeJob = SupervisorJob()
    private var store = createStore()
    private var blockedFailure: GameStorageException? = null
    private var pendingRecovery: File? = null
    private var closed = false

    private fun createStore(): DataStore<GameSnapshot> = DataStoreFactory.create(
        serializer = serializer,
        scope = CoroutineScope(Dispatchers.IO + storeJob),
        produceFile = { file },
    )

    override suspend fun load(): GameSession? = mutex.withLock {
        check(!closed) { "GameRepository is closed" }
        guardStorage {
            val recovery = pendingRecovery ?: withContext(Dispatchers.IO) {
                // A process can stop between backing up an explicit reset and committing its replacement.
                if (file.exists()) null else file.parentFile?.listFiles()
                    ?.filter { it.name.startsWith("${file.name}.") && it.name.endsWith(".bak") }
                    ?.maxByOrNull { it.lastModified() }
            }
            if (recovery != null) restore(recovery)
            val snapshot = store.data.first()
            validate(snapshot.session)
            blockedFailure = null
            snapshot.session
        }
    }

    override suspend fun save(session: GameSession): GameSession = mutex.withLock {
        check(!closed) { "GameRepository is closed" }
        blockedFailure?.let { throw it }
        guardStorage {
            validate(session)
            // Completion, clue acquisition, hints and attempts form one atomic snapshot.
            store.updateData { previous ->
                validate(previous.session)
                GameSnapshot(session = session)
            }.session ?: error("An update must retain its session")
        }
    }

    override suspend fun reset(session: GameSession): GameSession = mutex.withLock {
        check(!closed) { "GameRepository is closed" }
        guardStorage {
            validate(session)
            var backup: File? = pendingRecovery
            storeJob.cancelAndJoin()
            try {
                withContext(Dispatchers.IO) {
                    if (file.exists()) {
                        // Preserve even corrupt or future-version bytes before a user-approved reset.
                        val archived = File(file.parentFile, "${file.name}.${System.currentTimeMillis()}.bak")
                        if (!file.renameTo(archived)) {
                            throw IOException("기존 저장 파일을 보존할 수 없어 초기화를 중단했습니다.")
                        }
                        backup = archived
                    }
                }
            } finally {
                // Also restore a usable store when backup creation fails; retry must not hang.
                storeJob = SupervisorJob()
                store = createStore()
            }
            try {
                val saved = store.updateData { GameSnapshot(session = session) }.session
                    ?: error("A reset must retain its session")
                pendingRecovery = null
                blockedFailure = null
                saved
            } catch (error: Exception) {
                if (backup != null) {
                    pendingRecovery = backup
                    withContext(NonCancellable) {
                        try {
                            restore(backup!!)
                        } catch (restoreError: Exception) {
                            error.addSuppressed(restoreError)
                        }
                    }
                }
                throw error
            }
        }
    }

    override suspend fun close() = mutex.withLock {
        if (!closed) {
            closed = true
            storeJob.cancelAndJoin()
        }
    }

    private fun validate(session: GameSession?) {
        if (session == null) return
        if (session.schemaVersion != 1) {
            throw GameStorageException(StorageFailure.UNSUPPORTED_SCHEMA)
        }
        try {
            validator(session)
        } catch (error: GameStorageException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw GameStorageException(StorageFailure.INVALID_PROGRESS, error)
        }
    }

    private suspend fun restore(backup: File) {
        pendingRecovery = backup
        storeJob.cancelAndJoin()
        try {
            withContext(Dispatchers.IO) {
                val recoveryFile = File(file.parentFile, "${file.name}.recovery")
                backup.inputStream().use { input ->
                    FileOutputStream(recoveryFile).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                Files.move(
                    recoveryFile.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            }
            pendingRecovery = null
        } finally {
            storeJob = SupervisorJob()
            store = createStore()
        }
    }

    private suspend fun <T> guardStorage(block: suspend () -> T): T {
        try {
            return block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failure = when (error) {
                is GameStorageException -> error
                is CorruptionException -> GameStorageException(StorageFailure.CORRUPT, error)
                else -> GameStorageException(StorageFailure.READ_WRITE, error)
            }
            blockedFailure = failure
            throw failure
        }
    }

    companion object {
        fun forContext(
            context: Context,
            validator: (GameSession) -> Unit = {},
        ): JsonGameRepository = JsonGameRepository(
            context.applicationContext.dataStoreFile("game-session.json"),
            validator,
        )
    }
}
