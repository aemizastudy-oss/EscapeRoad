package com.team.carescape.drive

import android.content.Context
import com.team.carescape.game.TourCatalog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Driving lines per leg (assets/drive_lines.json): short destination info spoken to the driver. */
@Serializable
data class DriveScript(val legs: List<ScriptLeg>) {
    @Serializable
    data class ScriptLeg(val chapterIndex: Int, val lines: List<ScriptLine>)

    @Serializable
    data class ScriptLine(
        val id: String,
        val text: String,
        @SerialName("at") val atFraction: Double? = null,
        val atRemainingM: Double? = null,
    )

    companion object {
        const val ASSET = "drive_lines.json"
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): DriveScript = json.decodeFromString(serializer(), text)
        fun load(context: Context): DriveScript? =
            runCatching { parse(context.assets.open(ASSET).bufferedReader().use { it.readText() }) }.getOrNull()
    }
}

/**
 * Legs come from the place coordinates in [TourCatalog] and the driving lines in [DriveScript].
 * The lines are about the destination, not the case story; recorded files named `voice_<id>` play as-is.
 */
object DriveCourse {
    fun plan(chapterIndex: Int, script: DriveScript?): LegPlan? {
        val waypoint = TourCatalog.checkpoints.getOrNull(chapterIndex) ?: return null
        val lat = waypoint.latitude ?: return null
        val lon = waypoint.longitude ?: return null
        val lines = script?.legs?.firstOrNull { it.chapterIndex == chapterIndex }?.lines.orEmpty()
            .map { LegLine(it.id, it.text, it.atFraction, it.atRemainingM) }
        return LegPlan(chapterIndex, waypoint.name, lat, lon, waypoint.radiusMeters, lines)
    }
}
