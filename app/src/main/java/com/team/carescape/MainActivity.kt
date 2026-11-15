package com.team.carescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.team.carescape.ui.EscapeApp
import com.team.carescape.ui.GameViewModel
import com.team.carescape.ui.LiveTourTestSession
import com.team.carescape.platform.sdk.PleosSdkRuntime
import com.team.carescape.drive.DriveService
import com.team.carescape.drive.DemoArrival

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private val sdkPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsChanged()
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The debug-only explicit session is selected before the first ViewModel access.
        LiveTourTestSession.configure(intent.getStringExtra(LiveTourTestSession.INTENT_EXTRA))
        if (LiveTourTestSession.capture() == null) {
            DriveService.watch(this) // 원본 여행용 안내는 격리된 SDK 검증 세션에서 시작하지 않는다.
            DemoArrival.watch(this, vm)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                if (vm.liveSdkVehicleTest) {
                    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        EscapeApp(vm, ::requestSdkPermissions)
                    }
                } else EscapeApp(vm, ::requestSdkPermissions)
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
        val missing = PleosSdkRuntime.missingRuntimePermissions(this).toMutableList()
        // 도착 시 자동 화면 전환 대신 사용자가 선택한 알림으로 복귀한다.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            missing += android.Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isNotEmpty()) sdkPermissions.launch(missing.toTypedArray())
        else vm.onPermissionsChanged()
    }
}
