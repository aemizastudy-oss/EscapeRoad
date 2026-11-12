package com.example.roadescape.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.example.roadescape.model.TourKey
import com.example.roadescape.model.TourMode
import com.example.roadescape.model.TourStartRecord
import com.example.roadescape.model.TourState
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface TourRepository {
    suspend fun load(): TourState
    /** Inserts once per session/chapter/mode; an existing start clock is never replaced. */
    suspend fun start(record: TourStartRecord): TourState
    suspend fun markDebugTimeElapsed(key: TourKey, debugBuild: Boolean): TourState
    suspend fun close()
}

enum class TourStorageFailure { CORRUPT, UNSUPPORTED_SCHEMA, INVALID_STATE, READ_WRITE }

class TourStorageException(val reason: TourStorageFailure, cause: Throwable? = null) : IOException(reason.name, cause)

object TourStateSerializer : Serializer<TourState> {
    private val json = Json { encodeDefaults = true }
    override val defaultValue = TourState()

    override suspend fun readFrom(input: InputStream): TourState {
        val text = input.readBytes().decodeToString()
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull
                ?: throw CorruptionException("관광 기록 형식 버전이 없습니다.")
            if (version != TourState.CURRENT_SCHEMA_VERSION) throw TourStorageException(TourStorageFailure.UNSUPPORTED_SCHEMA)
            if (!root.containsKey("records")) throw CorruptionException("관광 시작 기록 항목이 없습니다.")
            return json.decodeFromString<TourState>(text).also(::validateTourState)
        } catch (error: TourStorageException) { throw error
        } catch (error: SerializationException) { throw CorruptionException("관광 기록 JSON을 읽을 수 없습니다.", error)
        } catch (error: IllegalArgumentException) { throw CorruptionException("관광 기록 구조가 올바르지 않습니다.", error) }
    }

    override suspend fun writeTo(t: TourState, output: OutputStream) {
        validateTourState(t)
        output.write(json.encodeToString(TourState.serializer(), t).encodeToByteArray())
    }
}

private fun validateRecord(record: TourStartRecord) {
    if (record.sessionId.isBlank() || record.checkpointId.isBlank() ||
        record.startedAt.wallTimeMillis <= 0 || record.startedAt.elapsedRealtimeMillis < 0 ||
        record.startedAt.bootId.isBlank() || record.mode == TourMode.LIVE && record.debugTimeBypass
    ) throw TourStorageException(TourStorageFailure.INVALID_STATE)
}

private fun validateTourState(state: TourState) {
    if (state.schemaVersion != TourState.CURRENT_SCHEMA_VERSION) throw TourStorageException(TourStorageFailure.UNSUPPORTED_SCHEMA)
    state.records.forEach(::validateRecord)
    if (state.records.map { it.key }.toSet().size != state.records.size) throw TourStorageException(TourStorageFailure.INVALID_STATE)
}

/**
 * Owns only tour-state.json. Never reads, replaces, migrates or deletes game-session.json.
 * There is deliberately no clear/reset API: starting another game preserves previous tour starts.
 * The application owns one instance for this file.
 */
class JsonTourRepository(
    file: File,
    serializer: Serializer<TourState> = TourStateSerializer,
) : TourRepository {
    private val mutex = Mutex()
    private val job = SupervisorJob()
    private val store = DataStoreFactory.create(
        serializer = serializer,
        scope = CoroutineScope(Dispatchers.IO + job),
        produceFile = { file },
    )
    private var blockedFailure: TourStorageException? = null
    private var closed = false

    override suspend fun load(): TourState = mutex.withLock {
        check(!closed)
        guard {
            val loaded = store.data.first()
            validateTourState(loaded)
            blockedFailure = null
            loaded
        }
    }

    override suspend fun start(record: TourStartRecord): TourState = mutex.withLock {
        check(!closed)
        require(!record.debugTimeBypass) { "Use the explicit debug-only command for simulated time." }
        blockedFailure?.let { throw it }
        guard {
            validateRecord(record)
            store.updateData { previous ->
                validateTourState(previous)
                if (previous.find(record.key) != null) previous else previous.copy(records = previous.records + record)
            }
        }
    }

    override suspend fun markDebugTimeElapsed(key: TourKey, debugBuild: Boolean): TourState = mutex.withLock {
        check(!closed)
        require(debugBuild && key.mode == TourMode.MOCK) { "Tour time bypass is available only for debug mock records." }
        blockedFailure?.let { throw it }
        guard {
            store.updateData { previous ->
                validateTourState(previous)
                require(previous.find(key) != null) { "Start the current simulated tour first." }
                previous.copy(records = previous.records.map { if (it.key == key) it.copy(debugTimeBypass = true) else it })
            }
        }
    }

    override suspend fun close() = mutex.withLock {
        if (!closed) { closed = true; job.cancelAndJoin() }
    }

    private suspend fun <T> guard(action: suspend () -> T): T = try {
        action()
    } catch (error: CancellationException) { throw error
    } catch (error: Exception) {
        val failure = when (error) {
            is TourStorageException -> error
            is CorruptionException -> TourStorageException(TourStorageFailure.CORRUPT, error)
            else -> TourStorageException(TourStorageFailure.READ_WRITE, error)
        }
        blockedFailure = failure
        throw failure
    }

    companion object {
        fun forContext(context: Context) = JsonTourRepository(context.applicationContext.dataStoreFile("tour-state.json"))
    }
}
