package com.example.roadescape.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadescape.RoadEscapeApplication
import com.example.roadescape.game.GameCommand
import com.example.roadescape.model.CheckpointStatus
import com.example.roadescape.model.GameSession
import com.example.roadescape.model.LockInput
import com.example.roadescape.model.ParkingState
import com.example.roadescape.platform.SimulatedParkingProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies ordering at the event processor, independently of disabled UI buttons. */
@RunWith(AndroidJUnit4::class)
class GameViewModelOrderingTest {
    private val viewModelStore = ViewModelStore()
    private lateinit var viewModel: GameViewModel
    private lateinit var parking: SimulatedParkingProvider

    @Before
    fun createFreshSession() {
        val application = ApplicationProvider.getApplicationContext<RoadEscapeApplication>()
        onMain {
            parking = application.container.sources.parking as SimulatedParkingProvider
            parking.invalidate()
            viewModel = ViewModelProvider(
                viewModelStore,
                GameViewModel.factory(application.container),
            )[GameViewModel::class.java]
            viewModel.setForeground(true)
        }
        val previous = await { !it.loading && it.error == null }.session?.sessionId
        onMain { viewModel.newGameConfirmed() }
        val fresh = await { !it.saving && it.session != null && it.session.sessionId != previous }
        onMain { parking.simulate(ParkingState.PARKED) }
        await { it.parking == ParkingState.PARKED }
        onMain {
            viewModel.command(GameCommand.Arrive(fresh.session!!.sessionId, "cp_01", 1L))
        }
        await { !it.saving && it.session?.progress?.first()?.arrivedAt != null }
    }

    @After
    fun clearViewModel() {
        onMain { viewModelStore.clear() }
    }

    @Test
    fun submitDuringDrivingCannotBecomeValidAfterImmediateParking() {
        val sessionId = viewModel.state.value.session!!.sessionId
        onMain {
            parking.simulate(ParkingState.DRIVING)
            viewModel.command(GameCommand.Submit(sessionId, "p_01", LockInput.Numeric("0427")))
            parking.simulate(ParkingState.PARKED)
            // A valid following command serves as a barrier for the queued rejected submission.
            viewModel.command(GameCommand.RevealHint(sessionId, "p_01", 1))
        }
        val settled = await {
            val progress = it.session?.progress?.first()
            !it.saving && (progress?.revealedHintCount == 1 || progress?.status?.isComplete == true)
        }.session!!
        assertFalse(settled.progress.first().status.isComplete)
        assertEquals(0, settled.progress.first().wrongAttempts)
        assertEquals(1, settled.progress.first().revealedHintCount)
        assertTrue(settled.acquiredClues.isEmpty())
    }

    @Test
    fun acceptedCompletionIsRetainedWhenParkingChangesDuringOrAfterSave() {
        val sessionId = viewModel.state.value.session!!.sessionId
        onMain {
            viewModel.command(GameCommand.Submit(sessionId, "p_01", LockInput.Numeric("0427")))
        }
        await { it.saving || it.session?.progress?.first()?.status == CheckpointStatus.SOLVED }
        onMain { parking.simulate(ParkingState.DRIVING) }
        val solved = await {
            !it.saving && it.parking == ParkingState.DRIVING &&
                it.session?.progress?.first()?.status == CheckpointStatus.SOLVED
        }.session!!
        assertEquals(1, solved.acquiredClues.size)
        val saved = runBlocking { viewModel.container.gameRepository.load() }!!
        assertEquals(solved, saved)
    }

    @Test
    fun backgroundInvalidatesParkingAndRejectsSubmittedWorkAfterReturning() {
        val sessionId = viewModel.state.value.session!!.sessionId
        onMain {
            viewModel.setForeground(false)
            assertEquals(ParkingState.UNKNOWN, parking.snapshot.value.state)
            viewModel.command(GameCommand.Submit(sessionId, "p_01", LockInput.Numeric("0427")))
            viewModel.setForeground(true)
            parking.simulate(ParkingState.PARKED)
            viewModel.command(GameCommand.RevealHint(sessionId, "p_01", 1))
        }
        val settled = await {
            val progress = it.session?.progress?.first()
            !it.saving && (progress?.revealedHintCount == 1 || progress?.status?.isComplete == true)
        }.session!!
        assertFalse(settled.progress.first().status.isComplete)
        assertEquals(1, settled.progress.first().revealedHintCount)
        assertTrue(settled.acquiredClues.isEmpty())
    }

    @Test
    fun staleSubmissionCannotAwardCluesToResetSession() {
        val original = viewModel.state.value.session!!
        onMain {
            viewModel.newGameConfirmed()
            viewModel.command(GameCommand.Submit(original.sessionId, "p_01", LockInput.Numeric("0427")))
        }
        val fresh = await {
            !it.saving && it.session != null && it.session.sessionId != original.sessionId
        }.session!!
        // Commit a current-session command after the reset/old submission queue.
        onMain { viewModel.command(GameCommand.Arrive(fresh.sessionId, "cp_01", 2L)) }
        val settled: GameSession = await {
            !it.saving && it.session?.sessionId == fresh.sessionId &&
                it.session.progress.first().arrivedAt == 2L
        }.session!!
        assertNotEquals(original.sessionId, settled.sessionId)
        assertTrue(settled.acquiredClues.isEmpty())
        assertEquals(CheckpointStatus.NOT_STARTED, settled.progress.first().status)
        assertEquals(0, settled.progress.first().wrongAttempts)
    }

    private fun await(predicate: (GameUiState) -> Boolean): GameUiState = runBlocking {
        withTimeout(15_000) { viewModel.state.first { predicate(it) } }
    }

    private fun onMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { action() }
    }
}
