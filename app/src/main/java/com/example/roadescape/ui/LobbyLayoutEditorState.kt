package com.example.roadescape.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.roadescape.BuildConfig
import com.example.roadescape.data.LobbyLayoutLoadResult
import com.example.roadescape.data.LobbyLayoutRepository
import com.example.roadescape.model.LobbyBlock
import com.example.roadescape.model.LobbyLayoutJson
import com.example.roadescape.model.LobbyLayoutProfile
import com.example.roadescape.model.LobbyLayoutSettings
import com.example.roadescape.model.LobbyOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Canvas coordinates in dp. Recording these values never writes Compose snapshot state. */
data class LobbyBlockBounds(
    val baseXDp: Float,
    val baseYDp: Float,
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val canvasWidthDp: Float,
    val canvasHeightDp: Float,
)

/** A local UI draft. Game sessions and navigation are deliberately outside this class. */
class LobbyLayoutEditorState internal constructor(
    val profile: LobbyLayoutProfile,
    private val repository: LobbyLayoutRepository,
    initial: LobbyLayoutLoadResult,
    private val scope: CoroutineScope,
    private val safetyAllowed: () -> Boolean,
) {
    private var saved by mutableStateOf(initial.settings)
    private val defaults: LobbyLayoutSettings = initial.defaults
    private var draft by mutableStateOf(initial.settings.offsets(profile).toMap())
    private var editingRequested by mutableStateOf(false)
    private var saveJob: Job? = null
    private var operationRevision = 0L
    private val measuredBounds = mutableMapOf<LobbyBlock, LobbyBlockBounds>()

    var selectedBlock by mutableStateOf(LobbyBlock.WELCOME)
        private set
    var saving by mutableStateOf(false)
        private set
    var warning by mutableStateOf(initial.warning)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var panelExpanded by mutableStateOf(true)
        private set
    var stepDp by mutableFloatStateOf(1f)
        private set

    val canEdit: Boolean get() = BuildConfig.DEBUG && safetyAllowed()
    // Read the current safety input as well as the draft flag: unsafe frames expose no handles.
    val editing: Boolean get() = editingRequested && canEdit
    val dirty: Boolean get() = draft != saved.offsets(profile)
    val statusText: String
        get() = when {
            saving -> "저장 중…"
            message != null -> message!!
            dirty -> "미저장 변경"
            else -> "저장됨"
        }

    fun offsetFor(id: LobbyBlock): LobbyOffset =
        (if (editing) draft else saved.offsets(profile))[id] ?: LobbyOffset(0f, 0f)

    fun recordBounds(id: LobbyBlock, bounds: LobbyBlockBounds) {
        if (listOf(bounds.baseXDp, bounds.baseYDp, bounds.xDp, bounds.yDp,
                bounds.widthDp, bounds.heightDp, bounds.canvasWidthDp, bounds.canvasHeightDp)
                .all(Float::isFinite) && bounds.widthDp >= 0f && bounds.heightDp >= 0f &&
            bounds.canvasWidthDp >= 0f && bounds.canvasHeightDp >= 0f
        ) measuredBounds[id] = bounds
    }

    fun boundsFor(id: LobbyBlock): LobbyBlockBounds? = measuredBounds[id]

    fun beginEditing() {
        if (!canEdit || saving) return
        // A previous explicitly requested commit may have completed as its UI was disposed.
        // Always start the next draft from the actual private preference value.
        val latest = repository.load()
        saved = latest.settings
        warning = latest.warning
        draft = saved.offsets(profile).toMap()
        message = null
        panelExpanded = true
        editingRequested = true
    }

    fun select(id: LobbyBlock) {
        if (editing && !saving) selectedBlock = id
    }

    fun moveBy(id: LobbyBlock, dxDp: Float, dyDp: Float) {
        if (!editing || saving || !dxDp.isFinite() || !dyDp.isFinite()) return
        selectedBlock = id
        val old = draft[id] ?: LobbyOffset(0f, 0f)
        val bounds = measuredBounds[id]
        val next = if (bounds == null) {
            LobbyOffset((old.xDp + dxDp).coerceIn(-4096f, 4096f),
                (old.yDp + dyDp).coerceIn(-4096f, 4096f))
        } else {
            // Start from the rendered position when an imported offset extends off the canvas.
            val maxX = (bounds.canvasWidthDp - bounds.widthDp).coerceAtLeast(0f)
            val maxY = (bounds.canvasHeightDp - bounds.heightDp).coerceAtLeast(0f)
            val x = (bounds.baseXDp + old.xDp).coerceIn(0f, maxX)
            val y = (bounds.baseYDp + old.yDp).coerceIn(0f, maxY)
            LobbyOffset(((x + dxDp).coerceIn(0f, maxX) - bounds.baseXDp).coerceIn(-4096f, 4096f),
                ((y + dyDp).coerceIn(0f, maxY) - bounds.baseYDp).coerceIn(-4096f, 4096f))
        }
        draft = draft + (id to next)
        message = null
    }

    fun nudge(dx: Int, dy: Int) = moveBy(selectedBlock, dx * stepDp, dy * stepDp)

    fun toggleStep() {
        if (editing && !saving) stepDp = if (stepDp == 1f) 10f else 1f
    }

    fun togglePanel() {
        if (editing) panelExpanded = !panelExpanded
    }

    fun resetDraftToDefaults() {
        if (!editing || saving) return
        draft = defaults.offsets(profile).toMap()
        message = "기본값 적용 · 저장 전"
    }

    /** Exporting does not persist. The caller copies only after an explicit user click. */
    fun exportJson(): String? = if (editing && !saving) {
        LobbyLayoutJson.encode(saved.withOffsets(profile, draft))
    } else null

    fun copiedToClipboard() {
        if (editing) message = "레이아웃 JSON을 복사했습니다 · 저장은 별도"
    }

    fun save() {
        if (!editing || saving) return
        val requestedOffsets = draft.toMap()
        val revision = ++operationRevision
        saving = true
        message = null
        saveJob = scope.launch {
            try {
                // A queued action cannot begin writing after the foreground/parking gate closed.
                if (!editing || revision != operationRevision) return@launch
                val result = repository.saveProfile(profile, requestedOffsets)
                if (revision != operationRevision || !canEdit) return@launch
                result.fold(onSuccess = { settings ->
                    saved = settings
                    draft = settings.offsets(profile).toMap()
                    warning = null
                    message = "저장됨"
                    editingRequested = false
                }, onFailure = {
                    message = "저장 실패 · 변경 내용을 유지했습니다"
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (revision == operationRevision) message = "저장 실패 · 변경 내용을 유지했습니다"
            } finally {
                if (revision == operationRevision) {
                    saving = false
                    saveJob = null
                }
            }
        }
    }

    fun cancel() {
        operationRevision++
        saveJob?.cancel()
        saveJob = null
        saving = false
        draft = saved.offsets(profile).toMap()
        editingRequested = false
        message = null
    }

    internal fun onSafetyChanged(allowed: Boolean) {
        if ((!allowed || !BuildConfig.DEBUG) && (editingRequested || saving)) cancel()
    }

    internal fun close() = cancel()
}

@Composable
fun rememberLobbyLayoutEditor(
    profile: LobbyLayoutProfile,
    canEditSafely: Boolean,
): LobbyLayoutEditorState {
    val applicationContext = LocalContext.current.applicationContext
    val repository = remember(applicationContext) { LobbyLayoutRepository(applicationContext) }
    val scope = rememberCoroutineScope()
    val currentSafety = rememberUpdatedState(canEditSafely)
    // Replacing the state on a profile change discards that profile's unsaved draft.
    val editor = remember(repository, profile) {
        LobbyLayoutEditorState(profile, repository, repository.load(), scope) { currentSafety.value }
    }
    SideEffect { editor.onSafetyChanged(canEditSafely) }
    DisposableEffect(editor) { onDispose { editor.close() } }
    return editor
}
