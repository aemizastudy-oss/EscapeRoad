package com.team.carescape.drive

import android.content.Context

/** Release builds never drive the car: the demo drive exists only in debug builds. */
internal object DemoDrive {
    fun available(context: Context): Boolean = false
    fun start(context: Context): Pair<Double, Double>? = null
    fun drive(context: Context, chapterIndex: Int, onFix: (Double, Double) -> Unit, onDone: () -> Unit): AutoCloseable? = null
}
