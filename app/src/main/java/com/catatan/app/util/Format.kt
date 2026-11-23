package com.catatan.app.util

import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/* ============================================================
   Utilitas format — locale id-ID, sama dengan utils.js
   ============================================================ */

private val ID: Locale = Locale("id", "ID")
private const val DAY_MS = 86_400_000L

fun uid(): String = try {
    UUID.randomUUID().toString()
} catch (_: Throwable) {
    "id-" + System.currentTimeMillis().toString(36) + "-" + (0..1_000_000).random().toString(36)
}

fun fmtDate(ts: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", ID)
        .format(java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()))

fun fmtDateLong(ts: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", ID)
        .format(java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()))

fun fmtTime(ts: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("HH.mm", ID)
        .format(java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()))

fun fmtDateTime(ts: Long): String = "${fmtDate(ts)} · ${fmtTime(ts)}"

private fun zoned(ts: Long) =
    java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())

fun relativeTime(ts: Long): String {
    if (ts <= 0) return "Baru saja"
    val diff = System.currentTimeMillis() - ts
    if (diff < 60_000) return "Baru saja"
    if (diff < 3_600_000) return "${diff / 60_000} mnt lalu"
    if (diff < 86_400_000) return "${diff / 3_600_000} jam lalu"
    val days = (diff / DAY_MS).toInt()
    if (days == 1) return "Kemarin"
    if (days < 7) return "$days hari lalu"
    if (days < 30) return "${days / 7} mgg lalu"
    return fmtDate(ts)
}

/** Label waktu ringkas untuk baris catatan. */
fun noteTimeLabel(ts: Long): String {
    if (ts <= 0) return ""
    val now = zoned(System.currentTimeMillis())
    val then = zoned(ts)
    return when {
        then.toLocalDate() == now.toLocalDate() -> fmtTime(ts)
        then.toLocalDate() == now.toLocalDate().minusDays(1) -> "Kemarin"
        then.year == now.year ->
            java.time.format.DateTimeFormatter.ofPattern("d MMM", ID).format(then)
        else -> fmtDate(ts)
    }
}

/** Label grup tanggal: Hari ini, Kemarin, 7 hari terakhir, … */
fun dateGroupLabel(ts: Long?): String {
    if (ts == null || ts <= 0) return "Lebih lama"
    val now = zoned(System.currentTimeMillis())
    val then = zoned(ts)
    val days = ((System.currentTimeMillis() - ts) / DAY_MS).toInt()
    return when {
        then.toLocalDate() == now.toLocalDate() -> "Hari ini"
        days <= 1 -> "Kemarin"
        days < 7 -> "7 hari terakhir"
        days < 30 -> "30 hari terakhir"
        then.year == now.year ->
            java.time.format.DateTimeFormatter.ofPattern("MMMM", ID).format(then)
        else -> java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", ID).format(then)
    }
}

/** Sisa hari di sampah (maks 30 hari). */
fun trashDaysLeft(deletedAt: Long?, days: Int = 30): Int {
    if (deletedAt == null || deletedAt <= 0) return days
    val elapsed = ((System.currentTimeMillis() - deletedAt) / DAY_MS).toInt()
    return max(0, days - elapsed)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    val text = if (value >= 10 || unit == 0) String.format(ID, "%.0f", value)
    else String.format(ID, "%.1f", value)
    return "$text ${units[unit]}"
}

/** Durasi audio → MM:SS */
fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(ID, "%02d:%02d", m, s)
}

/* --- Warna --- */

fun hexToRgb(hex: String): IntArray {
    val clean = hex.removePrefix("#")
    return try {
        if (clean.length == 6 || clean.length == 3) {
            val full = if (clean.length == 3) clean.map { "$it$it" }.joinToString("") else clean
            intArrayOf(
                full.substring(0, 2).toInt(16),
                full.substring(2, 4).toInt(16),
                full.substring(4, 6).toInt(16)
            )
        } else intArrayOf(99, 102, 241)
    } catch (_: Throwable) {
        intArrayOf(99, 102, 241)
    }
}

fun hexToRgba(hex: String, alpha: Float): String {
    val (r, g, b) = hexToRgb(hex)
    return "rgba($r,$g,$b,$alpha)"
}

/** #rrggbb → #rrgggb lebih gelap sebesar percent. */
fun darkenHex(hex: String, percent: Int = 10): String {
    val (r, g, b) = hexToRgb(hex)
    val f = 1 - percent / 100.0
    fun c(v: Int) = (v * f).toInt().coerceIn(0, 255)
    return String.format(ID, "#%02x%02x%02x", c(r), c(g), c(b))
}

/** Kontras teks di atas warna: terang/gelap. */
fun contrastColor(hex: String): String {
    val (r, g, b) = hexToRgb(hex)
    val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return if (lum > 0.62) "#111827" else "#ffffff"
}

fun clamp(value: Int, minV: Int, maxV: Int): Int = max(minV, min(maxV, value))

fun sanitizeFilename(name: String, fallback: String = "catatan"): String {
    val cleaned = name.replace(Regex("""[\\/:*?"<>|]"""), "-")
        .trim().replace(Regex("""\s+"""), " ")
    val cut = cleaned.take(80).trim()
    return if (cut.isEmpty()) fallback else cut
}

fun formatNumber(n: Int): String = n.toString()
