package com.team.carescape.ui

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.PolylineOverlay
import com.naver.maps.map.util.MarkerIcons
import com.team.carescape.BuildConfig
import com.team.carescape.travel.GuideStop
import com.team.carescape.travel.Place
import kotlinx.coroutines.delay

/** Only revealed stops enter this state; map rendering does not own game or trip data. */
internal data class TravelMapState(
    val stops: List<GuideStop>,
    val dayStops: List<Int>,
    val places: List<Place>,
    val selectedStop: Int,
    val selectedPlace: Place?,
    val nearby: Boolean,
    val scheduled: Boolean,
    val placeColor: Int,
)

@Composable
internal fun TravelNaverMap(
    state: TravelMapState,
    onSelectStop: (Int) -> Unit,
    onSelectPlace: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (BuildConfig.NAVER_MAP_KEY_ID.isBlank()) {
        MapNotice("네이버 지도 키 설정 대기", "지도 설정을 마치면 경유지와 주변 장소가 표시됩니다.",
            modifier.testTag("travel-map-pending"))
        return
    }
    var retry by remember { mutableStateOf(0) }
    key(retry) { NativeTravelMap(state, onSelectStop, onSelectPlace, { retry++ }, modifier) }
}

@Composable
private fun NativeTravelMap(
    state: TravelMapState,
    onSelectStop: (Int) -> Unit,
    onSelectPlace: (Place) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val registry = LocalSavedStateRegistryOwner.current.savedStateRegistry
    val latestSelectStop by rememberUpdatedState(onSelectStop)
    val latestSelectPlace by rememberUpdatedState(onSelectPlace)
    val holder = remember(context, lifecycleOwner, registry) { TravelMapHolder(context) }
    var timedOut by remember(holder) { mutableStateOf(false) }
    val stateKey = "travel-naver-map"

    DisposableEffect(holder, lifecycleOwner, registry) {
        val view = holder.view
        if (view == null) return@DisposableEffect onDispose { holder.destroy() }
        val sdk = NaverMapSdk.getInstance(context)
        val previousListener = sdk.onAuthFailedListener
        val listener = NaverMapSdk.OnAuthFailedListener { exception ->
            holder.main.post {
                if (!holder.closed) holder.error = when (exception.errorCode) {
                    "401" -> "지도 인증에 실패했습니다. 키 ID와 Android 앱 등록을 확인해 주세요."
                    "429" -> "지도 사용 설정 또는 한도를 확인해 주세요."
                    else -> "지도 인증에 실패했습니다. Maps 설정을 확인해 주세요."
                }
            }
        }
        sdk.onAuthFailedListener = listener
        holder.create(registry.consumeRestoredStateForKey(stateKey))
        registry.registerSavedStateProvider(stateKey) { Bundle().also(holder::save) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.start()
                Lifecycle.Event.ON_RESUME -> holder.resume()
                Lifecycle.Event.ON_PAUSE -> holder.pause()
                Lifecycle.Event.ON_STOP -> holder.stop()
                Lifecycle.Event.ON_DESTROY -> holder.destroy()
                else -> Unit
            }
        }
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { if (!holder.closed) view.onLowMemory() }
            override fun onTrimMemory(level: Int) { if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) onLowMemory() }
        }
        context.applicationContext.registerComponentCallbacks(callbacks)
        lifecycleOwner.lifecycle.addObserver(observer)
        view.getMapAsync { map ->
            if (!holder.closed) {
                holder.map = map
                map.uiSettings.isLocationButtonEnabled = false
                map.uiSettings.isCompassEnabled = true
                map.uiSettings.isZoomControlEnabled = true
                map.addOnLoadListener(holder.loadedListener)
                holder.loaded = map.isLoaded
            }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            registry.unregisterSavedStateProvider(stateKey)
            if (sdk.onAuthFailedListener === listener) sdk.onAuthFailedListener = previousListener
            holder.destroy()
        }
    }
    LaunchedEffect(holder) {
        delay(15_000)
        if (!holder.loaded && holder.error == null) timedOut = true
    }
    val readyMap = holder.map
    SideEffect {
        if (readyMap != null) holder.render(state, { latestSelectStop(it) }, { latestSelectPlace(it) })
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        holder.view?.let { view ->
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().testTag("travel-naver-map"))
        }
        val problem = holder.error ?: if (timedOut && !holder.loaded) "지도를 불러오지 못했습니다. 인터넷 연결을 확인해 주세요." else null
        if (problem != null) {
            MapNotice("지도 연결 확인", problem, Modifier.fillMaxSize().testTag("travel-map-error"), onRetry)
        } else if (!holder.loaded) {
            MapNotice("네이버 지도를 불러오는 중", "잠시만 기다려 주세요.", Modifier.fillMaxSize().testTag("travel-map-loading"))
        }
    }
}

@Composable
private fun MapNotice(title: String, body: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.background(Esc.panel).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text(title, style = Esc.nanum(24), textAlign = TextAlign.Center)
        Text(body, style = Esc.noto(16, Esc.creamSoft), textAlign = TextAlign.Center)
        if (onRetry != null) OutlineButton("지도 다시 불러오기", onRetry, minHeight = 48.dp, fontSize = 16)
    }
}

/** One composition owns the view, listeners and overlays and disposes them together. */
private class TravelMapHolder(context: Context) {
    val main = Handler(Looper.getMainLooper())
    var error by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
    var map by mutableStateOf<NaverMap?>(null)
    var closed = false
        private set
    private var created = false
    private var started = false
    private var resumed = false
    private var rendered: TravelMapState? = null
    private var markers = emptyList<Marker>()
    private var lines = emptyList<PolylineOverlay>()
    // The app renders its theme in Compose; SDK widgets require their own AppCompat context.
    val view: MapView? = try { MapView(ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)) }
        catch (_: Exception) { error = "이 기기에서 네이버 지도를 시작하지 못했습니다."; null }
        catch (_: LinkageError) { error = "이 기기에서 네이버 지도 라이브러리를 사용할 수 없습니다."; null }
    val loadedListener = NaverMap.OnLoadListener { if (!closed) loaded = true }

    fun create(saved: Bundle?) { if (!created && !closed) { view?.onCreate(saved); created = true } }
    fun start() { if (created && !started && !closed) { view?.onStart(); started = true } }
    fun resume() { if (created && !resumed && !closed) { start(); view?.onResume(); resumed = true } }
    fun pause() { if (resumed) { view?.onPause(); resumed = false } }
    fun stop() { pause(); if (started) { view?.onStop(); started = false } }
    fun save(bundle: Bundle) { if (created && !closed) view?.onSaveInstanceState(bundle) }
    fun destroy() {
        if (closed) return
        stop()
        closed = true
        markers.forEach { it.map = null }
        lines.forEach { it.map = null }
        markers = emptyList()
        lines = emptyList()
        map?.removeOnLoadListener(loadedListener)
        if (created) view?.onDestroy()
        map = null
        main.removeCallbacksAndMessages(null)
    }

    fun render(state: TravelMapState, selectStop: (Int) -> Unit, selectPlace: (Place) -> Unit) {
        val map = map ?: return
        if (closed || state == rendered) return
        val old = rendered
        markers.forEach { it.map = null }
        lines.forEach { it.map = null }
        val route = state.stops.map { LatLng(it.lat, it.lng) }
        val today = state.stops.filter { it.index in state.dayStops }.map { LatLng(it.lat, it.lng) }
        lines = buildList {
            fun addLine(points: List<LatLng>, tint: Int, pixels: Int) {
                if (points.size >= 2) add(PolylineOverlay().apply {
                    coords = points; color = tint; width = pixels; this.map = map
                })
            }
            addLine(route, android.graphics.Color.rgb(185, 168, 130), 5)
            if (state.scheduled) addLine(today, android.graphics.Color.rgb(139, 39, 39), 8)
        }
        markers = buildList {
            state.stops.forEach { stop ->
                add(Marker().apply {
                    position = LatLng(stop.lat, stop.lng)
                    captionText = "${stop.index + 1}. ${stop.name}"
                    icon = MarkerIcons.BLACK
                    iconTintColor = if (stop.index == state.selectedStop) android.graphics.Color.rgb(190, 143, 44)
                        else android.graphics.Color.rgb(139, 39, 39)
                    zIndex = 5
                    setOnClickListener { if (!closed) selectStop(stop.index); true }
                    this.map = map
                })
            }
            state.places.forEach { place ->
                if (place.lat == null || place.lng == null) return@forEach
                add(Marker().apply {
                    position = LatLng(place.lat, place.lng)
                    captionText = place.name
                    icon = MarkerIcons.BLACK
                    iconTintColor = if (place == state.selectedPlace) android.graphics.Color.DKGRAY else state.placeColor
                    zIndex = if (place == state.selectedPlace) 10 else 1
                    setOnClickListener { if (!closed) selectPlace(place); true }
                    this.map = map
                })
            }
        }
        val focus = state.stops.firstOrNull { it.index == state.selectedStop }
        val selected = state.selectedPlace?.takeIf { it in state.places }
        if (selected != null && selected != old?.selectedPlace) {
            map.moveCamera(CameraUpdate.scrollTo(LatLng(selected.lat!!, selected.lng!!)))
        } else if (old == null || state.stops != old.stops || state.places != old.places ||
            state.nearby != old.nearby || state.selectedStop != old.selectedStop) {
            val points = if (state.nearby && focus != null) listOf(LatLng(focus.lat, focus.lng)) +
                state.places.map { LatLng(it.lat!!, it.lng!!) } else route + state.places.map { LatLng(it.lat!!, it.lng!!) }
            if (points.isNotEmpty()) map.moveCamera(if (points.distinct().size > 1)
                CameraUpdate.fitBounds(LatLngBounds.from(points), 70) else CameraUpdate.scrollAndZoomTo(points.first(), 15.0))
        }
        rendered = state
    }
}
