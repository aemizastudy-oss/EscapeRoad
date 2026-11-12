package com.example.roadescape.data

import android.content.Context
import com.example.roadescape.game.ContentValidator
import com.example.roadescape.model.GamePackage
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PackageLoadException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Static bundled content only; no location, network or Vehicle SDK access. */
class PackageRepository(private val context: Context) {
    private val json = Json { classDiscriminator = "type" }

    suspend fun load(): GamePackage = withContext(Dispatchers.IO) {
        try {
            val text = context.assets.open("demo_package.json").bufferedReader().use { it.readText() }
            val content = json.decodeFromString<GamePackage>(text)
            val problems = ContentValidator.validate(content)
            if (problems.isNotEmpty()) throw PackageLoadException(problems.joinToString("\n"))
            content
        } catch (error: CancellationException) {
            throw error
        } catch (error: PackageLoadException) {
            throw error
        } catch (error: Exception) {
            throw PackageLoadException("테스트 콘텐츠를 불러오지 못했습니다.", error)
        }
    }
}
