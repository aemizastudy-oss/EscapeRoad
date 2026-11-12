package com.example.roadescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roadescape.ui.GameViewModel
import com.example.roadescape.ui.RoadEscapeApp
import com.example.roadescape.ui.RoadEscapeTheme
import com.example.roadescape.platform.sdk.PleosSdkRuntime

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        gameViewModel.reconnectSdk()
    }
    private val gameViewModel by viewModels<GameViewModel> {
        GameViewModel.factory((application as RoadEscapeApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoadEscapeTheme {
                val state = gameViewModel.state.collectAsStateWithLifecycle()
                RoadEscapeApp(gameViewModel, state.value) {
                    val missing = PleosSdkRuntime.missingRuntimePermissions(this)
                    if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
                    else gameViewModel.reconnectSdk()
                }
            }
        }
    }

    override fun onStart() { super.onStart(); gameViewModel.setForeground(true) }
    override fun onStop() { gameViewModel.setForeground(false); super.onStop() }
}
