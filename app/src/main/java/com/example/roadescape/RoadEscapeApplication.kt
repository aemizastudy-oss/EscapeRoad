package com.example.roadescape

import android.app.Application
import android.content.Context
import com.example.roadescape.data.JsonGameRepository
import com.example.roadescape.data.PackageRepository
import com.example.roadescape.data.JsonTourRepository
import com.example.roadescape.platform.createPlatformSources
import com.example.roadescape.platform.sdk.PleosSdkRuntime

class RoadEscapeApplication : Application() {
    val container by lazy { AppContainer(this) }
}

/** One application-owned DataStore instance; no Activity owns a second store. */
class AppContainer(context: Context) {
    val appContext = context.applicationContext
    val sources = createPlatformSources()
    val sdkRuntime by lazy { PleosSdkRuntime(appContext) }
    val tourRepository by lazy { JsonTourRepository.forContext(appContext) }
    val packageRepository = PackageRepository(context.applicationContext)
    val gameRepository = JsonGameRepository.forContext(context.applicationContext)
}
