package com.catatan.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/* ============================================================
   HtmlCodec — jembatan HTML catatan <-> blok editor
   (kompatibel dengan backup & sanitizer versi web)
   ============================================================ */

object HtmlCodec {

    private val BLOCK_TAGS = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre",
        "ul", "ol", "table", "figure", "section", "article", "figcaption", "hr"
    )
    private val CONTAINER_TAGS = setOf(
        "div", "section", "article", "figure", "main", "body", "span",
        "strong", "em", "b", "i", "u", "a", "mark", "code", "small", "ins", "del"
    )

    private data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val highlight: String? = null,
        val color: String? = null,
        val link: String? = null
    )

    /* ----------------------------------------------------------
       Parsing HTML -> Blok
       ---------------------------------------------------------- */

    fun parse(html: String): List<Block> {
        if (html.isBlank()) return listOf(Block.Paragraph())
        val doc = Jsoup.parseBodyFragment(html)
        val out = mutableListOf<Block>()
        parseChildren(doc.body(), out)
        if (out.isEmpty()) out.add(Block.Paragraph())
        return out
    }

    private fun parseChildren(parent: Element, out: MutableList<Block>) {
        for (node in parent.childNodes()) {
            when (node) {
                is TextNode -> if (node.text().isNotBlank()) {
                    val text = node.text().trim()
                    if (text.isNotEmpty()) out.add(Block.Paragraph(text = text))
                }
                is Element -> parseElement(node, out)
            }
        }
    }

    private fun parseElement(el: Element, out: MutableList<Block>) {
        val tag = el.tagName().lowercase()
        when {
            tag == "hr" -> out.add(Block.Divider())

            tag == "img" -> imageBlock(el)?.let { out.add(it) }

            tag == "audio" -> audioBlock(el)?.let { out.add(it) }

            tag == "table" -> tableBlock(el)?.let { out.add(it) }

            tag == "ul" || tag == "ol" -> parseList(el, out)

            tag in setOf("h1", "h2", "h3", "h4", "h5", "h6") -> {
                val level = tag[1].digitToInt().coerceIn(1, 3)
                parseContent(el, out) { t, m ->
                    Block.Heading(level = level, text = t, marks = m, align = alignOf(el))
                }
            }

            tag == "blockquote" ->
                parseContent(el, out) { t, m -> Block.Quote(text = t, marks = m) }

            tag == "pre" -> out.add(Block.Code(text = el.wholeText().trim()))

            tag == "li" ->
                parseContent(el, out) { t, m -> Block.Paragraph(text = t, marks = m) }

            tag in CONTAINER_TAGS && el.children().any { it.tagName().lowercase() in BLOCK_TAGS || it.tagName().lowercase() in setOf("img", "audio", "li") } ->
                parseChildren(el, out)

            tag in setOf("td", "th") ->
                parseContent(el, out) { t, m -> Block.Paragraph(text = t, marks = m) }

            else ->
                parseContent(el, out) { t, m ->
                    Block.Paragraph(text = t, marks = m, align = alignOf(el))
                }
        }
    }

    private fun parseContent(
        el: Element,
        out: MutableList<Block>,
        make: (String, List<Mark>) -> Block
    ) {
        val flow = Flow()
        val align = alignOf(el)
        var emitted = false

        fun onMedia(child: Element) {
            if (flow.hasText) {
                val (t, m) = flow.take()
                out.add(Block.Paragraph(text = t, marks = m, align = align))
                emitted = true
            }
            val block = if (child.tagName().equals("img", ignoreCase = true))
                imageBlock(child) else audioBlock(child)
            if (block != null) {
                out.add(block)
                emitted = true
            }
        }

        for (child in el.childNodes()) walk(child, Style(), flow, ::onMedia)

        if (flow.hasText) {
            val (t, m) = flow.take()
            out.add(make(t, m))
        } else if (!emitted) {
            out.add(make("", emptyList()))
        }
    }

    private fun parseList(el: Element, out: MutableList<Block>) {
        val ordered = el.tagName() == "ol"
        val listItems = mutableListOf<ListItem>()
        val checkItems = mutableListOf<CheckItem>()
        var anyCheckbox = el.hasClass("checklist")

        for (li in el.children()) {
            if (!li.tagName().equals("li", ignoreCase = true)) continue
            val input = li.children().firstOrNull {
                it.tagName().equals("input", ignoreCase = true) &&
                    (it.attr("type").isEmpty() || it.attr("type").equals("checkbox", true))
            }
            val clone = li.clone()
            if (input != null) {
                clone.children().filter {
                    it.tagName().equals("input", ignoreCase = true)
                }.forEach { it.remove() }
                val flow = Flow()
                for (child in clone.childNodes()) walk(child, Style(), flow, null)
                val (text, marks) = flow.take()
                val done = input.hasAttr("checked") || li.hasClass("is-done")
                checkItems.add(CheckItem(text = text, done = done, marks = marks))
                anyCheckbox = true
            } else {
                val flow = Flow()
                for (child in clone.childNodes()) walk(child, Style(), flow, null)
                val (text, marks) = flow.take()
                listItems.add(ListItem(text = text, marks = marks))
            }
        }

        when {
            anyCheckbox && checkItems.isNotEmpty() -> out.add(Block.Checklist(items = checkItems))
            listItems.isNotEmpty() ->
                out.add(if (ordered) Block.NumberedList(items = listItems)
                else Block.BulletList(items = listItems))
            anyCheckbox && checkItems.isEmpty() ->
                out.add(Block.Checklist(items = listOf(CheckItem())))
        }
    }

    private fun walk(
        node: Node,
        style: Style,
        flow: Flow,
        onMedia: ((Element) -> Unit)?
    ) {
        when (node) {
            is TextNode -> flow.append(node.text(), style)

            is Element -> {
                val tag = node.tagName().lowercase()
                when {
                    tag == "script" || tag == "style" || tag == "template" -> Unit
                    tag == "img" || tag == "audio" -> onMedia?.invoke(node)
                    tag == "br" -> flow.append("\n", style)
                    tag == "input" -> Unit
                    else -> {
                        val next = styleFor(style, tag, node)
                        for (child in node.childNodes()) walk(child, next, flow, onMedia)
                    }
                }
            }
        }
    }

    private fun styleFor(base: Style, tag: String, el: Element): Style {
        var s = base
        when (tag) {
            "strong", "b" -> s = s.copy(bold = true)
            "em", "i" -> s = s.copy(italic = true)
            "u", "ins" -> s = s.copy(underline = true)
            "s", "del", "strike" -> s = s.copy(strike = true)
            "mark" -> s = s.copy(highlight = s.highlight ?: "#fef08a")
            "a" -> {
                val href = el.attr("href").trim()
                if (href.startsWith("http://") || href.startsWith("https://") ||
                    href.startsWith("mailto:") || href.startsWith("tel:") || href == "#"
                ) s = s.copy(link = href)
            }
        }
        // gaya inline dari atribut style
        val styleAttr = el.attr("style").lowercase()
        if (styleAttr.isNotEmpty()) {
            Regex("color:\\s*(#[0-9a-f]{3,8}|[a-z]+)").find(styleAttr)?.let {
                val v = it.groupValues[1]
                if (v != "transparent") s = s.copy(color = normalizeHex(v))
            }
            Regex("background-color:\\s*(#[0-9a-f]{3,8}|[a-z]+)").find(styleAttr)?.let {
                val v = it.groupValues[1]
                if (v != "transparent") s = s.copy(highlight = normalizeHex(v))
            }
            if ("font-weight: bold" in styleAttr || "font-weight:700" in styleAttr)
                s = s.copy(bold = true)
            if ("font-style: italic" in styleAttr) s = s.copy(italic = true)
            if ("text-decoration: underline" in styleAttr) s = s.copy(underline = true)
            if ("line-through" in styleAttr) s = s.copy(strike = true)
        }
        if (el.hasClass("is-done") && tag == "li") s = s
        return s
    }

    private fun alignOf(el: Element): Int {
        val style = el.attr("style").lowercase()
        return when {
            "text-align: center" in style || "text-align:center" in style -> 1
            "text-align: right" in style || "text-align:right" in style -> 2
            else -> 0
        }
    }

    private fun normalizeHex(v: String): String {
        val clean = v.removePrefix("#").lowercase()
        return when (clean.length) {
            3 -> "#" + clean.map { "$it$it" }.joinToString("")
            6, 8 -> "#" + clean.take(6)
            else -> v
        }
    }

    private fun imageBlock(el: Element): Block? {
        val src = el.attr("src").trim()
        if (!safeSrc(src, "image")) return null
        val style = el.attr("style")
        val pct = Regex("width:\\s*(\\d{1,3})%").find(style)?.groupValues?.get(1)?.toIntOrNull()
            ?: 100
        return Block.Image(
            src = src,
            alt = el.attr("alt"),
            widthPct = pct.coerceIn(25, 100),
            drawing = el.hasClass("note-drawing")
        )
    }

    private fun audioBlock(el: Element): Block? {
        var src = el.attr("src").trim()
        if (src.isEmpty()) src = el.selectFirst("source")?.attr("src")?.trim() ?: ""
        if (!safeSrc(src, "audio")) return null
        return Block.Audio(src = src)
    }

    private fun tableBlock(el: Element): Block? {
        val rows = el.select("tr").map { tr ->
            tr.children()
                .filter { it.tagName().equals("td", true) || it.tagName().equals("th", true) }
                .map { it.text().trim() }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return null
        val header = el.selectFirst("tr")?.children()
            ?.any { it.tagName().equals("th", true) } == true
        return Block.Table(rows = rows, header = header)
    }

    private fun safeSrc(src: String, kind: String): Boolean {
        if (src.isEmpty()) return false
        if (src.startsWith("http://") || src.startsWith("https://")) return true
        val pattern = if (kind == "image")
            Regex("^data:image/(png|jpe?g|gif|webp);base64,", RegexOption.IGNORE_CASE)
        else
            Regex("^data:audio/[^;]+;base64,", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(src)
    }

    /* ----------------------------------------------------------
       Serialisasi Blok -> HTML
       ---------------------------------------------------------- */

    fun serialize(blocks: List<Block>): String =
        blocks.joinToString("") { serializeBlock(it) }

    private fun serializeBlock(b: Block): String = when (b) {
        is Block.Paragraph ->
            if (b.text.isEmpty()) "<p><br></p>"
            else "<p${alignAttr(b.align)}>${inline(b.text, b.marks)}</p>"

        is Block.Heading -> {
            val lvl = b.level.coerceIn(1, 3)
            "<h$lvl${alignAttr(b.align)}>${inline(b.text, b.marks)}</h$lvl>"
        }

        is Block.Quote -> "<blockquote>${inline(b.text, b.marks)}</blockquote>"

        is Block.Code -> "<pre><code>${escape(b.text)}</code></pre>"

        is Block.Checklist -> buildString {
            append("<ul class=\"checklist\">")
            b.items.forEach { item ->
                append("<li class=\"check-item")
                if (item.done) append(" is-done")
                append("\"><input type=\"checkbox\"")
                if (item.done) append(" checked")
                append(">")
                append(inline(item.text, item.marks))
                append("</li>")
            }
            append("</ul>")
        }

        is Block.BulletList -> buildString {
            append("<ul>")
            b.items.forEach { append("<li>${inline(it.text, it.marks)}</li>") }
            append("</ul>")
        }

        is Block.NumberedList -> buildString {
            append("<ol>")
            b.items.forEach { append("<li>${inline(it.text, it.marks)}</li>") }
            append("</ol>")
        }

        is Block.Divider -> "<hr>"

        is Block.Image -> buildString {
            append("<img class=\"note-image")
            if (b.drawing) append(" note-drawing")
            append("\" src=\"").append(escapeAttr(b.src))
            append("\" alt=\"").append(escapeAttr(b.alt.ifEmpty { "Gambar catatan" }))
            append("\" style=\"max-width:100%;width:")
            append(b.widthPct.coerceIn(25, 100)).append("%\">")
        }

        is Block.Audio ->
            "<audio controls preload=\"metadata\" src=\"${escapeAttr(b.src)}\"></audio>"

        is Block.Table -> buildString {
            append("<table class=\"note-table\"><tbody>")
            b.rows.forEachIndexed { i, row ->
                append("<tr>")
                row.forEach { cell ->
                    val tag = if (b.header && i == 0) "th" else "td"
                    append("<$tag>${escape(cell)}</$tag>")
                }
                append("</tr>")
            }
            append("</tbody></table>")
        }
    }

    private fun alignAttr(align: Int): String = when (align) {
        1 -> " style=\"text-align:center\""
        2 -> " style=\"text-align:right\""
        else -> ""
    }

    private fun openTag(m: Mark): String = when (m.type) {
        MarkType.BOLD -> "<strong>"
        MarkType.ITALIC -> "<em>"
        MarkType.UNDERLINE -> "<u>"
        MarkType.STRIKE -> "<s>"
        MarkType.HIGHLIGHT -> "<span style=\"background-color:${m.value ?: "#fef08a"}\">"
        MarkType.COLOR -> "<span style=\"color:${m.value ?: "#111827"}\">"
        MarkType.LINK ->
            "<a href=\"${escapeAttr(m.value ?: "#")}\" target=\"_blank\" rel=\"noopener noreferrer nofollow\">"
    }

    private fun closeTag(m: Mark): String = when (m.type) {
        MarkType.BOLD -> "</strong>"
        MarkType.ITALIC -> "</em>"
        MarkType.UNDERLINE -> "</u>"
        MarkType.STRIKE -> "</s>"
        MarkType.HIGHLIGHT, MarkType.COLOR -> "</span>"
        MarkType.LINK -> "</a>"
    }

    /** Susun teks + tanda gaya menjadi HTML inline. */
    fun inline(text: String, marks: List<Mark>): String {
        val valid = marks
            .filter { it.end > it.start && it.start >= 0 && it.start <= text.length }
            .map { it.copy(end = it.end.coerceAtMost(text.length)) }
            .sortedWith(compareBy({ it.start }, { -it.end }))
        if (valid.isEmpty()) return escape(text).replace("\n", "<br>")

        val sb = StringBuilder()
        val open = mutableListOf<Mark>()
        var i = 0
        var idx = 0
        while (i <= text.length) {
            var changed = true
            while (changed) {
                changed = false
                for (j in open.indices.reversed()) {
                    if (open[j].end <= i) {
                        sb.append(closeTag(open[j]))
                        open.removeAt(j)
                        changed = true
                    }
                }
            }
            if (i == text.length) break
            while (idx < valid.size && valid[idx].start == i) {
                val m = valid[idx]
                if (open.none { it.type == m.type && it.value == m.value && it.end >= m.end }) {
                    sb.append(openTag(m))
                    open.add(m)
                }
                idx++
            }
            val c = text[i]
            if (c == '\n') sb.append("<br>") else sb.append(escape(c.toString()))
            i++
        }
        for (j in open.indices.reversed()) sb.append(closeTag(open[j]))
        return sb.toString()
    }

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    private fun escapeAttr(s: String): String = escape(s).replace("'", "&#39;")

    /* ----------------------------------------------------------
       HTML -> teks polos (preview, pencarian, jumlah kata)
       ---------------------------------------------------------- */

    fun htmlToText(html: String): String {
        if (html.isBlank()) return ""
        val doc = Jsoup.parseBodyFragment(html)
        val sb = StringBuilder()

        fun walk(node: Node) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> {
                    val tag = node.tagName().lowercase()
                    when (tag) {
                        "script", "style", "template" -> Unit
                        "br" -> sb.append('\n')
                        "hr" -> sb.append("\n---\n")
                        "img" -> sb.append(node.attr("alt").ifEmpty { "[Gambar]" })
                        "input" -> sb.append(if (node.hasAttr("checked")) "☑" else "☐")
                        "li" -> {
                            sb.append("\n• ")
                            node.childNodes().forEach { walk(it) }
                        }
                        in BLOCK_TAGS, "td", "th", "tr" -> {
                            node.childNodes().forEach { walk(it) }
                            sb.append('\n')
                        }
                        else -> node.childNodes().forEach { walk(it) }
                    }
                }
            }
        }

        doc.body().childNodes().forEach { walk(it) }
        return sb.toString()
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /* ----------------------------------------------------------
       Detektor & ekstraktor (sama dengan utils.js)
       ---------------------------------------------------------- */

    fun hasChecklist(html: String): Boolean =
        Regex("<input[^>]+type=[\"']?checkbox", RegexOption.IGNORE_CASE).containsMatchIn(html)

    fun hasImage(html: String): Boolean =
        Regex("<img[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(html)

    fun hasAudio(html: String): Boolean =
        Regex("<audio[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(html)

    fun hasLink(html: String): Boolean =
        Regex("<a[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(html)

    fun hasTable(html: String): Boolean =
        Regex("<table[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(html)

    fun firstImageSrc(html: String): String =
        runCatching {
            Jsoup.parseBodyFragment(html).selectFirst("img")?.attr("src") ?: ""
        }.getOrDefault("")

    fun countChecklist(html: String): Pair<Int, Int> {
        val doc = runCatching { Jsoup.parseBodyFragment(html) }.getOrNull() ?: return 0 to 0
        val inputs = doc.select("input[type=checkbox]")
        val done = inputs.count { it.hasAttr("checked") }
        return inputs.size to done
    }

    fun extractHashtags(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val regex = Regex("""(?:^|[\s(])#([\p{L}\p{N}_-]{1,40})""")
        return regex.findAll(text)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .toList()
    }

    fun noteTags(note: Note): List<String> {
        val direct = note.tags
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotEmpty() }
        val source = note.plainText.ifEmpty { htmlToText(note.content) }
        return (direct + extractHashtags(source)).distinct().take(30)
    }

    fun notePreview(note: Note, max: Int = 180): String {
        val plain = note.plainText.ifEmpty { htmlToText(note.content) }
        val collapsed = plain.replace(Regex("\\s+"), " ").trim()
        if (collapsed.isEmpty()) return if (hasImage(note.content)) "Gambar" else ""
        return collapsed.take(max)
    }

    fun countWords(text: String): Int =
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size

    /* ----------------------------------------------------------
       Aliran penulisan saat parsing
       ---------------------------------------------------------- */

    private class Flow {
        private val sb = StringBuilder()
        private val marks = mutableListOf<Mark>()
        val hasText: Boolean get() = sb.isNotEmpty()

        fun append(text: String, style: Style) {
            if (text.isEmpty()) return
            val start = sb.length
            sb.append(text)
            val end = sb.length
            fun add(type: MarkType, value: String? = null) {
                marks.add(Mark(start, end, type, value))
            }
            if (style.bold) add(MarkType.BOLD)
            if (style.italic) add(MarkType.ITALIC)
            if (style.underline) add(MarkType.UNDERLINE)
            if (style.strike) add(MarkType.STRIKE)
            style.highlight?.let { add(MarkType.HIGHLIGHT, it) }
            style.color?.let { add(MarkType.COLOR, it) }
            style.link?.let { add(MarkType.LINK, it) }
        }

        fun take(): Pair<String, List<Mark>> {
            val text = sb.toString()
            val result = marks.filter { it.end > it.start && it.start < text.length }
                .map { it.copy(end = it.end.coerceAtMost(text.length)) }
            sb.clear()
            marks.clear()
            return text to result
        }
    }

    /** Bersihkan HTML apa pun menjadi HTML hasil codec kita (sanitizer). */
    fun clean(html: String): String = serialize(parse(html))
}
