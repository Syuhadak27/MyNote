package com.catatan.app.data

import android.content.Context
import android.util.Base64
import java.io.File

/**
 * Penyimpanan media (gambar/rekaman) sebagai file di filesDir/<scope>/media.
 * HTML catatan hanya menyimpan penunjuk `catatan-media:<nama file>`, sehingga
 * file catatan JSON tetap kecil. Saat ekspor backup, penunjuk ini diubah
 * kembali menjadi data URL agar kompatibel dengan versi web.
 *
 * Scope mengikuti akun: "media" untuk mode lokal, "media-<uid>" untuk akun.
 */
object MediaStore {

    const val PREFIX = "catatan-media:"

    private lateinit var appContext: Context
    private var dirName = "media"

    private val safeName = Regex("^[A-Za-z0-9._-]+$")

    fun init(context: Context, scopeDirName: String = "media") {
        appContext = context.applicationContext
        dirName = scopeDirName
        runCatching { dir().mkdirs() }
    }

    /** Ganti folder media saat akun/login berubah. */
    fun setScope(scopeDirName: String) {
        dirName = scopeDirName.ifBlank { "media" }
        runCatching { dir().mkdirs() }
    }

    private fun dir(): File = File(appContext.filesDir, dirName)

    /* ----------------------------------------------------------
       Akses file (dipakai sinkronisasi)
       ---------------------------------------------------------- */

    fun listNames(): List<String> = runCatching {
        dir().listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
    }.getOrDefault(emptyList())

    fun fileNamed(name: String): File? =
        if (name.matches(safeName)) File(dir(), name).takeIf { it.isFile } else null

    fun saveBytes(name: String, bytes: ByteArray): File? {
        if (!name.matches(safeName)) return null
        return runCatching {
            File(dir(), name).apply { writeBytes(bytes) }
        }.getOrNull()
    }

    /** Hapus semua file media pada scope saat ini. */
    fun clearAll() {
        runCatching {
            dir().listFiles()?.forEach { file -> if (file.isFile) file.delete() }
        }
    }

    /** Salin media dari folder scope lain (mis. lokal -> akun saat login). */
    fun copyAllFrom(sourceDirName: String) {
        if (sourceDirName == dirName) return
        runCatching {
            File(appContext.filesDir, sourceDirName).listFiles()?.forEach { file ->
                if (file.isFile && file.name.matches(safeName)) {
                    File(dir(), file.name).writeBytes(file.readBytes())
                }
            }
        }
    }

    /* ----------------------------------------------------------
       Konversi data URL <-> penunjuk lokal
       ---------------------------------------------------------- */

    /** Simpan data URL, kembalikan penunjuk lokal. */
    fun save(dataUrl: String): String {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return dataUrl
        return try {
            val meta = dataUrl.substring(0, comma).lowercase()
            val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
            val ext = when {
                meta.contains("image/png") -> "png"
                meta.contains("image/") -> "jpg"
                meta.contains("audio/mp4") || meta.contains("audio/mpeg") -> "m4a"
                meta.contains("audio/") -> "aac"
                else -> "bin"
            }
            val name = "m-${System.currentTimeMillis()}-${(Math.random() * 1_000_000).toInt()}.$ext"
            File(dir(), name).writeBytes(bytes)
            PREFIX + name
        } catch (_: Exception) {
            dataUrl
        }
    }

    fun fileFor(src: String): File? =
        if (src.startsWith(PREFIX)) {
            fileNamed(src.removePrefix(PREFIX))
        } else null

    fun resolve(src: String): String = fileFor(src)?.absolutePath ?: src

    /** Penunjuk lokal -> data URL (untuk backup). */
    fun toDataUrl(src: String): String? {
        val file = fileFor(src) ?: return null
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            else -> "application/octet-stream"
        }
        return try {
            "data:$mime;base64,${Base64.encodeToString(file.readBytes(), Base64.DEFAULT)}"
        } catch (_: Exception) {
            null
        }
    }

    /** Ganti semua src pada HTML: lokal -> data URL (ekspor). */
    fun exportContent(content: String): String = replaceSources(content) { src ->
        if (src.startsWith(PREFIX)) toDataUrl(src) ?: src else src
    }

    /** Ganti semua data URL pada HTML -> file lokal (impor). */
    fun importContent(content: String): String = replaceSources(content) { src ->
        if (src.startsWith("data:image/") || src.startsWith("data:audio/")) save(src) else src
    }

    private fun replaceSources(content: String, transform: (String) -> String): String =
        Regex("""(src=["'])([^"']+)(["'])""").replace(content) { match ->
            match.groupValues[1] + transform(match.groupValues[2]) + match.groupValues[3]
        }

    /** Hapus file media yang sudah tidak dirujuk catatan mana pun. */
    fun prune(notes: List<Note>) {
        val used = mutableSetOf<String>()
        val ref = Regex("$PREFIX([A-Za-z0-9._-]+)")
        notes.forEach { note -> ref.findAll(note.content).forEach { used.add(it.groupValues[1]) } }
        runCatching {
            dir().listFiles()?.forEach { file -> if (file.name !in used) file.delete() }
        }
    }
}
