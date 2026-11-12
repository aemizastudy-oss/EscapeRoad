package com.example.roadescape.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.roadescape.R
import com.example.roadescape.game.GameController
import com.example.roadescape.model.ParkingState
import com.example.roadescape.platform.ArrivalEvent
import com.example.roadescape.platform.SimulatedArrivalProvider
import com.example.roadescape.platform.SimulatedParkingProvider

@Composable
fun EnvironmentPanel(viewModel: GameViewModel, state: GameUiState) {
    val content = state.content
    val session = state.session
    val current = if (content != null && session != null) GameController.currentCheckpoint(content, session) else null
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ParkingState.entries.forEach { parking ->
                    EscapeButton(parking.name, "parking-${parking.name}", {
                        (viewModel.container.sources.parking as SimulatedParkingProvider).simulate(parking)
                    }, secondary = state.parking != parking)
                }
                EscapeButton(stringResource(R.string.dev_arrive), "arrive", {
                    if (session != null && current != null) {
                        (viewModel.container.sources.arrival as SimulatedArrivalProvider).simulate(ArrivalEvent(session.sessionId, current.id, System.currentTimeMillis()))
                    }
                }, enabled = current != null && !state.loading && !state.saving && state.error == null, secondary = true)
            }
            if (content != null && session != null) {
                val gate = GameController.gate(content, session, state.environment)
                Text(stringResource(R.string.dev_gate, stringResource(gateLabel(gate))), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            } else Text(stringResource(R.string.dev_empty), style = MaterialTheme.typography.labelMedium)
        }
    }
}
