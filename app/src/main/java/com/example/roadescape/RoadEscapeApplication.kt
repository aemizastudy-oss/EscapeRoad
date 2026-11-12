package com.example.roadescape

import android.app.Application
import android.content.Context
import com.example.roadescape.data.JsonGameRepository
import com.example.roadescape.data.PackageRepository
import com.example.roadescape.platform.createPlatformSources

class RoadEscapeApplication : Application() {
    val container by lazy { AppContainer(this) }
}

/** One application-owned DataStore instance; no Activity owns a second store. */
class AppContainer(context: Context) {
    val sources = createPlatformSources()
    val packageRepository = PackageRepository(context.applicationContext)
    val gameRepository = JsonGameRepository.forContext(context.applicationContext)
}
