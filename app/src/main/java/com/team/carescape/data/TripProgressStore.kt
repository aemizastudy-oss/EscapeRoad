package com.team.carescape.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.team.carescape.game.StoryCatalog
import com.team.carescape.game.StoryPhase
import com.team.carescape.game.StoryPuzzleProgress
import com.team.carescape.game.TourCatalog
import com.team.carescape.model.TourMode
import com.team.carescape.model.TourStartRecord
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.CharacterCodingException
import java.util.UUID
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
    val flowVersion: Int = 1,
    val legacyCompletedCount: Int = 0,
    val puzzleProgress: List<StoryPuzzleProgress> = emptyList(),
    val legacySourceSessionId: String? = null,
)

@Serializable
data class TripStoreState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val trips: List<StoredTrip> = emptyList(),
    val archivedTrips: List<StoredTrip> = emptyList(),
) {
    companion object { const val CURRENT_SCHEMA_VERSION = 2 }
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
            val schemaValue = root["schemaVersion"]?.jsonPrimitive
            val schema = schemaValue?.takeIf { !it.isString }?.intOrNull
                ?: throw CorruptionException("여행 기록 버전 항목이 없습니다.")
            if (schema !in 1..TripStoreState.CURRENT_SCHEMA_VERSION) {
                throw TripStorageException(TripStorageFailure.UNSUPPORTED_SCHEMA)
            }
            if (!root.containsKey("trips")) throw CorruptionException("여행 기록 목록이 없습니다.")
            val decoded = json.decodeFromString<TripStoreState>(text)
            // No DataStore migration/writer is installed: this is an in-memory read adapter only.
            return (if (schema == 1) migrateLegacyTripState(decoded) else decoded).also(::validateTripState)
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
    requireValid(state.archivedTrips.size <= TourMode.entries.size)
    requireValid(state.archivedTrips.map { it.mode }.distinct().size == state.archivedTrips.size)
    state.archivedTrips.forEach { archived ->
        requireValid(archived.flowVersion == StoryCatalog.LEGACY_FLOW_VERSION)
        validateStoredTrip(archived)
    }
    state.trips.forEach { validateStoredTrip(it, state.archivedTrips) }
}

internal fun validateStoredTrip(trip: StoredTrip, archivedTrips: List<StoredTrip> = emptyList()) {
    if (trip.flowVersion !in StoryCatalog.LEGACY_FLOW_VERSION..StoryCatalog.CURRENT_FLOW_VERSION) {
        throw TripStorageException(TripStorageFailure.UNSUPPORTED_SCHEMA)
    }
    val episode = StoryCatalog.episodeFor(trip.flowVersion)
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
    val completedCount = StoryCatalog.completedCount(trip.chapterIndex, trip.phase, episode.chapters.size)
    requireValid(trip.legacyCompletedCount in 0..completedCount)
    if (trip.flowVersion == StoryCatalog.LEGACY_FLOW_VERSION) {
        requireValid(trip.phase != StoryPhase.PUZZLE && trip.legacyCompletedCount == 0 &&
            trip.puzzleProgress.isEmpty() && trip.legacySourceSessionId == null)
    } else {
        val sourceId = trip.legacySourceSessionId
        if (sourceId == null) requireValid(trip.legacyCompletedCount == 0)
        else {
            val source = archivedTrips.singleOrNull { it.mode == trip.mode && it.sessionId == sourceId }
                ?: throw TripStorageException(TripStorageFailure.INVALID_STATE)
            requireValid(source.flowVersion == StoryCatalog.LEGACY_FLOW_VERSION)
            requireValid(trip.sessionId == integratedSessionId(source) && trip.sessionId != source.sessionId)
            requireValid(trip.episodeId == source.episodeId && trip.startedAtEpochMillis == source.startedAtEpochMillis)
            requireValid(trip.legacyCompletedCount == StoryCatalog.completedCount(
                source.chapterIndex, source.phase, StoryCatalog.episodeFor(source.flowVersion).chapters.size,
            ))
            requireValid(trip.items.take(source.items.size) == source.items && trip.clues.take(source.clues.size) == source.clues)
        }
        requireValid(episode.chapters.all { it.puzzle != null })
        requireValid(trip.puzzleProgress.size <= episode.chapters.size)
        requireValid(trip.puzzleProgress.map { it.checkpointId }.distinct().size == trip.puzzleProgress.size)
        trip.puzzleProgress.forEach { progress ->
            val index = TourCatalog.checkpoints.indexOfFirst { it.id == progress.checkpointId }
            requireValid(index in trip.legacyCompletedCount..trip.chapterIndex)
            val puzzle = episode.chapters[index].puzzle!!
            requireValid(progress.wrongAttempts >= 0 && progress.hintsShown in 0..puzzle.hints.size)
            requireValid(progress.solved == (index < completedCount))
            if (!progress.solved) requireValid(index == trip.chapterIndex && trip.phase == StoryPhase.PUZZLE)
        }
        val solvedIds = trip.puzzleProgress.filter { it.solved }.map { it.checkpointId }.toSet()
        requireValid(TourCatalog.checkpoints.subList(trip.legacyCompletedCount, completedCount).all { it.id in solvedIds })
        if (trip.phase == StoryPhase.PUZZLE) requireValid(trip.puzzleProgress.any {
            it.checkpointId == TourCatalog.checkpoints[trip.chapterIndex].id && !it.solved
        })
    }
    val completed = episode.chapters.take(completedCount)
    requireValid(trip.items == completed.mapNotNull { it.item })
    requireValid(trip.clues == completed.mapNotNull { it.clue })
    requireValid(trip.tourRecords.size <= episode.chapters.size)
    requireValid(trip.tourRecords.map { it.key }.distinct().size == trip.tourRecords.size)
    trip.tourRecords.forEach { record ->
        requireValid(record.sessionId == trip.sessionId && record.mode == trip.mode)
        val index = TourCatalog.checkpoints.indexOfFirst { it.id == record.checkpointId }
        requireValid(index in trip.legacyCompletedCount..trip.chapterIndex)
        requireValid(record.startedAt.wallTimeMillis > 0 && record.startedAt.elapsedRealtimeMillis >= 0)
        requireValid(record.startedAt.bootId.isNotBlank() && record.startedAt.bootId.length <= 128)
        requireValid(!record.debugTimeBypass || trip.mode == TourMode.MOCK)
    }
    val requiredStarts = if (trip.phase in listOf(StoryPhase.EXPLORING, StoryPhase.PUZZLE)) trip.chapterIndex + 1 else completedCount
    val startedIds = trip.tourRecords.map { it.checkpointId }.toSet()
    requireValid(TourCatalog.checkpoints.subList(trip.legacyCompletedCount, requiredStarts).all { it.id in startedIds })
}

/** Keep all original records in the archive; the current unfinished tour must start anew. */
internal fun migrateLegacyTripState(previous: TripStoreState): TripStoreState {
    requireValid(previous.schemaVersion == 1 && previous.archivedTrips.isEmpty())
    requireValid(previous.trips.size <= TourMode.entries.size)
    requireValid(previous.trips.map { it.mode }.distinct().size == previous.trips.size)
    previous.trips.forEach {
        requireValid(it.flowVersion == StoryCatalog.LEGACY_FLOW_VERSION)
        validateStoredTrip(it)
    }
    return TripStoreState(
        trips = previous.trips.map { old ->
            old.copy(
                sessionId = integratedSessionId(old),
                flowVersion = StoryCatalog.CURRENT_FLOW_VERSION,
                legacyCompletedCount = StoryCatalog.completedCount(
                    old.chapterIndex, old.phase, StoryCatalog.episodeFor(old.flowVersion).chapters.size,
                ),
                phase = if (old.phase == StoryPhase.EXPLORING) StoryPhase.ARRIVED else old.phase,
                showMap = if (old.phase == StoryPhase.EXPLORING) false else old.showMap,
                tourRecords = emptyList(),
                puzzleProgress = emptyList(),
                legacySourceSessionId = old.sessionId,
            )
        },
        archivedTrips = previous.trips.toList(),
    ).also(::validateTripState)
}

private fun integratedSessionId(source: StoredTrip): String = UUID.nameUUIDFromBytes(
    "v04:${source.mode.name}:${source.sessionId}".toByteArray(Charsets.UTF_8),
).toString()

/** An already persisted start remains immutable even if a duplicate UI command arrives. */
internal fun upsertTrip(previous: TripStoreState, trip: StoredTrip): TripStoreState {
    validateTripState(previous)
    validateStoredTrip(trip, previous.archivedTrips)
    val old = previous.trips.firstOrNull { it.mode == trip.mode }
    if (old != null && old.sessionId == trip.sessionId) {
        requireValid(old.episodeId == trip.episodeId && old.startedAtEpochMillis == trip.startedAtEpochMillis)
        requireValid(old.flowVersion == trip.flowVersion && old.legacyCompletedCount == trip.legacyCompletedCount)
        requireValid(old.legacySourceSessionId == trip.legacySourceSessionId)
        requireValid(trip.chapterIndex >= old.chapterIndex)
        if (trip.chapterIndex == old.chapterIndex) requireValid(trip.phase.ordinal >= old.phase.ordinal)
        old.puzzleProgress.forEach { saved ->
            val next = trip.puzzleProgress.firstOrNull { it.checkpointId == saved.checkpointId }
            requireValid(next != null && next.wrongAttempts >= saved.wrongAttempts && next.hintsShown >= saved.hintsShown)
            requireValid(next != null && (!saved.solved || next.solved))
        }
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
