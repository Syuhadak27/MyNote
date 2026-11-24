package com.catatan.app.data

import android.content.Context
import android.util.Base64
import java.io.File

/**
 * Penyimpanan media (gambar/rekaman) sebagai file di filesDir/media.
 * HTML catatan hanya menyimpan penunjuk `catatan-media:<nama file>`, sehingga
 * file catatan JSON tetap kecil. Saat ekspor backup, penunjuk ini diubah
 * kembali menjadi data URL agar kompatibel dengan versi web.
 */
object MediaStore {

    const val PREFIX = "catatan-media:"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        runCatching { dir().mkdirs() }
    }

    private fun dir(): File = File(appContext.filesDir, "media")

    /** Simpan data URL, kembalikan penunjuk lokal. */
    fun save(dataUrl: String): String = try {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return dataUrl
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

    fun fileFor(src: String): File? =
        if (src.startsWith(PREFIX)) {
            File(dir(), src.removePrefix(PREFIX)).takeIf { it.isFile }
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
