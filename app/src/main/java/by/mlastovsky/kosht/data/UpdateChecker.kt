package by.mlastovsky.kosht.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Outcome of a manual "is there a newer build?" check. */
sealed interface UpdateStatus {

    data object UpToDate : UpdateStatus

    data class Available(
        val versionName: String,
        val downloadUrl: String
    ) : UpdateStatus

    /** No network, GitHub unreachable or a release without an APK. */
    data object Failed : UpdateStatus
}

/**
 * Compares the installed build with the latest GitHub release. Releases
 * are tagged by CI run number, so the actual version comes from the APK
 * asset name (kosht-2.47-release.apk) — its build part is the commit
 * count, which is exactly this app's versionCode.
 */
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
            // GitHub rejects requests without a User-Agent.
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

        /** "kosht-2.47-release.apk" -> "2.47" */
        fun parseVersionName(assetName: String): String? =
            ASSET_NAME.find(assetName)?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }

        /** "kosht-2.47-release.apk" -> 47, the commit count behind versionCode. */
        fun parseBuildNumber(assetName: String): Long? =
            ASSET_NAME.find(assetName)?.groupValues?.get(2)?.toLongOrNull()
    }
}
