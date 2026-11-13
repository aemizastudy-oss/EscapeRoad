package com.example.roadescape.data

import android.content.Context
import com.example.roadescape.model.LobbyBlock
import com.example.roadescape.model.LobbyLayoutJson
import com.example.roadescape.model.LobbyLayoutProfile
import com.example.roadescape.model.LobbyLayoutSettings
import com.example.roadescape.model.LobbyOffset
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class LobbyLayoutLoadResult(
    val settings: LobbyLayoutSettings,
    val defaults: LobbyLayoutSettings,
    val warning: String? = null,
)

/** UI-only preferences. Never reads or writes either game or tour DataStore. */
class LobbyLayoutRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Read failures never initialize, remove or overwrite the user's local value. */
    fun load(): LobbyLayoutLoadResult = synchronized(storageLock) {
        val warnings = mutableListOf<String>()
        val defaults = try {
            val text = appContext.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            LobbyLayoutJson.parse(text)
        } catch (_: Exception) {
            warnings += "기본 로비 배치 파일을 읽지 못해 위치 조정 없이 표시합니다."
            LobbyLayoutSettings()
        }
        val settings = try {
            preferences.getString(SETTINGS_KEY, null)?.let(LobbyLayoutJson::parse) ?: defaults
        } catch (_: Exception) {
            warnings += "저장된 로비 배치를 읽지 못했습니다. 원본을 보존하고 기본 배치로 표시합니다."
            defaults
        }
        LobbyLayoutLoadResult(settings, defaults, warnings.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }

    suspend fun saveProfile(
        profile: LobbyLayoutProfile,
        offsets: Map<LobbyBlock, LobbyOffset>,
    ): Result<LobbyLayoutSettings> = try {
        // Multiple editor instances must merge against the latest other profile atomically.
        Result.success(withContext(Dispatchers.IO) {
            val saveContext = currentCoroutineContext()
            saveContext.ensureActive()
            synchronized(storageLock) {
                val next = load().settings.withOffsets(profile, offsets)
                val encoded = LobbyLayoutJson.encode(next)
                val contained = preferences.contains(SETTINGS_KEY)
                val previous = preferences.all[SETTINGS_KEY]
                saveContext.ensureActive()
                try {
                    if (!preferences.edit().putString(SETTINGS_KEY, encoded).commit()) {
                        throw IOException("로비 배치를 저장하지 못했습니다. 다시 시도해 주세요.")
                    }
                } catch (error: Exception) {
                    // SharedPreferences updates its memory map even when commit returns false.
                    // Restore the prior key so a later read does not report that failed save as saved.
                    runCatching { restorePrevious(contained, previous) }
                    throw error
                }
                next
            }
        })
    } catch (error: CancellationException) { throw error
    } catch (error: Exception) { Result.failure(error) }

    private fun restorePrevious(contained: Boolean, previous: Any?) {
        val editor = preferences.edit()
        if (!contained) editor.remove(SETTINGS_KEY) else when (previous) {
            is String -> editor.putString(SETTINGS_KEY, previous)
            is Int -> editor.putInt(SETTINGS_KEY, previous)
            is Long -> editor.putLong(SETTINGS_KEY, previous)
            is Float -> editor.putFloat(SETTINGS_KEY, previous)
            is Boolean -> editor.putBoolean(SETTINGS_KEY, previous)
            is Set<*> -> editor.putStringSet(SETTINGS_KEY, previous.filterIsInstance<String>().toSet())
            else -> editor.remove(SETTINGS_KEY)
        }
        editor.commit()
    }

    companion object {
        const val PREFERENCES_NAME = "roadescape-lobby-layout"
        const val SETTINGS_KEY = "settings_json"
        const val ASSET_FILE = "lobby_layout.json"
        private val storageLock = Any()
    }
}
