package com.team.carescape.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.team.carescape.game.Siyeok
import com.team.carescape.game.StoryPhase
import com.team.carescape.game.TourCatalog
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourStartRecord
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.CharacterCodingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Only durable story progress and tour start clocks. Positions and safety observations are never stored. */
@Serializable
data class StoredTrip(
    val mode: TourMode,
    val sessionId: String,
    val episodeId: String = "siyeok",
    val chapterIndex: Int,
    val phase: StoryPhase,
    val items: List<String> = emptyList(),
    val clues: List<String> = emptyList(),
    val startedAtEpochMillis: Long,
    val showMap: Boolean = false,
    val tourRecords: List<TourStartRecord> = emptyList(),
)

@Serializable
data class TripStoreState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val trips: List<StoredTrip> = emptyList(),
) {
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

enum class TripStorageFailure { CORRUPT, UNSUPPORTED_SCHEMA, INVALID_STATE, READ_WRITE }

class TripStorageException(val reason: TripStorageFailure, cause: Throwable? = null) : IOException(
    when (reason) {
        TripStorageFailure.CORRUPT -> "저장된 여행 기록을 읽을 수 없습니다. 원본은 보존됩니다."
        TripStorageFailure.UNSUPPORTED_SCHEMA -> "지원하지 않는 여행 기록 버전입니다. 원본은 보존됩니다."
        TripStorageFailure.INVALID_STATE -> "여행 기록의 내용이 올바르지 않습니다."
        TripStorageFailure.READ_WRITE -> "여행 기록을 저장하거나 불러오지 못했습니다. 다시 시도해 주세요."
    },
    cause,
)

/** No corruption handler is registered: malformed or newer data must never become an empty trip. */
object TripStateSerializer : Serializer<TripStoreState> {
    private const val MAX_BYTES = 256 * 1024
    private val json = Json { encodeDefaults = true }
    override val defaultValue = TripStoreState()

    override suspend fun readFrom(input: InputStream): TripStoreState {
        try {
            val bytes = input.readBounded(MAX_BYTES)
            val text = bytes.decodeToString(throwOnInvalidSequence = true)
            val root = json.parseToJsonElement(text).jsonObject
            val schema = root["schemaVersion"]?.jsonPrimitive?.intOrNull
                ?: throw CorruptionException("여행 기록 버전 항목이 없습니다.")
            if (schema != TripStoreState.CURRENT_SCHEMA_VERSION) {
                throw TripStorageException(TripStorageFailure.UNSUPPORTED_SCHEMA)
            }
            if (!root.containsKey("trips")) throw CorruptionException("여행 기록 목록이 없습니다.")
            return json.decodeFromString<TripStoreState>(text).also(::validateTripState)
        } catch (error: TripStorageException) {
            throw error
        } catch (error: SerializationException) {
            throw CorruptionException("여행 기록 JSON 형식이 올바르지 않습니다.", error)
        } catch (error: CharacterCodingException) {
            throw CorruptionException("여행 기록 문자 인코딩이 올바르지 않습니다.", error)
        } catch (error: IllegalArgumentException) {
            throw CorruptionException("여행 기록 구조가 올바르지 않습니다.", error)
        }
    }

    override suspend fun writeTo(t: TripStoreState, output: OutputStream) {
        validateTripState(t)
        val bytes = json.encodeToString(TripStoreState.serializer(), t).encodeToByteArray()
        if (bytes.size > MAX_BYTES) throw TripStorageException(TripStorageFailure.INVALID_STATE)
        output.write(bytes)
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > limit) throw CorruptionException("여행 기록 파일이 너무 큽니다.")
            output.write(buffer, 0, count)
        }
    }
}

internal fun validateTripState(state: TripStoreState) {
    if (state.schemaVersion != TripStoreState.CURRENT_SCHEMA_VERSION) {
        throw TripStorageException(TripStorageFailure.UNSUPPORTED_SCHEMA)
    }
    requireValid(state.trips.size <= TourMode.entries.size)
    requireValid(state.trips.map { it.mode }.distinct().size == state.trips.size)
    state.trips.forEach(::validateStoredTrip)
}

internal fun validateStoredTrip(trip: StoredTrip) {
    val episode = Siyeok.episode
    requireValid(trip.episodeId == episode.id)
    requireValid(trip.sessionId.isNotBlank() && trip.sessionId.length <= 128)
    requireValid(trip.chapterIndex in episode.chapters.indices)
    requireValid(trip.startedAtEpochMillis > 0)
    requireValid(!trip.showMap || trip.phase == StoryPhase.EN_ROUTE)
    requireValid(trip.phase != StoryPhase.ENDED || trip.chapterIndex == episode.finalChapterIndex)
    requireValid(trip.phase != StoryPhase.FINAL_REVEAL || trip.chapterIndex == episode.finalChapterIndex - 1)
    requireValid(trip.phase != StoryPhase.REVEALED || trip.chapterIndex < episode.finalChapterIndex)
    listOf(trip.items, trip.clues).forEach { entries ->
        requireValid(entries.size <= episode.chapters.size)
        requireValid(entries.distinct().size == entries.size)
        requireValid(entries.all { it.isNotBlank() && it.length <= 4096 })
    }
    val completedCount = when (trip.phase) {
        StoryPhase.REVEALED, StoryPhase.FINAL_REVEAL -> trip.chapterIndex + 1
        StoryPhase.ENDED -> episode.chapters.size
        else -> trip.chapterIndex
    }
    val completed = episode.chapters.take(completedCount)
    requireValid(trip.items == completed.mapNotNull { it.item })
    requireValid(trip.clues == completed.mapNotNull { it.clue })
    requireValid(trip.tourRecords.size <= episode.chapters.size)
    requireValid(trip.tourRecords.map { it.key }.distinct().size == trip.tourRecords.size)
    trip.tourRecords.forEach { record ->
        requireValid(record.sessionId == trip.sessionId && record.mode == trip.mode)
        val index = TourCatalog.checkpoints.indexOfFirst { it.id == record.checkpointId }
        requireValid(index in 0..trip.chapterIndex)
        requireValid(record.startedAt.wallTimeMillis > 0 && record.startedAt.elapsedRealtimeMillis >= 0)
        requireValid(record.startedAt.bootId.isNotBlank() && record.startedAt.bootId.length <= 128)
        requireValid(!record.debugTimeBypass || trip.mode == TourMode.MOCK)
    }
    val requiredStarts = if (trip.phase == StoryPhase.EXPLORING) trip.chapterIndex + 1 else completedCount
    val startedIds = trip.tourRecords.map { it.checkpointId }.toSet()
    requireValid(TourCatalog.checkpoints.take(requiredStarts).all { it.id in startedIds })
}

/** An already persisted start remains immutable even if a duplicate UI command arrives. */
internal fun upsertTrip(previous: TripStoreState, trip: StoredTrip): TripStoreState {
    validateTripState(previous)
    validateStoredTrip(trip)
    val old = previous.trips.firstOrNull { it.mode == trip.mode }
    if (old != null && old.sessionId == trip.sessionId) {
        requireValid(old.episodeId == trip.episodeId && old.startedAtEpochMillis == trip.startedAtEpochMillis)
        old.tourRecords.forEach { saved ->
            val next = trip.tourRecords.firstOrNull { it.key == saved.key }
            requireValid(next != null && next.startedAt == saved.startedAt)
            requireValid(next != null && (!saved.debugTimeBypass || next.debugTimeBypass))
        }
    }
    return previous.copy(trips = previous.trips.filterNot { it.mode == trip.mode } + trip).also(::validateTripState)
}

private fun requireValid(condition: Boolean) {
    if (!condition) throw TripStorageException(TripStorageFailure.INVALID_STATE)
}

/**
 * One application-lifetime Typed DataStore per canonical file path, shared by all repository wrappers.
 * Every change is one atomic update of trip-progress.json; other modes are preserved.
 */
class TripProgressStore internal constructor(private val store: DataStore<TripStoreState>) {
    constructor(context: Context) : this(TripStoreRegistry.forFile(context.applicationContext.dataStoreFile(FILE_NAME)))

    suspend fun load(): TripStoreState = storageOperation {
        store.data.first().also(::validateTripState)
    }

    suspend fun save(trip: StoredTrip): StoredTrip = storageOperation {
        store.updateData { previous -> upsertTrip(previous, trip) }.trips.first { it.mode == trip.mode }
    }

    /** Explicit user reset only. A corrupt file is not silently replaced even through this method. */
    suspend fun remove(mode: TourMode): Unit = storageOperation {
        store.updateData { previous ->
            validateTripState(previous)
            previous.copy(trips = previous.trips.filterNot { it.mode == mode })
        }
        Unit
    }

    companion object { const val FILE_NAME = "trip-progress.json" }
}

private object TripStoreRegistry {
    private val stores = mutableMapOf<String, DataStore<TripStoreState>>()
    @Synchronized
    fun forFile(file: File): DataStore<TripStoreState> = stores.getOrPut(file.canonicalPath) {
        DataStoreFactory.create(
            serializer = TripStateSerializer,
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { file },
        )
    }
}

private suspend fun <T> storageOperation(action: suspend () -> T): T = try {
    action()
} catch (error: CancellationException) {
    throw error
} catch (error: TripStorageException) {
    throw error
} catch (error: CorruptionException) {
    throw TripStorageException(TripStorageFailure.CORRUPT, error)
} catch (error: Exception) {
    throw TripStorageException(TripStorageFailure.READ_WRITE, error)
}
