package com.team.carescape.drive

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide voice narration on/off (the toggle in the app's top-right corner). Kept across restarts.
 * Off silences narration immediately and drops every line that comes up while it is off; nothing is
 * saved for later, so switching back on never plays an old line late — only lines that come up after.
 * BGM is not affected.
 */
object NarrationSwitch {
    private const val PREFS = "drive_chapter"
    private const val KEY = "narration_on"
    private var flow: MutableStateFlow<Boolean>? = null

    fun state(context: Context): StateFlow<Boolean> = flowFor(context).asStateFlow()
    fun isOn(context: Context): Boolean = flowFor(context).value

    fun set(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        flowFor(context).value = on
    }

    @Synchronized
    private fun flowFor(context: Context): MutableStateFlow<Boolean> = flow ?: MutableStateFlow(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true),
    ).also { flow = it }
}
