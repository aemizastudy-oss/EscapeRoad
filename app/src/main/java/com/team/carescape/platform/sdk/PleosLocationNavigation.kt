package com.team.carescape.platform.sdk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.team.carescape.model.TourLocation
import com.team.carescape.model.TourMode
import com.team.carescape.model.WaypointConfig

internal class PleosLocationNavigation(
    context: Context,
    private val onLocation: (TourLocation?) -> Unit,
    private val onFused: (SdkFeatureState, Float?) -> Unit,
    private val onNavigation: (SdkFeatureState, String) -> Unit,
) : AutoCloseable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var fused: FusedLocation? = null
    private var navigation: NaviHelper? = null
    private var fusedListener: FusedLocationListener? = null
    private var naviListener: NaviHelperEventListener? = null
    private var closed = false
    private var navigationReady = false
    private var initializedAt = 0L
    private var lastLocation: TourLocation? = null
    private var lastTimestamp = 0L
    private var fusedState = SdkFeatureState(SdkAvailability.CHECKING)
    private var navState = SdkFeatureState(SdkAvailability.CHECKING)
    private var routeState = "IDLE"

    fun start() {
        if (closed || fused != null || navigation != null) return
        initializedAt = SystemClock.elapsedRealtime()
        onFused(fusedState, null); onNavigation(navState, routeState)
        initializeLocation()
        initializeNavigation()
        main.post(tick)
    }

    private fun initializeLocation() {
        if (!granted("pleos.car.permission.FUSED_LOCATION")) {
            fusedState = SdkFeatureState(SdkAvailability.PERMISSION_DENIED, "FUSED_LOCATION_PERMISSION")
            onFused(fusedState, null); return
        }
        try {
            val sdk = FusedLocation(context)
            fused = sdk
            if (!sdk.isSupported()) {
                fusedState = SdkFeatureState(SdkAvailability.UNSUPPORTED, "FUSED_LOCATION_UNSUPPORTED")
                onFused(fusedState, null); return
            }
            val listener = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) = onMain { if (!closed) accept(location) }
                override fun onError(exception: Exception) = onMain {
                    if (!closed) invalidateLocation(failureState(exception), "LOCATION_SDK_ERROR")
                }
            }
            fusedListener = listener
            sdk.initialize()
            sdk.registerFusedLocationCallback(listener)
            // Deliberately no getLastKnownLocation(): arrival proof must be an updated callback.
            fusedState = SdkFeatureState(SdkAvailability.NO_DATA, "WAITING_FOR_LOCATION_UPDATE")
            onFused(fusedState, null)
        } catch (error: Exception) { invalidateLocation(failureState(error), "LOCATION_SDK_ERROR") }
    }

    private fun initializeNavigation() {
        if (!granted("pleos.car.permission.NAVI_ROUTE")) {
            updateNavigation(SdkFeatureState(SdkAvailability.PERMISSION_DENIED, "NAVI_ROUTE_PERMISSION"), "UNAVAILABLE"); return
        }
        try {
            val sdk = NaviHelper(context)
            navigation = sdk
            val listener = object : NaviHelperEventListener {
                override fun onRouteStarted(routeStartInfo: RouteStartInfo) = onMain {
                    if (!closed) updateNavigation(SdkFeatureState(SdkAvailability.READY), "ROUTING_STARTED_EVENT")
                }
                override fun onRoutingCompleted() = onMain {
                    if (!closed) updateNavigation(SdkFeatureState(SdkAvailability.READY), "ROUTING_COMPLETED_EVENT")
                }
                override fun onRouteCancelled() = onMain {
                    if (!closed) updateNavigation(SdkFeatureState(SdkAvailability.READY), "CANCELLED_EVENT")
                }
                override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = onMain {
                    if (!closed) updateNavigation(SdkFeatureState(SdkAvailability.READY), "DESTINATION_EVENT_NOT_LOCATION_PROOF")
                }
                override fun onError(errorCode: NaviErrorCode) = onMain {
                    if (!closed) updateNavigation(SdkFeatureState(SdkAvailability.ERROR, errorCode.name), "ERROR")
                }
            }
            naviListener = listener
            sdk.initialize({
                onMain {
                    if (!closed) {
                        try {
                            sdk.addListener(listener)
                            navigationReady = true
                            updateNavigation(SdkFeatureState(SdkAvailability.READY, "SERVICE_CONNECTED"), "IDLE")
                        } catch (error: Exception) { updateNavigation(failureState(error), "ERROR") }
                    }
                }
            }, { error -> onMain { if (!closed) updateNavigation(failureState(error), "ERROR") } })
        } catch (error: Exception) { updateNavigation(failureState(error), "ERROR") }
    }

    fun navigate(waypoint: WaypointConfig): Boolean {
        if (closed || !navigationReady) return false
        val latitude = waypoint.latitude
        val longitude = waypoint.longitude
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            updateNavigation(SdkFeatureState(SdkAvailability.NO_DATA, "WAYPOINT_COORDINATES_NOT_CONFIGURED"), "NOT_REQUESTED")
            return false
        }
        if (!granted("pleos.car.permission.NAVI_ROUTE_SEARCH")) {
            updateNavigation(SdkFeatureState(SdkAvailability.PERMISSION_DENIED, "NAVI_ROUTE_SEARCH_PERMISSION"), "NOT_REQUESTED")
            return false
        }
        return try {
            // Coordinates/name are configured by the app; unknown POI/provider IDs are not invented.
            navigation!!.requestRoute(RouteInfo(latitude = latitude, longitude = longitude, poiName = waypoint.name,
                poiId = "", address = "", poiSubId = ""))
            updateNavigation(SdkFeatureState(SdkAvailability.READY, "REQUEST_SENT_AWAIT_EVENTS"), "REQUESTED")
            true
        } catch (error: Exception) { updateNavigation(failureState(error), "ERROR"); false }
    }

    private fun accept(sample: LocationData?) {
        if (sample == null) {
            invalidateLocation(SdkFeatureState(SdkAvailability.NO_DATA, "LOCATION_NULL"), "LOCATION_NULL"); return
        }
        if (sample.timestampMillis > 0 && sample.timestampMillis <= lastTimestamp) return
        val age = System.currentTimeMillis() - sample.timestampMillis
        if (!sample.latitude.isFinite() || !sample.longitude.isFinite() || sample.latitude !in -90.0..90.0 ||
            sample.longitude !in -180.0..180.0 || sample.timestampMillis <= 0 || age !in -1_000L..FRESHNESS_MS
        ) {
            invalidateLocation(SdkFeatureState(SdkAvailability.STALE, "LOCATION_TIME_OR_COORDINATES"), "LOCATION_STALE_OR_INVALID"); return
        }
        lastTimestamp = sample.timestampMillis
        lastLocation = TourLocation(mode = TourMode.LIVE, latitude = sample.latitude, longitude = sample.longitude,
            accuracyMeters = null, timestampEpochMillis = sample.timestampMillis,
            receivedElapsedRealtimeMillis = SystemClock.elapsedRealtime(), fromLiveUpdate = true)
        fusedState = SdkFeatureState(SdkAvailability.READY, "UPDATED_LOCATION_ACCURACY_UNIT_UNVERIFIED")
        onLocation(lastLocation)
        onFused(fusedState, sample.confidence.takeIf { it.isFinite() })
    }

    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            val now = SystemClock.elapsedRealtime()
            val location = lastLocation
            if (location != null && location.error == null &&
                (now - (location.receivedElapsedRealtimeMillis ?: 0) !in 0..FRESHNESS_MS ||
                    System.currentTimeMillis() - (location.timestampEpochMillis ?: 0) !in -1_000L..FRESHNESS_MS)
            ) invalidateLocation(SdkFeatureState(SdkAvailability.STALE, "LOCATION_EXPIRED"), "LOCATION_STALE")
            if (!navigationReady && navState.availability == SdkAvailability.CHECKING && now - initializedAt >= 12_000) {
                updateNavigation(SdkFeatureState(SdkAvailability.NO_DATA, "NAVIGATION_INITIALIZE_TIMEOUT"), "UNAVAILABLE")
            }
            main.postDelayed(this, 1_000)
        }
    }

    private fun invalidateLocation(state: SdkFeatureState, code: String) {
        fusedState = state
        lastLocation = lastLocation?.copy(error = code) ?: TourLocation(TourMode.LIVE, error = code)
        onLocation(lastLocation)
        onFused(state, null)
    }
    private fun updateNavigation(state: SdkFeatureState, route: String) {
        navState = state; routeState = route; onNavigation(state, route)
    }
    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun onMain(action: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action) }
    override fun close() {
        if (closed) return
        closed = true
        main.removeCallbacks(tick)
        runCatching { fusedListener?.let { fused?.unregisterFusedLocationCallback(it) } }
        runCatching { naviListener?.let { navigation?.removeListener(it) } }
        runCatching { fused?.release() }; runCatching { navigation?.release() }
        fused = null; navigation = null; fusedListener = null; naviListener = null
        navigationReady = false; lastLocation = null
    }
    private companion object { const val FRESHNESS_MS = 15_000L }
}
