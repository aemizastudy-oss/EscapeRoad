package com.team.carescape.drive

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Pleos SDK connections owned by the drive chapter's background service. They are separate from the
 * app's foreground-only [com.team.carescape.platform.sdk.PleosSdkRuntime] on purpose: that runtime stops
 * when the app leaves the screen, while the drive chapter must keep working behind the driving view.
 */
internal class DriveSignals(
    context: Context,
    private val onGear: (DriveGear) -> Unit,
    private val onLocation: (Double, Double) -> Unit,
    private val onNavigation: (remainingM: Int, etaSec: Int) -> Unit,
    private val onStatus: (String) -> Unit,
) : AutoCloseable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var fused: FusedLocation? = null
    private var navi: NaviHelper? = null
    private var naviReady = false
    private var pendingRoute: LegPlan? = null
    private var gearRetries = 0
    private var closed = false

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = post { onGear(map(gear)) }
        override fun onFailed(e: Exception) = post { Log.w(TAG, "gear: ${e.message}"); onGear(DriveGear.UNKNOWN); retryGear() }
    }
    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            post { onLocation(location.latitude, location.longitude) }
        }
        override fun onError(exception: Exception) = post { Log.w(TAG, "location: ${exception.message}") }
    }
    private val naviListener = object : NaviHelperEventListener {
        override fun onDrivingInfo(info: DrivingInfo) = post { onNavigation(info.destination.distance, info.destination.duration) }
        override fun onDestinationInfo(info: DestinationInfo) = post { onNavigation(info.remainDistance, info.remainTime) }
        override fun onRouteStarted(info: RouteStartInfo) = post { onStatus("길 안내: ${info.name}"); onNavigation(info.distance, info.duration) }
        override fun onDestinationArrived(info: DestinationArrivedInfo) = post { onStatus("내비 도착: ${info.name}") }
        override fun onError(code: NaviErrorCode) = post { onStatus("내비 오류: $code") }
    }

    fun start() {
        connectGear()
        connectLocation()
        connectNavigation()
    }

    private fun connectGear() {
        if (closed || vehicle != null) return
        try {
            val v = Vehicle(context).also { it.initialize() }
            vehicle = v
            v.getGear().getCurrentGearSelectionState({ g: VehicleGear? -> post { onGear(map(g)) } }, { e -> Log.w(TAG, "gear read: ${e.message}") })
            v.getGear().registerGearSelection(gearListener)
        } catch (e: Exception) {
            Log.w(TAG, "vehicle unavailable: ${e.message}"); onStatus("기어 신호 없음"); retryGear()
        }
    }

    private fun retryGear() {
        if (closed || gearRetries++ > 20) return
        main.postDelayed({
            vehicle?.let { runCatching { it.getGear().unregisterGearSelection(gearListener); it.release() } }
            vehicle = null
            connectGear()
        }, 3_000)
    }

    private fun connectLocation() {
        if (!granted("pleos.car.permission.FUSED_LOCATION")) { onStatus("위치 권한 필요(앱에서 SDK 권한 허용)"); return }
        try {
            val sdk = FusedLocation(context)
            fused = sdk
            sdk.initialize()
            // isSupported() answers once the service is bound.
            main.postDelayed({
                if (closed) return@postDelayed
                if (runCatching { sdk.isSupported() }.getOrDefault(false)) sdk.registerFusedLocationCallback(locationListener)
                else onStatus("위치 서비스 미지원")
            }, 1_500)
        } catch (e: Exception) { Log.w(TAG, "fused unavailable: ${e.message}"); onStatus("위치 서비스 오류") }
    }

    private fun connectNavigation() {
        try {
            val helper = NaviHelper(context)
            navi = helper
            helper.initialize({
                post {
                    naviReady = true
                    runCatching { helper.addListener(naviListener) }
                    pendingRoute?.let { route(it) }
                }
            }, { e -> post { Log.w(TAG, "navi init: ${e.message}"); onStatus("내비 연결 실패") } })
        } catch (e: Exception) { Log.w(TAG, "navi unavailable: ${e.message}") }
    }

    /** Sets the place as the navigation destination (same request shape as the app's own guidance). */
    fun route(plan: LegPlan) {
        val helper = navi
        if (helper == null || !naviReady) { pendingRoute = plan; return }
        pendingRoute = null
        if (!granted("pleos.car.permission.NAVI_ROUTE_SEARCH")) { onStatus("길 안내 권한 필요"); return }
        runCatching {
            helper.requestRoute(RouteInfo(latitude = plan.latitude, longitude = plan.longitude, poiName = plan.place,
                poiId = "", address = "", poiSubId = ""))
        }.onFailure { Log.w(TAG, "route: ${it.message}"); onStatus("길 안내 요청 실패") }
    }

    /** Puts the Pleos navigation screen in front (route guidance while driving). */
    fun showNavigation() {
        val helper = navi?.takeIf { naviReady } ?: return
        if (!granted("pleos.car.permission.NAVI_CUSTOM_MAP")) { onStatus("내비 화면 권한 필요"); return }
        runCatching { helper.requestNavigationScreen(NavigationScreenRequest.Foreground) }
            .onFailure { Log.w(TAG, "navigation screen: ${it.message}") }
    }

    private fun map(gear: VehicleGear?): DriveGear = when (gear) {
        null, VehicleGear.GEAR_UNKNOWN -> DriveGear.UNKNOWN
        VehicleGear.GEAR_PARK -> DriveGear.PARK
        VehicleGear.GEAR_REVERSE -> DriveGear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> DriveGear.NEUTRAL
        else -> DriveGear.DRIVE // GEAR_DRIVE and manual GEAR_1..9
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun post(action: () -> Unit) { main.post { if (!closed) action() } }

    override fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        vehicle?.let { runCatching { it.getGear().unregisterGearSelection(gearListener) }; runCatching { it.release() } }
        fused?.let { runCatching { it.unregisterFusedLocationCallback(locationListener) }; runCatching { it.release() } }
        navi?.let { runCatching { it.removeListener(naviListener) }; runCatching { it.release() } }
        vehicle = null; fused = null; navi = null
    }

    private companion object { const val TAG = "DriveSignals" }
}
