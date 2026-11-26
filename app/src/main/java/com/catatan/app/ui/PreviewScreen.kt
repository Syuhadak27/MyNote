package com.catatan.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catatan.app.data.Block
import com.catatan.app.data.Category
import com.catatan.app.data.HtmlCodec
import com.catatan.app.data.Mark
import com.catatan.app.data.MarkType
import com.catatan.app.ui.theme.CatatanTheme
import com.catatan.app.ui.theme.parseHex
import com.catatan.app.util.fmtDateTime
import com.catatan.app.util.relativeTime

/** Pratinjau catatan di sampah (read-only). */
@Composable
fun PreviewScreen(state: AppState) {
    val c = CatatanTheme.colors
    val store by state.repo.store.collectAsState()
    val noteId = (state.screen as? Screen.Preview)?.noteId
    val note = remember(noteId, store.notes) { store.notes.firstOrNull { it.id == noteId } }

    Column(modifier = Modifier.fillMaxSize()) {
        CatatanTopBar(
            title = "Pratinjau catatan",
            subtitle = "Hanya baca · di sampah",
            navigationIcon = {
                AppIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    onClick = { state.pop() }
                )
            },
            actions = {
                if (noteId != null) {
                    AppIconButton(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = "Menu catatan",
                        onClick = { state.openModal(Modal.NoteMenu(noteId)) }
                    )
                }
            }
        )

        if (note == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Catatan tidak ditemukan.", color = c.text3, fontSize = 13.sp)
            }
        } else {
            val folder: Category? = note.categoryId?.let { id -> store.categories.firstOrNull { it.id == id } }
            val blocks = remember(note.content) { HtmlCodec.parse(note.content) }
            val checks = remember(note.content) { HtmlCodec.countChecklist(note.content) }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                item(key = "header") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (folder != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(parseHex(folder.color).copy(alpha = 0.15f))
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = parseHex(folder.color),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(folder.name, color = c.text, fontSize = 11.sp)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.dangerSoft)
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Di sampah", color = c.danger, fontSize = 11.sp, fontWeight = FontWeight.W600)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    note.title.ifBlank { "Tanpa judul" },
                    color = c.text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.W700,
                    lineHeight = 34.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Dibuat ${fmtDateTime(note.createdAt)}", color = c.text3, fontSize = 11.5.sp)
                note.deletedAt?.let { Text("Dihapus ${fmtDateTime(it)}", color = c.text3, fontSize = 11.5.sp) }
                if (checks.first > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${checks.second}/${checks.first} tugas selesai · ${note.wordCount} kata",
                        color = c.text3,
                        fontSize = 11.5.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                AppDivider()
                }

                items(blocks.size, key = { blocks[it].id }) { index ->
                    Spacer(Modifier.height(if (index == 0) 12.dp else 0.dp))
                    StaticBlock(blocks[index])
                }
                item(key = "footer") {
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "Terakhir diubah ${relativeTime(note.updatedAt)}",
                        color = c.text3,
                        fontSize = 10.5.sp
                    )
                    Spacer(Modifier.height(26.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg.copy(alpha = 0.94f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AppButton(
                        "Pulihkan",
                        { state.restoreNotes(listOf(note.id)); state.pop() },
                        BtnType.PRIMARY,
                        block = true
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AppButton(
                        "Hapus permanen",
                        { state.deletePermanently(listOf(note.id)) },
                        BtnType.DANGER_GHOST,
                        icon = Icons.Filled.DeleteForever,
                        block = true
                    )
                }
            }
        }
    }
}

/* ---------------- Renderer blok statis ---------------- */

@Composable
private fun StaticBlock(block: Block) {
    val c = CatatanTheme.colors
    when (block) {
        is Block.Paragraph -> Text(
            marked(block.text, block.marks, c.accentStrong),
            color = c.text,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        is Block.Heading -> Text(
            marked(block.text, block.marks, c.accentStrong),
            color = c.text,
            fontSize = when (block.level) { 1 -> 25.sp; 2 -> 21.sp; else -> 18.sp },
            fontWeight = FontWeight.W700,
            lineHeight = 30.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
        )
        is Block.Quote -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .background(c.accentSofter)
                .border(1.dp, c.accentSoft, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(marked(block.text, block.marks, c.accentStrong), color = c.text2, fontSize = 15.sp, fontStyle = FontStyle.Italic)
        }
        is Block.Code -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(c.surface2)
                .padding(12.dp)
        ) {
            Text(block.text, color = c.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        is Block.Checklist -> Column(modifier = Modifier.padding(vertical = 3.dp)) {
            block.items.forEach { item ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                    Icon(
                        if (item.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (item.done) c.accentStrong else c.borderStrong,
                        modifier = Modifier.size(17.dp).padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        marked(item.text, item.marks, c.accentStrong),
                        color = if (item.done) c.text3 else c.text,
                        fontSize = 15.sp,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        is Block.BulletList -> Column(modifier = Modifier.padding(vertical = 3.dp)) {
            block.items.forEach { item ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("•", color = c.accentStrong, fontSize = 15.sp, modifier = Modifier.width(18.dp))
                    Text(marked(item.text, item.marks, c.accentStrong), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        is Block.NumberedList -> Column(modifier = Modifier.padding(vertical = 3.dp)) {
            block.items.forEachIndexed { idx, item ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("${idx + 1}.", color = c.accentStrong, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                    Text(marked(item.text, item.marks, c.accentStrong), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        is Block.Divider -> AppDivider(Modifier.padding(vertical = 9.dp))
        is Block.Image -> {
            DataImage(block.src, Modifier.fillMaxWidth(block.widthPct.coerceIn(25, 100) / 100f).clip(RoundedCornerShape(9.dp)))
            if (block.alt.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(block.alt, color = c.text3, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
        is Block.Audio -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface2)
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = c.accentStrong, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Rekaman audio", color = c.text2, fontSize = 13.sp)
        }
        is Block.Table -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .border(1.dp, c.border, RoundedCornerShape(9.dp))
        ) {
            block.rows.forEachIndexed { r, row ->
                Row {
                    row.forEach { cell ->
                        Text(
                            cell,
                            color = if (block.header && r == 0) c.text else c.text2,
                            fontWeight = if (block.header && r == 0) FontWeight.W700 else FontWeight.W400,
                            fontSize = 12.5.sp,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (block.header && r == 0) c.surface2 else Color.Transparent)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun marked(text: String, marks: List<Mark>, accent: Color): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    append(text)
    marks.forEach { m ->
        val s = m.start.coerceIn(0, text.length)
        val e = m.end.coerceIn(s, text.length)
        if (s < e) {
            when (m.type) {
                MarkType.BOLD -> addStyle(
                    androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.W700), s, e
                )
                MarkType.ITALIC -> addStyle(
                    androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic), s, e
                )
                MarkType.UNDERLINE -> addStyle(
                    androidx.compose.ui.text.SpanStyle(textDecoration = TextDecoration.Underline), s, e
                )
                MarkType.STRIKE -> addStyle(
                    androidx.compose.ui.text.SpanStyle(textDecoration = TextDecoration.LineThrough), s, e
                )
                MarkType.COLOR -> m.value?.let { hex ->
                    addStyle(androidx.compose.ui.text.SpanStyle(color = parseHex(hex)), s, e)
                }
                MarkType.HIGHLIGHT -> m.value?.let { hex ->
                    addStyle(androidx.compose.ui.text.SpanStyle(background = parseHex(hex)), s, e)
                }
                MarkType.LINK -> addStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = accent,
                        textDecoration = TextDecoration.Underline
                    ), s, e
                )
            }
        }
    }
}

@Composable
private fun DataImage(dataUrl: String, modifier: Modifier = Modifier) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, dataUrl) {
        value = runCatching {
            val bytes: ByteArray? = if (dataUrl.startsWith(com.catatan.app.data.MediaStore.PREFIX)) {
                com.catatan.app.data.MediaStore.fileFor(dataUrl)?.readBytes()
            } else {
                val b64 = dataUrl.substringAfter("base64,", "")
                if (b64.isEmpty()) null else android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            }
            if (bytes == null) null
            else android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    bmp?.let { Image(it, contentDescription = null, modifier = modifier) }
        ?: Box(modifier.background(CatatanTheme.colors.surface2))
}
