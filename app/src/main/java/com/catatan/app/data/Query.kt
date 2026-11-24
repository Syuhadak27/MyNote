package com.catatan.app.data

import com.catatan.app.util.dateGroupLabel
import java.time.LocalDate
import java.time.ZoneId

/* ============================================================
   Query — pencarian, filter, sort, grup (port dari app.js)
   ============================================================ */

data class ParsedQuery(
    val text: String = "",
    val tags: List<String> = emptyList(),
    val isFilters: List<String> = emptyList(),
    val hasFilters: List<String> = emptyList(),
    val before: Long? = null,
    val after: Long? = null
)

data class UiFilters(
    val favorite: Boolean = false,
    val checklist: Boolean = false,
    val image: Boolean = false,
    val audio: Boolean = false,
    val archived: Boolean = false
) {
    val isEmpty: Boolean
        get() = !favorite && !checklist && !image && !audio && !archived
}

object Query {

    /* ----------------------------------------------------------
       Tokenisasi query: "frasa", #tag, is:x, has:x, before:, after:
       ---------------------------------------------------------- */

    fun parse(raw: String): ParsedQuery {
        val input = raw.trim()
        if (input.isEmpty()) return ParsedQuery()

        val tokens = Regex(""""([^"]+)"|(\S+)""")
            .findAll(input)
            .map { m ->
                val quoted = m.groupValues[1]
                if (quoted.isNotEmpty()) "\"$quoted\"" else m.groupValues[2]
            }
            .toList()

        val textParts = mutableListOf<String>()
        val tags = mutableListOf<String>()
        val isFilters = mutableListOf<String>()
        val hasFilters = mutableListOf<String>()
        var before: Long? = null
        var after: Long? = null

        for (tok in tokens) {
            when {
                tok.length >= 2 && tok.startsWith("\"") && tok.endsWith("\"") ->
                    textParts.add(tok.substring(1, tok.length - 1))

                tok.startsWith("#") && tok.length > 1 ->
                    tags.add(tok.substring(1).lowercase())

                tok.length > 3 && tok.startsWith("is:", ignoreCase = true) ->
                    isFilters.add(tok.substring(3).lowercase())

                tok.length > 4 && tok.startsWith("has:", ignoreCase = true) ->
                    hasFilters.add(tok.substring(4).lowercase())

                tok.length > 7 && tok.startsWith("before:", ignoreCase = true) ->
                    parseDate(tok.substring(7))?.let { before = endOfDay(it) }

                tok.length > 6 && tok.startsWith("after:", ignoreCase = true) ->
                    parseDate(tok.substring(6))?.let { after = startOfDay(it) }

                else -> textParts.add(tok)
            }
        }

        return ParsedQuery(
            text = textParts.joinToString(" ").trim(),
            tags = tags.distinct(),
            isFilters = isFilters,
            hasFilters = hasFilters,
            before = before,
            after = after
        )
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value.trim())
    } catch (_: Throwable) {
        null
    }

    private fun startOfDay(d: LocalDate): Long =
        d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun endOfDay(d: LocalDate): Long =
        d.atTime(23, 59, 59, 999_999_999).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /* ----------------------------------------------------------
       Filter dasar per halaman
       ---------------------------------------------------------- */

    fun baseNotes(
        notes: List<Note>,
        view: String,              // notes | favorites | archive | trash | folder | tag
        folderId: String? = null,
        tag: String? = null
    ): List<Note> = when (view) {
        "trash" -> notes.filter { it.isDeleted }
        "archive" -> notes.filter { it.isArchived && !it.isDeleted }
        "favorites" -> notes.filter { it.isFavorite && !it.isDeleted && !it.isArchived }
        "folder" -> notes.filter {
            !it.isDeleted && !it.isArchived && it.categoryId == folderId
        }
        "tag" -> notes.filter {
            !it.isDeleted && !it.isArchived &&
                (tag?.let { t -> HtmlCodec.noteTags(it).contains(t) } == true)
        }
        else -> notes.filter { !it.isDeleted && !it.isArchived }
    }

    fun quickCounts(notes: List<Note>): Map<String, Int> {
        var checklist = 0
        var image = 0
        var audio = 0
        for (n in notes) {
            if (HtmlCodec.hasChecklist(n.content)) checklist++
            if (HtmlCodec.hasImage(n.content)) image++
            if (HtmlCodec.hasAudio(n.content)) audio++
        }
        return mapOf(
            "all" to notes.size,
            "checklist" to checklist,
            "image" to image,
            "audio" to audio
        )
    }

    fun applyQuickFilter(notes: List<Note>, quick: String): List<Note> = when (quick) {
        "checklist" -> notes.filter { HtmlCodec.hasChecklist(it.content) }
        "image" -> notes.filter { HtmlCodec.hasImage(it.content) }
        "audio" -> notes.filter { HtmlCodec.hasAudio(it.content) }
        else -> notes
    }

    /* ----------------------------------------------------------
       Pencarian
       ---------------------------------------------------------- */

    fun search(
        notes: List<Note>,
        rawQuery: String,
        ui: UiFilters,
        folderId: String?,
        categories: List<Category>
    ): List<Note> {
        val q = parse(rawQuery)
        var pool = notes

        val wantsTrash = q.isFilters.contains("trash")
        if (wantsTrash) pool = pool.filter { it.isDeleted }
        if (q.isFilters.contains("locked")) return emptyList()

        if (ui.archived || q.isFilters.contains("archived")) {
            pool = pool.filter { it.isArchived && !it.isDeleted }
        } else if (!wantsTrash) {
            pool = pool.filter { !it.isArchived }
        }

        if (ui.favorite || q.isFilters.contains("favorite")) pool = pool.filter { it.isFavorite }
        if (q.isFilters.contains("pinned")) pool = pool.filter { it.isPinned }

        if (ui.checklist || "checklist" in q.hasFilters)
            pool = pool.filter { HtmlCodec.hasChecklist(it.content) }
        if (ui.image || "image" in q.hasFilters)
            pool = pool.filter { HtmlCodec.hasImage(it.content) }
        if (ui.audio || "audio" in q.hasFilters)
            pool = pool.filter { HtmlCodec.hasAudio(it.content) }
        if ("link" in q.hasFilters) pool = pool.filter { HtmlCodec.hasLink(it.content) }
        if ("table" in q.hasFilters) pool = pool.filter { HtmlCodec.hasTable(it.content) }

        if (folderId != null) pool = pool.filter { it.categoryId == folderId }

        if (q.tags.isNotEmpty()) {
            pool = pool.filter { note ->
                val noteTags = HtmlCodec.noteTags(note)
                q.tags.all { it in noteTags }
            }
        }

        q.before?.let { boundary -> pool = pool.filter { it.updatedAt <= boundary } }
        q.after?.let { boundary -> pool = pool.filter { it.updatedAt >= boundary } }

        val words = q.text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isNotEmpty()) {
            val folderName = categories.associate { it.id to it.name.lowercase() }
            pool = pool.filter { note ->
                val haystack = buildString {
                    append(note.title.lowercase())
                    append(' ')
                    append(note.plainText.lowercase())
                    append(' ')
                    append(HtmlCodec.noteTags(note).joinToString(" ").lowercase())
                    note.categoryId?.let {
                        append(' ')
                        append(folderName[it] ?: "")
                    }
                }
                words.all { it in haystack }
            }
        }

        return pool
    }

    /* ----------------------------------------------------------
       Pengurutan
       ---------------------------------------------------------- */

    fun sortNotes(notes: List<Note>, sortBy: String, trashView: Boolean = false): List<Note> {
        val timeOf: (Note) -> Long = { n ->
            if (trashView) (n.deletedAt ?: n.updatedAt) else n.updatedAt
        }
        val comparator = when (sortBy) {
            "title" -> compareBy<Note> { it.title.lowercase() }
            "createdAt" -> compareByDescending<Note> { it.createdAt }
            "oldest" -> compareBy<Note> { it.createdAt }
            else -> compareByDescending<Note> { timeOf(it) }
        }
        val pinned = if (trashView) emptyList() else notes.filter { it.isPinned }
        val rest = if (trashView) notes else notes.filter { !it.isPinned }
        return pinned.sortedWith(comparator) + rest.sortedWith(comparator)
    }

    /* ----------------------------------------------------------
       Pengelompokan tanggal
       ---------------------------------------------------------- */

    fun groupNotes(
        notes: List<Note>,
        groupByDate: Boolean,
        trashView: Boolean
    ): List<Pair<String, List<Note>>> {
        if (notes.isEmpty()) return emptyList()
        if (!groupByDate) {
            val label = if (trashView) "Sampah" else "Catatan"
            return listOf(label to notes)
        }

        val out = mutableListOf<Pair<String, List<Note>>>()
        val pinned = notes.filter { it.isPinned }
        val rest = notes.filter { !it.isPinned }
        if (pinned.isNotEmpty()) out.add("Disematkan" to pinned)

        if (rest.isNotEmpty()) {
            val groups = LinkedHashMap<String, MutableList<Note>>()
            for (n in rest) {
                val ts = if (trashView) (n.deletedAt ?: n.updatedAt) else n.updatedAt
                groups.getOrPut(dateGroupLabel(ts)) { mutableListOf() }.add(n)
            }
            out.addAll(groups.map { (label, list) -> label to list })
        }
        return out
    }

    /* ----------------------------------------------------------
       Tag (gabungan tag tersimpan + hashtag pada isi)
       ---------------------------------------------------------- */

    fun allTags(notes: List<Note>): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        for (n in notes) {
            if (n.isDeleted || n.isArchived) continue
            for (t in HtmlCodec.noteTags(n)) counts[t] = (counts[t] ?: 0) + 1
        }
        return counts
    }

    fun folderCounts(notes: List<Note>): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        for (n in notes) {
            if (n.isDeleted || n.isArchived) continue
            val id = n.categoryId ?: continue
            counts[id] = (counts[id] ?: 0) + 1
        }
        return counts
    }
}
