package com.example.roadescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roadescape.ui.GameViewModel
import com.example.roadescape.ui.RoadEscapeApp
import com.example.roadescape.ui.RoadEscapeTheme

class MainActivity : ComponentActivity() {
    private val gameViewModel by viewModels<GameViewModel> {
        GameViewModel.factory((application as RoadEscapeApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoadEscapeTheme {
                val state = gameViewModel.state.collectAsStateWithLifecycle()
                RoadEscapeApp(gameViewModel, state.value)
            }
        }
    }

    override fun onStart() { super.onStart(); gameViewModel.setForeground(true) }
    override fun onStop() { gameViewModel.setForeground(false); super.onStop() }
}
