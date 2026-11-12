package com.example.roadescape.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.roadescape.AppContainer
import com.example.roadescape.data.GameStorageException
import com.example.roadescape.data.StorageFailure
import com.example.roadescape.game.GameCommand
import com.example.roadescape.game.GameController
import com.example.roadescape.game.GameFeedback
import com.example.roadescape.game.GateReason
import com.example.roadescape.game.PlayEnvironment
import com.example.roadescape.model.GamePackage
import com.example.roadescape.model.GameSession
import com.example.roadescape.model.ParkingState
import com.example.roadescape.platform.ParkingSnapshot
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameUiError(val contentError: Boolean, val reason: String, val detail: String?)

data class GameUiState(
    val content: GamePackage? = null,
    val session: GameSession? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: GameUiError? = null,
    val parking: ParkingState = ParkingState.UNKNOWN,
    val foreground: Boolean = false,
    val feedback: GameFeedback? = null,
    val rejectedGate: GateReason? = null,
) {
    val environment get() = PlayEnvironment(foreground, parking, !loading && error == null, saving)
    val canReadContent get() = foreground && parking == ParkingState.PARKED && error == null && !loading
}

class GameViewModel(val container: AppContainer) : ViewModel() {
    private sealed interface Event {
        data object Load : Event
        data object NewGame : Event
        data class Command(val command: GameCommand, val parking: ParkingSnapshot, val foreground: Boolean, val foregroundRevision: Long) : Event
    }
    private val mutable = MutableStateFlow(GameUiState())
    val state = mutable.asStateFlow()
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val destinations = Channel<String>(Channel.BUFFERED)
    val navigation = destinations.receiveAsFlow()
    private var foregroundRevision = 0L

    init {
        viewModelScope.launch {
            for (event in events) {
                when (event) {
                    Event.Load -> load()
                    Event.NewGame -> newGame()
                    is Event.Command -> process(event)
                }
            }
        }
        viewModelScope.launch {
            container.sources.parking.snapshot.collect { parking ->
                mutable.update { it.copy(parking = parking.state, feedback = null, rejectedGate = null) }
            }
        }
        viewModelScope.launch {
            container.sources.arrival.arrivals.collect { arrival ->
                enqueue(GameCommand.Arrive(arrival.sessionId, arrival.checkpointId, arrival.arrivedAt))
            }
        }
        events.trySend(Event.Load)
    }

    fun setForeground(foreground: Boolean) {
        foregroundRevision++
        if (!foreground) container.sources.parking.invalidate()
        mutable.update { it.copy(foreground = foreground, parking = container.sources.parking.snapshot.value.state) }
    }

    fun retry() { if (!mutable.value.saving) events.trySend(Event.Load) }
    fun newGameConfirmed() { if (!mutable.value.loading && !mutable.value.saving) events.trySend(Event.NewGame) }
    fun command(command: GameCommand) {
        if (!mutable.value.loading && !mutable.value.saving) enqueue(command)
    }

    private fun enqueue(command: GameCommand) {
        events.trySend(Event.Command(command, container.sources.parking.snapshot.value, mutable.value.foreground, foregroundRevision))
    }

    private suspend fun load() {
        mutable.update { it.copy(loading = true, error = null, feedback = null) }
        val content = try {
            container.packageRepository.load()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutable.update { it.copy(loading = false, error = GameUiError(true, "CONTENT", e.message)) }
            return
        }
        mutable.update { it.copy(content = content) }
        try {
            val session = container.gameRepository.load()
            if (session != null) validate(content, session)
            mutable.update { it.copy(session = session, loading = false, error = null) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            storageError(e)
        }
    }

    private fun validate(content: GamePackage, session: GameSession) {
        if (session.packageId != content.id || session.contentVersion != content.contentVersion) {
            throw GameStorageException(StorageFailure.CONTENT_MISMATCH)
        }
        if (GameController.validateSession(content, session).isNotEmpty()) {
            throw GameStorageException(StorageFailure.INVALID_PROGRESS)
        }
    }

    private suspend fun newGame() {
        val content = mutable.value.content ?: return
        if (mutable.value.error?.contentError == true) return
        mutable.update { it.copy(saving = true, feedback = null) }
        try {
            val fresh = GameController.newSession(content, UUID.randomUUID().toString())
            val saved = container.gameRepository.reset(fresh)
            mutable.update { it.copy(session = saved, saving = false, error = null, rejectedGate = null) }
            destinations.send("checkpoints")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            storageError(e)
        }
    }

    private suspend fun process(event: Event.Command) {
        val command = event.command
        val state = mutable.value
        val content = state.content ?: return
        val session = state.session ?: return
        val latestParking = container.sources.parking.snapshot.value
        // Capture conditions when a tap arrives and reject it after any intervening suspension.
        // This also handles StateFlow conflating a fast DRIVING -> PARKED pair.
        val staleSafety = command !is GameCommand.Arrive &&
            (event.parking.revision != latestParking.revision || event.foregroundRevision != foregroundRevision)
        val environment = state.environment.copy(
            parking = if (staleSafety) ParkingState.UNKNOWN else event.parking.state,
            foreground = event.foreground && state.foreground,
        )
        val transition = GameController.reduce(content, session, command, environment)
        if (transition.session == session) {
            mutable.update { it.copy(feedback = transition.feedback, rejectedGate = transition.gate) }
            return
        }
        mutable.update { it.copy(saving = true, feedback = null, rejectedGate = null) }
        try {
            val saved = container.gameRepository.save(transition.session)
            mutable.update { it.copy(session = saved, saving = false, feedback = transition.feedback) }
            if (GameController.currentCheckpoint(content, saved) == null) destinations.send("completion")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            storageError(e)
        }
    }

    private fun storageError(exception: Exception) {
        val reason = (exception as? GameStorageException)?.reason?.name ?: "READ_WRITE"
        mutable.update {
            it.copy(loading = false, saving = false, error = GameUiError(false, reason, exception.message))
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(container) as T
        }
    }
}
