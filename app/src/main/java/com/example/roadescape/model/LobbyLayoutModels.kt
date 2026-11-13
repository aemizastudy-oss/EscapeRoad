package com.example.roadescape.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

@Serializable
enum class LobbyBlock { WELCOME, LOGO, TAGLINE, TITLE, DESCRIPTION, ACTIONS, RESUME }

@Serializable
enum class LobbyLayoutProfile { WIDE, COMPACT }

/** Displacement from the existing native layout, in dp rather than screen pixels. */
@Serializable
data class LobbyOffset(val xDp: Float = 0f, val yDp: Float = 0f) {
    init {
        require(xDp.isFinite() && yDp.isFinite()) { "로비 위치는 유한한 숫자여야 합니다." }
        require(abs(xDp) <= MAX_ABSOLUTE_DP && abs(yDp) <= MAX_ABSOLUTE_DP) {
            "로비 위치는 ±4096dp 범위여야 합니다."
        }
    }

    companion object { const val MAX_ABSOLUTE_DP = 4096f }
}

@Serializable
data class LobbyLayoutSettings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val wide: Map<LobbyBlock, LobbyOffset> = emptyMap(),
    val compact: Map<LobbyBlock, LobbyOffset> = emptyMap(),
) {
    init { require(schemaVersion == CURRENT_SCHEMA_VERSION) { "지원하지 않는 로비 배치 형식입니다." } }

    fun offsets(profile: LobbyLayoutProfile): Map<LobbyBlock, LobbyOffset> = when (profile) {
        LobbyLayoutProfile.WIDE -> wide
        LobbyLayoutProfile.COMPACT -> compact
    }

    fun withOffsets(profile: LobbyLayoutProfile, offsets: Map<LobbyBlock, LobbyOffset>): LobbyLayoutSettings =
        when (profile) {
            LobbyLayoutProfile.WIDE -> copy(wide = offsets.toMap(), compact = compact.toMap())
            LobbyLayoutProfile.COMPACT -> copy(wide = wide.toMap(), compact = offsets.toMap())
        }

    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

/** Same format for APK defaults, local preferences and user-copied export JSON. */
object LobbyLayoutJson {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun parse(text: String): LobbyLayoutSettings {
        val root = json.parseToJsonElement(text).jsonObject
        val version = root["schemaVersion"]?.jsonPrimitive
        require(version != null && !version.isString && version.intOrNull == LobbyLayoutSettings.CURRENT_SCHEMA_VERSION) {
            "로비 배치의 schemaVersion 1이 필요합니다."
        }
        return json.decodeFromString<LobbyLayoutSettings>(text)
    }

    fun encode(settings: LobbyLayoutSettings): String = json.encodeToString(LobbyLayoutSettings.serializer(), settings)
}
