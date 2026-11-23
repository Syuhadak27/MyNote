package com.catatan.app.data

import com.catatan.app.util.uid

/* ============================================================
   Blok editor — model internal saat menulis (bukan disimpan;
   sumber kebenaran tetap HTML pada Note.content)
   ============================================================ */

enum class MarkType { BOLD, ITALIC, UNDERLINE, STRIKE, HIGHLIGHT, COLOR, LINK }

data class Mark(
    val start: Int,
    val end: Int,
    val type: MarkType,
    val value: String? = null
)

data class CheckItem(
    val text: String = "",
    val done: Boolean = false,
    val marks: List<Mark> = emptyList()
)

data class ListItem(
    val text: String = "",
    val marks: List<Mark> = emptyList()
)

sealed class Block {
    abstract val id: String

    data class Paragraph(
        override val id: String = uid(),
        val text: String = "",
        val marks: List<Mark> = emptyList(),
        val align: Int = 0            // 0 kiri · 1 tengah · 2 kanan
    ) : Block()

    data class Heading(
        override val id: String = uid(),
        val level: Int = 1,           // 1..3
        val text: String = "",
        val marks: List<Mark> = emptyList(),
        val align: Int = 0
    ) : Block()

    data class Quote(
        override val id: String = uid(),
        val text: String = "",
        val marks: List<Mark> = emptyList()
    ) : Block()

    data class Code(
        override val id: String = uid(),
        val text: String = ""
    ) : Block()

    data class Checklist(
        override val id: String = uid(),
        val items: List<CheckItem> = emptyList()
    ) : Block()

    data class BulletList(
        override val id: String = uid(),
        val items: List<ListItem> = emptyList()
    ) : Block()

    data class NumberedList(
        override val id: String = uid(),
        val items: List<ListItem> = emptyList()
    ) : Block()

    data class Divider(override val id: String = uid()) : Block()

    data class Image(
        override val id: String = uid(),
        val src: String = "",
        val alt: String = "",
        val widthPct: Int = 100,
        val drawing: Boolean = false
    ) : Block()

    data class Audio(
        override val id: String = uid(),
        val src: String = ""
    ) : Block()

    data class Table(
        override val id: String = uid(),
        val rows: List<List<String>> = emptyList(),
        val header: Boolean = false
    ) : Block()
}

/** Apakah blok masih "kosong" (tidak punya teks sama sekoli). */
fun Block.isTextEmpty(): Boolean = when (this) {
    is Block.Paragraph -> text.isBlank()
    is Block.Heading -> text.isBlank()
    is Block.Quote -> text.isBlank()
    is Block.Code -> text.isBlank()
    is Block.Checklist -> items.all { it.text.isBlank() }
    is Block.BulletList -> items.all { it.text.isBlank() }
    is Block.NumberedList -> items.all { it.text.isBlank() }
    else -> false
}

/** Total kata pada daftar blok. */
fun List<Block>.textLength(): Int = sumOf {
    when (it) {
        is Block.Paragraph -> it.text.length
        is Block.Heading -> it.text.length
        is Block.Quote -> it.text.length
        is Block.Code -> it.text.length
        is Block.Checklist -> it.items.sumOf { i -> i.text.length }
        is Block.BulletList -> it.items.sumOf { i -> i.text.length }
        is Block.NumberedList -> it.items.sumOf { i -> i.text.length }
        else -> 0
    }
}
