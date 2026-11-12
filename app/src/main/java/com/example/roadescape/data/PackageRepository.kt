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

    suspend fun load(packageId: String? = null): GamePackage = withContext(Dispatchers.IO) {
        try {
            val asset = when (packageId) {
                null, "demo_package" -> "demo_package.json"
                "suwon_tour_preview" -> "suwon_tour_package.json"
                else -> "demo_package.json"
            }
            val text = context.assets.open(asset).bufferedReader().use { it.readText() }
            val content = json.decodeFromString<GamePackage>(text)
            if (packageId != null && content.id != packageId) throw PackageLoadException("지원하지 않는 저장 코스입니다. 기존 저장은 보존합니다.")
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
