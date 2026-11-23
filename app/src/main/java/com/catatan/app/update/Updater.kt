package com.catatan.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/* ============================================================
   Updater — periksa & unduh APK dari GitHub Releases
   ============================================================ */

@Serializable
data class GhAsset(
    val name: String = "",
    val browser_download_url: String = ""
)

@Serializable
data class GhRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val published_at: String? = null,
    val assets: List<GhAsset> = emptyList()
)

data class UpdateInfo(
    val version: String,
    val tagName: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val publishedAt: String
)

sealed class CheckResult {
    data class Available(val info: UpdateInfo) : CheckResult()
    object UpToDate : CheckResult()
    object Error : CheckResult()
}

object Updater {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Bandingkan versi: 1.1.0 > 1.0.0 */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String): List<Int> =
            v.removePrefix("v").trim()
                .split(Regex("[.\\-+]"))
                .map { it.toIntOrNull() ?: 0 }

        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun check(url: String, currentVersion: String): CheckResult =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "Catatan-Updater")
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                val code = conn.responseCode
                if (code != 200) {
                    conn.disconnect()
                    return@withContext CheckResult.Error
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val release = json.decodeFromString(GhRelease.serializer(), text)
                if (release.draft) return@withContext CheckResult.UpToDate

                val version = release.tag_name.removePrefix("v")
                if (!isNewer(version, currentVersion)) return@withContext CheckResult.UpToDate

                val apk = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                } ?: return@withContext CheckResult.UpToDate

                CheckResult.Available(
                    UpdateInfo(
                        version = version,
                        tagName = release.tag_name,
                        notes = release.body ?: "",
                        apkUrl = apk.browser_download_url,
                        apkName = apk.name,
                        publishedAt = release.published_at ?: ""
                    )
                )
            } catch (_: Throwable) {
                CheckResult.Error
            }
        }

    /** Unduh APK ke filesDir/updates/. Mengembalikan file atau null. */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            val target = File(dir, "catatan-update.apk")
            if (target.exists()) target.delete()

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Catatan-Updater")
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return@withContext null
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } > 0) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
            onProgress(1f)
            if (target.length() > 0) target else null
        } catch (_: Throwable) {
            null
        }
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /** Luncurkan installer sistem untuk APK. */
    fun install(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
