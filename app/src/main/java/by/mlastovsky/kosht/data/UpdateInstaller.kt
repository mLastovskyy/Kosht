package by.mlastovsky.kosht.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK into the app's own cache and hands it to the system
 * package installer. No browser, no Downloads folder: the file never leaves
 * app-private storage and is deleted as soon as the install session owns it.
 */
class UpdateInstaller(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Android requires a one-time "install unknown apps" grant per source app;
     * without it [install] would fail silently.
     */
    fun canInstall(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    /** Settings page where the user grants that permission to Kosht. */
    fun unknownSourcesIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${appContext.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Streams the APK to app cache, reporting 0..100 as it goes (-1 while the
     * server has not told us the size). Returns null if the download failed.
     */
    suspend fun download(url: String, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val target = freshApkFile()
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    // GitHub rejects requests without a User-Agent.
                    setRequestProperty("User-Agent", "Kosht")
                }
                try {
                    val total = connection.contentLengthLong
                    onProgress(if (total > 0) 0 else UNKNOWN_PROGRESS)
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            var reported = -1
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (total <= 0) continue
                                val percent = (copied * 100 / total).toInt()
                                if (percent != reported) {
                                    reported = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                target.takeIf { it.length() > 0 } ?: run { target.delete(); null }
            } catch (cancelled: CancellationException) {
                target.delete()
                throw cancelled
            } catch (failure: Exception) {
                target.delete()
                null
            }
        }

    /**
     * Android refuses to replace an app with a build signed by another key.
     * Catching it here turns a cryptic system error into a clear explanation
     * — and it happens for real whenever a release is built without the
     * signing secrets and falls back to a throwaway debug key.
     */
    fun signedWithSameKey(apk: File): Boolean = runCatching {
        val installed = certificates(appContext.packageName) ?: return true
        val downloaded = archiveCertificates(apk.absolutePath) ?: return true
        installed.any { mine -> downloaded.any { theirs -> mine.contentEquals(theirs) } }
    }.getOrDefault(true)

    private fun certificates(packageName: String): List<ByteArray>? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return appContext.packageManager.getPackageInfo(packageName, flags).certificates()
    }

    private fun archiveCertificates(path: String): List<ByteArray>? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return appContext.packageManager.getPackageArchiveInfo(path, flags)?.certificates()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.certificates(): List<ByteArray>? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
            signingInfo?.let { info ->
                if (info.hasMultipleSigners()) {
                    info.apkContentsSigners
                } else {
                    info.signingCertificateHistory
                }
            }

        else -> signatures
    }?.map { it.toByteArray() }

    /**
     * Copies the APK into a package installer session and commits it. The
     * system takes over from here: it either shows its confirmation sheet or,
     * once Kosht is its own installer of record, updates in place.
     */
    suspend fun install(apk: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val installer = appContext.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(appContext.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Silent when Kosht installed the previous build itself;
                    // otherwise the system still asks, which we handle.
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(APK_NAME, 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                session.commit(InstallResultReceiver.statusSender(appContext, sessionId))
            }
            // The session owns the bytes now; nothing needs to stay on disk.
            apk.delete()
            true
        }.getOrElse {
            apk.delete()
            false
        }
    }

    /** Drops any half-downloaded APK left by an interrupted attempt. */
    private fun freshApkFile(): File {
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, APK_NAME)
    }

    companion object {
        const val UNKNOWN_PROGRESS = -1
        private const val APK_NAME = "kosht-update.apk"
    }
}
