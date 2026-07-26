package by.mlastovsky.kosht.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface UpdateStatus {

    data object UpToDate : UpdateStatus

    data class Available(
        val versionName: String,
        val downloadUrl: String
    ) : UpdateStatus

    data object Failed : UpdateStatus
}

class UpdateChecker {

    suspend fun check(currentVersionCode: Long): UpdateStatus = withContext(Dispatchers.IO) {
        runCatching {
            val body = fetch(LATEST_RELEASE_URL)
            val assets = JSONObject(body).optJSONArray("assets") ?: return@runCatching UpdateStatus.Failed
            var best: UpdateStatus.Available? = null
            var bestBuild = currentVersionCode
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                val version = parseVersionName(name) ?: continue
                val build = parseBuildNumber(name) ?: continue
                if (build > bestBuild && url.isNotBlank()) {
                    bestBuild = build
                    best = UpdateStatus.Available(versionName = version, downloadUrl = url)
                }
            }
            best ?: UpdateStatus.UpToDate
        }.getOrElse { UpdateStatus.Failed }
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            connection.setRequestProperty("User-Agent", "Kosht")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/mLastovskyy/Kosht/releases/latest"

        private val ASSET_NAME = Regex("""kosht-(\d+)\.(\d+)-release\.apk""")

        fun parseVersionName(assetName: String): String? =
            ASSET_NAME.find(assetName)?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }

        fun parseBuildNumber(assetName: String): Long? =
            ASSET_NAME.find(assetName)?.groupValues?.get(2)?.toLongOrNull()
    }
}
