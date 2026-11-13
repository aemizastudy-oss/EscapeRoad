package com.team.carescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.team.carescape.ui.EscapeApp
import com.team.carescape.ui.GameViewModel
import com.team.carescape.platform.sdk.PleosSdkRuntime

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private val sdkPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                EscapeApp(vm, ::requestSdkPermissions)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setForeground(true)
    }

    override fun onStop() {
        vm.setForeground(false)
        super.onStop()
    }

    private fun requestSdkPermissions() {
        val missing = PleosSdkRuntime.missingRuntimePermissions(this)
        if (missing.isNotEmpty()) sdkPermissions.launch(missing.toTypedArray())
        else vm.onPermissionsChanged()
    }
}
