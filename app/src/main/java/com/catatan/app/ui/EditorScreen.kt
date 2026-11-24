package com.catatan.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catatan.app.data.Block
import com.catatan.app.data.CheckItem
import com.catatan.app.data.HtmlCodec
import com.catatan.app.data.ListItem
import com.catatan.app.data.Mark
import com.catatan.app.data.MarkType
import com.catatan.app.util.fmtDateTime
import com.catatan.app.util.relativeTime
import com.catatan.app.ui.theme.CatatanTheme
import com.catatan.app.ui.theme.fontFamilyOf
import com.catatan.app.ui.theme.parseHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val SAVE_DEBOUNCE_MS = 650L
private const val REVISION_INTERVAL_MS = 4L * 60 * 1000
private val TEXT_COLORS = listOf(
    null, "#111827", "#ef4444", "#f97316", "#ca8a04", "#16a34a",
    "#2563eb", "#7c3aed", "#db2777", "#64748b"
)
private val HIGHLIGHT_COLORS = listOf(
    null, "#fef08a", "#bbf7d0", "#bfdbfe", "#fbcfe8", "#fed7aa", "#ddd6fe"
)

/* ============================================================
   Editor catatan
   ============================================================ */

@Composable
fun EditorScreen(state: AppState) {
    val c = CatatanTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store by state.repo.store.collectAsState()
    val noteId = (state.screen as? Screen.Editor)?.noteId
    val note = remember(noteId, store.notes) { store.notes.firstOrNull { it.id == noteId } }
    val folder = note?.categoryId?.let { id -> store.categories.firstOrNull { it.id == id } }

    var title by remember(noteId) { mutableStateOf(note?.title ?: "") }
    var blocks by remember(noteId) { mutableStateOf<List<Block>>(HtmlCodec.parse(note?.content ?: "")) }
    var tags by remember(noteId) { mutableStateOf(note?.tags ?: emptyList()) }
    var saveStatus by remember(noteId) { mutableStateOf("saved") }
    var focusIndex by remember(noteId) { mutableIntStateOf(-1) }
    var selection by remember(noteId) { mutableStateOf(0..0) }
    var focusMode by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    var undoStack by remember { mutableStateOf(emptyList<List<Block>>()) }
    var redoStack by remember { mutableStateOf(emptyList<List<Block>>()) }

    var styleDialog by remember { mutableStateOf(false) }
    var colorDialog by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var moreDialog by remember { mutableStateOf(false) }
    var tableDialog by remember { mutableStateOf(false) }
    var imageOptions by remember { mutableStateOf<Int?>(null) }
    var showDrawing by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("") }
    var linkLabel by remember { mutableStateOf("") }
    var tableRows by remember { mutableStateOf("3") }
    var tableCols by remember { mutableStateOf("3") }
    var tableHeader by remember { mutableStateOf(true) }
    var imageWidth by remember { mutableIntStateOf(100) }
    var imageAlt by remember { mutableStateOf("") }

    val fontSize = state.settings.fontSize
    val fontFamily = fontFamilyOf(note?.font ?: state.settings.editorFont)

    fun persist() {
        val id = noteId ?: return
        val current = state.repo.note(id) ?: return
        val now = System.currentTimeMillis()
        val content = HtmlCodec.serialize(blocks)
        val plain = HtmlCodec.htmlToText(content)
        var revAt = current.revAt
        if ((current.title.isNotBlank() || current.content.isNotBlank()) &&
            now - revAt >= REVISION_INTERVAL_MS
        ) {
            state.saveRevision(current)
            revAt = now
        }
        state.repo.upsertNote(
            current.copy(
                title = title.take(240),
                content = content,
                plainText = plain,
                wordCount = HtmlCodec.countWords(plain),
                tags = tags,
                updatedAt = now,
                revAt = revAt
            )
        )
        saveStatus = "saved"
    }

    fun leave() {
        persist()
        val n = noteId?.let { state.repo.note(it) }
        if (n != null && n.title.isBlank() && n.plainText.isBlank() &&
            blocks.none { it is Block.Image || it is Block.Audio || it is Block.Table }
        ) {
            state.repo.deleteNotesPermanently(listOf(n.id))
            state.repo.deleteRevisions(n.id)
            state.showToast("Catatan kosong tidak disimpan.", "info", 2000)
        }
        state.pop()
    }

    fun commitBlocks(next: List<Block>) {
        undoStack = (undoStack + listOf(blocks)).takeLast(40)
        redoStack = emptyList()
        blocks = next
        saveStatus = "saving"
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack = redoStack + listOf(blocks)
        blocks = undoStack.last()
        undoStack = undoStack.dropLast(1)
        saveStatus = "saving"
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack = undoStack + listOf(blocks)
        blocks = redoStack.last()
        redoStack = redoStack.dropLast(1)
        saveStatus = "saving"
    }

    fun insertBlocks(newBlocks: List<Block>) {
        if (newBlocks.isEmpty()) return
        val at = if (focusIndex in blocks.indices) focusIndex + 1 else blocks.size
        commitBlocks(blocks.toMutableList().also { it.addAll(at, newBlocks) })
    }

    fun replaceBlock(index: Int, block: Block) {
        if (index !in blocks.indices) return
        commitBlocks(blocks.toMutableList().also { it[index] = block })
    }

    fun deleteBlock(index: Int) {
        if (index !in blocks.indices) return
        commitBlocks(blocks.toMutableList().also { it.removeAt(index) })
        focusIndex = -1
    }

    fun applyMark(type: MarkType, value: String? = null) {
        val i = focusIndex
        if (i !in blocks.indices) return
        val block = blocks[i]
        val text = textOf(block) ?: return
        val marks = marksOf(block) ?: return
        val range = if (selection.first != selection.last) selection else 0..text.length
        val s = range.first.coerceIn(0, text.length)
        val e = range.last.coerceIn(s, text.length)
        if (s >= e) return
        val existing = marks.filter { it.type == type && it.end > s && it.start < e }
        val next = if (existing.isNotEmpty()) {
            marks.filterNot { it in existing }
        } else {
            (marks.filterNot { it.type == type && it.end > s && it.start < e } + Mark(s, e, type, value))
        }
        replaceBlock(i, withMarks(block, text, next))
    }

    fun clearFormat() {
        val i = focusIndex
        if (i !in blocks.indices) return
        val block = blocks[i]
        val text = textOf(block) ?: return
        replaceBlock(i, withMarks(block, text, emptyList()))
    }

    fun setAlign(align: Int) {
        val i = focusIndex
        if (i !in blocks.indices) return
        when (val b = blocks[i]) {
            is Block.Paragraph -> replaceBlock(i, b.copy(align = align))
            is Block.Heading -> replaceBlock(i, b.copy(align = align))
            else -> Unit
        }
    }

    fun changeStyle(style: String) {
        val i = focusIndex
        if (i !in blocks.indices) return
        val b = blocks[i]
        val text = textOf(b) ?: ""
        val marks = marksOf(b) ?: emptyList()
        val next = when (style) {
            "h1" -> Block.Heading(id = b.id, level = 1, text = text, marks = marks)
            "h2" -> Block.Heading(id = b.id, level = 2, text = text, marks = marks)
            "h3" -> Block.Heading(id = b.id, level = 3, text = text, marks = marks)
            "quote" -> Block.Quote(id = b.id, text = text, marks = marks)
            "code" -> Block.Code(id = b.id, text = text)
            else -> Block.Paragraph(id = b.id, text = text, marks = marks)
        }
        replaceBlock(i, next)
        styleDialog = false
    }

    fun addTag(raw: String) {
        val tag = raw.trim().lowercase(Locale.getDefault()).trimStart('#').replace(Regex("\\s+"), "-")
        if (tag.isEmpty()) return
        if (!tag.matches(Regex("^[\\p{L}\\p{N}_-]{1,30}$"))) {
            state.showToast("Tag hanya boleh berisi huruf, angka, garis bawah, atau tanda hubung.", "error")
            return
        }
        if (tag !in tags) {
            tags = tags + tag
            state.showToast("Tag #$tag ditambahkan", duration = 1500)
        }
        tagInput = ""
    }

    LaunchedEffect(title, blocks, tags, noteId) {
        if (noteId == null) return@LaunchedEffect
        saveStatus = "saving"
        delay(SAVE_DEBOUNCE_MS)
        persist()
    }

    DisposableEffect(noteId) {
        onDispose { if (noteId != null) persist() }
    }

    BackHandler { leave() }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        state.showToast("Memproses ${uris.size} gambar…", "info", 1800)
        scope.launch {
            val inserted = withContext(Dispatchers.Default) {
                uris.mapNotNull { uri -> compressImage(context, uri) }
            }
            insertBlocks(inserted.map { src -> Block.Image(src = src) })
            moreDialog = false
        }
    }

    if (note == null) {
        LaunchedEffect(Unit) { state.pop() }
        Box(Modifier.fillMaxSize())
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
        /* ---------------- Appbar ---------------- */
        CatatanTopBar(
            title = "Editor",
            navigationIcon = {
                AppIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    onClick = { leave() }
                )
            },
            centerContent = {
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        "CATATAN",
                        color = c.text3,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 0.8.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when (saveStatus) {
                                        "saving" -> c.warn
                                        "error" -> c.danger
                                        else -> c.success
                                    }
                                )
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            when (saveStatus) {
                                "saving" -> "Menyimpan…"
                                "error" -> "Gagal menyimpan"
                                else -> "Tersimpan"
                            },
                            color = c.text3,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            actions = {
                AppIconButton(
                    icon = Icons.Filled.CenterFocusStrong,
                    contentDescription = "Fokus",
                    onClick = { focusMode = !focusMode },
                    active = focusMode
                )
                AppIconButton(
                    icon = Icons.Filled.PushPin,
                    contentDescription = "Sematkan",
                    onClick = {
                        val v = !(note.isPinned)
                        state.togglePin(listOf(note.id), v)
                    },
                    active = note.isPinned
                )
                AppIconButton(
                    icon = Icons.Filled.Star,
                    contentDescription = "Favorit",
                    onClick = { state.toggleFavorite(listOf(note.id), !note.isFavorite) },
                    active = note.isFavorite
                )
                AppIconButton(
                    icon = Icons.Filled.MoreHoriz,
                    contentDescription = "Menu catatan",
                    onClick = { state.openModal(Modal.NoteMenu(note.id, fromEditor = true)) }
                )
            }
        )

        /* ---------------- Konten ---------------- */
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            // Baris meta
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.surface)
                        .border(1.dp, c.border, RoundedCornerShape(99.dp))
                        .clickable { state.openModal(Modal.MoveToFolder(listOf(note.id))) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (folder != null) parseHex(folder.color) else c.text3
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        folder?.name ?: "Tanpa folder",
                        color = c.text2,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.text3,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = c.text3, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(relativeTime(note.updatedAt), color = c.text3, fontSize = 11.sp)
            }

            Spacer(Modifier.height(8.dp))

            // Tag
            if (tags.isNotEmpty() || tagInput.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(c.accentSofter)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#$tag", color = c.accentStrong, fontSize = 11.sp)
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .clickable { tags = tags - tag },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Hapus tag",
                                    tint = c.accentStrong,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    BasicTextField(
                        value = tagInput,
                        onValueChange = { value ->
                            if (value.contains(',') || value.contains('\n')) {
                                addTag(value.replace(',', ' ').trim())
                            } else {
                                tagInput = value
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 11.5.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier
                            .width(96.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.surface)
                            .border(1.dp, c.border, RoundedCornerShape(99.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) { inner ->
                        if (tagInput.isEmpty()) {
                            Text("+ Tambah tag", color = c.text3, fontSize = 11.sp)
                        }
                        inner()
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                TextActionButton("+ Tambah tag", onClick = { tagInput = " " }, accent = true)
            }

            Spacer(Modifier.height(6.dp))

            /* ---------------- Kertas ---------------- */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surface)
                    .then(
                        if (note.color != null) {
                            Modifier.background(parseHex(note.color ?: "#5b7cfa").copy(alpha = 0.12f))
                        } else Modifier
                    )
                    .border(1.dp, c.border, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = c.text,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.W700,
                            lineHeight = 34.sp
                        ),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth()
                    ) { inner ->
                        if (title.isEmpty()) {
                            Text(
                                "Judul catatan",
                                color = c.text3,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.W700
                            )
                        }
                        inner()
                    }
                    Spacer(Modifier.height(10.dp))

                    blocks.forEachIndexed { index, block ->
                        BlockView(
                            block = block,
                            index = index,
                            fontSize = fontSize,
                            fontFamily = fontFamily,
                            selection = selection,
                            onSelection = { range ->
                                selection = range
                                focusIndex = index
                            },
                            onTextChange = { newText ->
                                val b = blocks[index]
                                val old = textOf(b) ?: ""
                                val marks = marksOf(b) ?: emptyList()
                                val nextMarks = shiftMarks(marks, old, newText)
                                commitBlocks(
                                    blocks.toMutableList().also {
                                        it[index] = withMarks(b, newText, nextMarks)
                                    }
                                )
                                focusIndex = index
                            },
                            onDelete = { deleteBlock(index) },
                            onImageClick = {
                                if (block is Block.Image) {
                                    imageWidth = block.widthPct
                                    imageAlt = block.alt
                                    imageOptions = index
                                }
                            },
                            onCheckToggle = { itemIndex ->
                                val b = blocks[index]
                                if (b is Block.Checklist) {
                                    val items = b.items.toMutableList()
                                    val item = items[itemIndex]
                                    items[itemIndex] = item.copy(done = !item.done)
                                    if (state.settings.moveChecked && !item.done) {
                                        val done = items.filter { it.done }
                                        val pending = items.filterNot { it.done }
                                        items.clear()
                                        items.addAll(pending + done)
                                    }
                                    replaceBlock(index, b.copy(items = items))
                                }
                            },
                            onCheckText = { itemIndex, newText ->
                                val b = blocks[index]
                                if (b is Block.Checklist) {
                                    val items = b.items.toMutableList()
                                    items[itemIndex] = items[itemIndex].copy(
                                        text = newText,
                                        marks = shiftMarks(items[itemIndex].marks, items[itemIndex].text, newText)
                                    )
                                    commitBlocks(blocks.toMutableList().also { it[index] = b.copy(items = items) })
                                }
                            },
                            onCheckEnter = { itemIndex ->
                                val b = blocks[index]
                                if (b is Block.Checklist) {
                                    val items = b.items.toMutableList()
                                    items.add(itemIndex + 1, CheckItem())
                                    commitBlocks(blocks.toMutableList().also { it[index] = b.copy(items = items) })
                                }
                            },
                            onCheckBackspace = { itemIndex ->
                                val b = blocks[index]
                                if (b is Block.Checklist && b.items.size > 1 && b.items[itemIndex].text.isEmpty()) {
                                    val items = b.items.toMutableList()
                                    items.removeAt(itemIndex)
                                    commitBlocks(blocks.toMutableList().also { it[index] = b.copy(items = items) })
                                }
                            },
                            onListItemText = { itemIndex, newText ->
                                val b = blocks[index]
                                val items = when (b) {
                                    is Block.BulletList -> b.items.toMutableList()
                                    is Block.NumberedList -> b.items.toMutableList()
                                    else -> null
                                }
                                if (items != null) {
                                    items[itemIndex] = items[itemIndex].copy(
                                        text = newText,
                                        marks = shiftMarks(items[itemIndex].marks, items[itemIndex].text, newText)
                                    )
                                    val next = when (b) {
                                        is Block.BulletList -> b.copy(items = items)
                                        is Block.NumberedList -> b.copy(items = items)
                                        else -> b
                                    }
                                    commitBlocks(blocks.toMutableList().also { it[index] = next })
                                }
                            },
                            onListEnter = { itemIndex ->
                                val b = blocks[index]
                                val items = when (b) {
                                    is Block.BulletList -> b.items.toMutableList()
                                    is Block.NumberedList -> b.items.toMutableList()
                                    else -> null
                                }
                                if (items != null) {
                                    items.add(itemIndex + 1, ListItem())
                                    val next = when (b) {
                                        is Block.BulletList -> b.copy(items = items)
                                        is Block.NumberedList -> b.copy(items = items)
                                        else -> b
                                    }
                                    commitBlocks(blocks.toMutableList().also { it[index] = next })
                                }
                            },
                            onTableCell = { row, col, newText ->
                                val b = blocks[index]
                                if (b is Block.Table) {
                                    val rows = b.rows.map { it.toMutableList() }.toMutableList()
                                    if (row in rows.indices && col in rows[row].indices) {
                                        rows[row][col] = newText
                                        commitBlocks(
                                            blocks.toMutableList().also {
                                                it[index] = b.copy(rows = rows.map { r -> r.toList() })
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }

                    // Area kosong untuk menambah paragraf
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable {
                                insertBlocks(listOf(Block.Paragraph()))
                                focusIndex = blocks.size - 1
                            }
                    )

                    if (focusMode) {
                        Spacer(Modifier.height(60.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Footer
            val plain = HtmlCodec.htmlToText(HtmlCodec.serialize(blocks))
            val words = HtmlCodec.countWords(plain)
            val minutes = max(1, words / 200)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$words kata · ${plain.length} karakter · $minutes mnt baca",
                    color = c.text3,
                    fontSize = 10.5.sp,
                    modifier = Modifier.weight(1f)
                )
                TextActionButton("Info", onClick = { state.openModal(Modal.NoteInfo(note.id)) }, accent = true)
            }
            Spacer(Modifier.height(4.dp))
            Text(fmtDateTime(note.updatedAt), color = c.text3, fontSize = 10.5.sp)
            Spacer(Modifier.height(24.dp))
        }

        /* ---------------- Toolbar ---------------- */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg.copy(alpha = 0.94f))
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(7.dp, RoundedCornerShape(15.dp))
                    .clip(RoundedCornerShape(15.dp))
                    .background(c.surface.copy(alpha = 0.96f))
                    .border(1.dp, c.border, RoundedCornerShape(15.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ToolButton(Icons.Filled.Undo, "Urungkan", undoStack.isNotEmpty()) { undo() }
                ToolButton(Icons.Filled.Redo, "Ulangi", redoStack.isNotEmpty()) { redo() }
                Spacer(Modifier.width(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.accentSofter)
                        .clickable { styleDialog = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(styleLabel(blocks.getOrNull(focusIndex)), color = c.accentStrong, fontSize = 11.sp)
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.accentStrong,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                ToolButton(Icons.Filled.FormatBold, "Tebal", true) { applyMark(MarkType.BOLD) }
                ToolButton(Icons.Filled.FormatItalic, "Miring", true) { applyMark(MarkType.ITALIC) }
                ToolButton(Icons.Filled.FormatUnderlined, "Garis bawah", true) { applyMark(MarkType.UNDERLINE) }
                ToolButton(Icons.Filled.StrikethroughS, "Coret", true) { applyMark(MarkType.STRIKE) }
                ToolButton(Icons.Filled.FormatColorText, "Warna", true) { colorDialog = true }
                ToolButton(Icons.Filled.Highlight, "Sorotan", true) { colorDialog = true }
                Spacer(Modifier.width(4.dp))
                ToolButton(Icons.Filled.Checklist, "Checklist", true) {
                    insertBlocks(listOf(Block.Checklist(items = listOf(CheckItem()))))
                }
                ToolButton(Icons.Filled.FormatListBulleted, "Daftar", true) {
                    insertBlocks(listOf(Block.BulletList(items = listOf(ListItem()))))
                }
                ToolButton(Icons.Filled.FormatListNumbered, "Nomor", true) {
                    insertBlocks(listOf(Block.NumberedList(items = listOf(ListItem()))))
                }
                ToolButton(Icons.Filled.Link, "Tautan", true) {
                    linkUrl = ""
                    linkLabel = ""
                    linkDialog = true
                }
                ToolButton(Icons.Filled.MoreHoriz, "Alat lainnya", true) { moreDialog = true }
            }
        }
    }

    /* ---------------- Dialog lokal ---------------- */

    if (styleDialog) {
        AppModal(title = "Gaya blok", subtitle = "Ubah jenis paragraf yang dipilih", onClose = { styleDialog = false }) {
            listOf(
                "p" to "Teks Biasa", "h1" to "Judul", "h2" to "Subjudul",
                "h3" to "Heading", "quote" to "Kutipan", "code" to "Monospace"
            ).forEach { (value, label) ->
                TextActionButton(label, onClick = { changeStyle(value) }, accent = true)
                Spacer(Modifier.height(2.dp))
            }
        }
    }

    if (colorDialog) {
        AppModal(title = "Warna & sorotan", subtitle = "Terapkan pada teks yang dipilih", onClose = { colorDialog = false }) {
            SectionLabel("Warna teks")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TEXT_COLORS.forEach { hex ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (hex == null) c.surface3 else parseHex(hex))
                            .border(1.dp, c.border, CircleShape)
                            .clickable {
                                applyMark(MarkType.COLOR, hex)
                                colorDialog = false
                            }
                    )
                }
            }
            SectionLabel("Sorotan")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HIGHLIGHT_COLORS.forEach { hex ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (hex == null) c.surface3 else parseHex(hex))
                            .border(1.dp, c.border, CircleShape)
                            .clickable {
                                applyMark(MarkType.HIGHLIGHT, hex)
                                colorDialog = false
                            }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TextActionButton("Hapus format", onClick = { clearFormat(); colorDialog = false })
        }
    }

    if (linkDialog) {
        AppModal(
            title = "Sisipkan tautan",
            subtitle = "Tautan akan diterapkan pada teks pilihan",
            onClose = { linkDialog = false },
            footer = {
                ModalFooterButtons(
                    confirmText = "Sisipkan",
                    onConfirm = {
                        if (linkUrl.isNotBlank()) {
                            val i = focusIndex
                            val block = blocks.getOrNull(i)
                            val text = textOf(block ?: Block.Paragraph())
                            if (text != null && selection.first != selection.last) {
                                applyMark(MarkType.LINK, linkUrl.trim())
                            } else {
                                val label = linkLabel.ifBlank { linkUrl.trim() }
                                insertBlocks(
                                    listOf(
                                        Block.Paragraph(
                                            text = label,
                                            marks = listOf(Mark(0, label.length, MarkType.LINK, linkUrl.trim()))
                                        )
                                    )
                                )
                            }
                        }
                        linkDialog = false
                    },
                    onCancel = { linkDialog = false }
                )
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldBox("Alamat tautan", linkUrl, { linkUrl = it }, KeyboardType.Uri)
                FieldBox("Teks tampilan (opsional)", linkLabel, { linkLabel = it }, KeyboardType.Text)
            }
        }
    }

    if (tableDialog) {
        AppModal(
            title = "Sisipkan tabel",
            subtitle = "Tentukan ukuran tabel yang akan dibuat",
            onClose = { tableDialog = false },
            footer = {
                ModalFooterButtons(
                    confirmText = "Sisipkan tabel",
                    onConfirm = {
                        val rows = (tableRows.toIntOrNull() ?: 3).coerceIn(1, 12)
                        val cols = (tableCols.toIntOrNull() ?: 3).coerceIn(1, 8)
                        insertBlocks(
                            listOf(
                                Block.Table(
                                    rows = List(rows) { List(cols) { "" } },
                                    header = tableHeader
                                )
                            )
                        )
                        tableDialog = false
                    },
                    onCancel = { tableDialog = false }
                )
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldBox("Baris (1-12)", tableRows, { tableRows = it }, KeyboardType.Number)
                FieldBox("Kolom (1-8)", tableCols, { tableCols = it }, KeyboardType.Number)
                SwitchRow("Baris pertama sebagai judul kolom", checked = tableHeader, onChange = { tableHeader = it })
                Text("Gunakan Enter untuk pindah ke baris berikutnya.", color = c.text3, fontSize = 11.sp)
            }
        }
    }

    if (moreDialog) {
        AppModal(title = "Alat lainnya", subtitle = "Sisipkan konten ke catatan", onClose = { moreDialog = false }) {
            val tools = listOf(
                Triple(
                    Icons.Filled.Image,
                    "Gambar",
                    { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)); false }
                ),
                Triple(
                    Icons.Filled.Gesture,
                    "Gambar tangan",
                    { showDrawing = true; true }
                ),
                Triple(
                    Icons.Filled.Mic,
                    "Rekam audio",
                    { showAudio = true; true }
                ),
                Triple(
                    Icons.Filled.TableChart,
                    "Tabel",
                    { tableDialog = true; true }
                ),
                Triple(
                    Icons.Filled.FormatQuote,
                    "Kutipan",
                    { insertBlocks(listOf(Block.Quote())); true }
                ),
                Triple(
                    Icons.Filled.Code,
                    "Kode",
                    { insertBlocks(listOf(Block.Code())); true }
                ),
                Triple(
                    Icons.Filled.HorizontalRule,
                    "Garis pemisah",
                    { insertBlocks(listOf(Block.Divider())); true }
                ),
                Triple(
                    Icons.Filled.FormatAlignLeft,
                    "Rata kiri",
                    { setAlign(0); true }
                ),
                Triple(
                    Icons.Filled.FormatAlignCenter,
                    "Rata tengah",
                    { setAlign(1); true }
                ),
                Triple(
                    Icons.Filled.FormatAlignRight,
                    "Rata kanan",
                    { setAlign(2); true }
                ),
                Triple(
                    Icons.Filled.Close,
                    "Bersihkan format",
                    { clearFormat(); true }
                ),
                Triple(
                    Icons.Filled.Delete,
                    "Hapus blok",
                    { if (focusIndex in blocks.indices) deleteBlock(focusIndex); true }
                )
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                tools.chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (icon, label, action) ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(c.surface2)
                                    .clickable {
                                        val close = action()
                                        if (close) moreDialog = false
                                    }
                                    .padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = c.accentStrong, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(label, color = c.text, fontSize = 12.5.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    val idx = imageOptions
    if (idx != null && idx in blocks.indices) {
        val block = blocks[idx]
        if (block is Block.Image) {
            AppModal(
                title = "Opsi gambar",
                subtitle = "Atur tampilan gambar dalam catatan",
                onClose = { imageOptions = null },
                footer = {
                    ModalFooterButtons(
                        confirmText = "Terapkan",
                        onConfirm = {
                            replaceBlock(idx, block.copy(widthPct = imageWidth, alt = imageAlt.trim()))
                            imageOptions = null
                        },
                        onCancel = { imageOptions = null }
                    )
                }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Ukuran gambar: $imageWidth%", color = c.text2, fontSize = 13.sp)
                    androidx.compose.material3.Slider(
                        value = imageWidth.toFloat(),
                        onValueChange = { imageWidth = it.toInt().coerceIn(25, 100) },
                        valueRange = 25f..100f,
                        steps = 14
                    )
                    Spacer(Modifier.height(8.dp))
                    FieldBox("Deskripsi gambar", imageAlt, { imageAlt = it }, KeyboardType.Text)
                    Spacer(Modifier.height(12.dp))
                    TextActionButton(
                        "Hapus gambar dari catatan",
                        onClick = { deleteBlock(idx); imageOptions = null }
                    )
                }
            }
        }
    }

    if (showDrawing) {
        DrawingModal(
            onDismiss = { showDrawing = false },
            onInsert = { url ->
                insertBlocks(listOf(Block.Image(src = url, drawing = true)))
                showDrawing = false
            }
        )
    }

    if (showAudio) {
        AudioModal(
            onDismiss = { showAudio = false },
            onInsert = { url ->
                insertBlocks(listOf(Block.Audio(src = url)))
                showAudio = false
            }
        )
    }
}

/* ============================================================
   Helper model blok
   ============================================================ */

private fun textOf(block: Block): String? = when (block) {
    is Block.Paragraph -> block.text
    is Block.Heading -> block.text
    is Block.Quote -> block.text
    is Block.Code -> block.text
    else -> null
}

private fun marksOf(block: Block): List<Mark>? = when (block) {
    is Block.Paragraph -> block.marks
    is Block.Heading -> block.marks
    is Block.Quote -> block.marks
    else -> null
}

private fun withMarks(block: Block, text: String, marks: List<Mark>): Block = when (block) {
    is Block.Paragraph -> block.copy(text = text, marks = marks)
    is Block.Heading -> block.copy(text = text, marks = marks)
    is Block.Quote -> block.copy(text = text, marks = marks)
    is Block.Code -> block.copy(text = text)
    else -> block
}

private fun shiftMarks(marks: List<Mark>, old: String, new: String): List<Mark> {
    var prefix = 0
    while (prefix < old.length && prefix < new.length && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < old.length - prefix && suffix < new.length - prefix &&
        old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
    ) suffix++
    val delta = new.length - old.length
    val tail = old.length - suffix
    return marks.mapNotNull { m ->
        when {
            m.end <= prefix -> m
            m.start >= tail -> m.copy(start = m.start + delta, end = m.end + delta)
            m.start < prefix && m.end > tail -> m.copy(start = 0, end = new.length)
            m.start < prefix -> m.copy(end = prefix)
            m.end > tail -> m.copy(start = new.length - suffix, end = new.length)
            else -> m.copy(start = m.start + delta, end = m.end + delta)
        }
    }.filter { it.end > it.start && it.start >= 0 && it.end <= new.length }
}

private fun styleLabel(block: Block?): String = when (block) {
    is Block.Heading -> when (block.level) {
        1 -> "Judul"
        2 -> "Subjudul"
        else -> "Heading"
    }
    is Block.Quote -> "Kutipan"
    is Block.Code -> "Monospace"
    else -> "Teks Biasa"
}

private fun compressImage(context: Context, uri: Uri): String? = try {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return null
    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val max = 1600
    if (bitmap.width > max || bitmap.height > max) {
        val ratio = min(max.toFloat() / bitmap.width, max.toFloat() / bitmap.height)
        bitmap = android.graphics.Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, out)
    "data:image/jpeg;base64,${Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)}"
} catch (_: Exception) {
    null
}

/* ============================================================
   Tampilan blok
   ============================================================ */

@Composable
private fun BlockView(
    block: Block,
    index: Int,
    fontSize: Int,
    fontFamily: FontFamily,
    selection: IntRange,
    onSelection: (IntRange) -> Unit,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onImageClick: () -> Unit,
    onCheckToggle: (Int) -> Unit,
    onCheckText: (Int, String) -> Unit,
    onCheckEnter: (Int) -> Unit,
    onCheckBackspace: (Int) -> Unit,
    onListItemText: (Int, String) -> Unit,
    onListEnter: (Int) -> Unit,
    onTableCell: (Int, Int, String) -> Unit
) {
    val c = CatatanTheme.colors
    val bringRequester = remember { BringIntoViewRequester() }
    val blockScope = rememberCoroutineScope()
    val focusBring = Modifier
        .bringIntoViewRequester(bringRequester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                blockScope.launch { runCatching { bringRequester.bringIntoView() } }
            }
        }
    when (block) {
        is Block.Paragraph, is Block.Heading, is Block.Quote, is Block.Code -> {
            val text = textOf(block) ?: ""
            val marks = marksOf(block) ?: emptyList()
            val isHeading = block is Block.Heading
            val isQuote = block is Block.Quote
            val isCode = block is Block.Code
            val align = when (block) {
                is Block.Paragraph -> block.align
                is Block.Heading -> block.align
                else -> 0
            }
            val annotated = styled(text, marks, c.text)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isCode || isQuote) {
                            Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isCode) c.surface2 else c.accentSofter)
                                .padding(10.dp)
                        } else Modifier.padding(vertical = 3.dp)
                    )
            ) {
                Column {
                    BasicTextField(
                        value = TextFieldValue(
                            annotatedString = annotated,
                            selection = TextRange(
                                selection.first.coerceAtMost(text.length),
                                selection.last.coerceAtMost(text.length)
                            )
                        ),
                        onValueChange = { value ->
                            onTextChange(value.text)
                            onSelection(value.selection.min..value.selection.max)
                        },
                        textStyle = TextStyle(
                            fontSize = when {
                                isHeading -> (fontSize + when ((block as Block.Heading).level) {
                                    1 -> 8; 2 -> 4; else -> 1
                                }).sp
                                else -> fontSize.sp
                            },
                            fontFamily = if (isCode) FontFamily.Monospace else fontFamily,
                            fontWeight = if (isHeading) FontWeight.W700 else FontWeight.W400,
                            fontStyle = if (isQuote) FontStyle.Italic else FontStyle.Normal,
                            textAlign = when (align) {
                                1 -> TextAlign.Center
                                2 -> TextAlign.End
                                else -> TextAlign.Start
                            },
                            lineHeight = (fontSize * 1.5).sp
                        ),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().then(focusBring)
                    )
                }
            }
        }

        is Block.Checklist -> Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            block.items.forEachIndexed { itemIndex, item ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(21.dp)
                            .padding(top = 3.dp)
                            .clip(CircleShape)
                            .background(if (item.done) c.accent else Color.Transparent)
                            .border(
                                if (item.done) 0.dp else 1.8.dp,
                                if (item.done) c.accent else c.borderStrong,
                                CircleShape
                            )
                            .clickable { onCheckToggle(itemIndex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.done) {
                            Icon(Icons.Filled.Check, null, tint = c.accentFg, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    val annotatedItem = styled(
                        item.text,
                        item.marks,
                        if (item.done) c.text3 else c.text
                    )
                    BasicTextField(
                        value = TextFieldValue(annotatedString = annotatedItem),
                        onValueChange = { value ->
                            onCheckText(itemIndex, value.text)
                            if (value.text.isEmpty() && itemIndex > 0) onCheckBackspace(itemIndex)
                        },
                        textStyle = TextStyle(
                            fontSize = fontSize.sp,
                            fontFamily = fontFamily,
                            lineHeight = (fontSize * 1.45).sp,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null
                        ),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.weight(1f).then(focusBring)
                    )
                }
            }
        }

        is Block.BulletList, is Block.NumberedList -> {
            val items = when (block) {
                is Block.BulletList -> block.items
                is Block.NumberedList -> block.items
                else -> emptyList()
            }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                items.forEachIndexed { itemIndex, item ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (block is Block.BulletList) "•" else "${itemIndex + 1}.",
                            color = c.accentStrong,
                            fontSize = fontSize.sp,
                            modifier = Modifier.width(22.dp)
                        )
                        val annotatedListItem = styled(item.text, item.marks, c.text)
                        BasicTextField(
                            value = TextFieldValue(annotatedString = annotatedListItem),
                            onValueChange = { value -> onListItemText(itemIndex, value.text) },
                            textStyle = TextStyle(
                                fontSize = fontSize.sp,
                                fontFamily = fontFamily,
                                lineHeight = (fontSize * 1.45).sp
                            ),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.weight(1f).then(focusBring)
                        )
                    }
                }
            }
        }

        is Block.Divider -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.height(1.dp).weight(1f).background(c.borderStrong))
            Spacer(Modifier.width(8.dp))
            AppIconButton(Icons.Filled.Delete, "Hapus garis", onDelete, size = 26.dp)
        }

        is Block.Image -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Column {
                DataImage(
                    block.src,
                    Modifier
                        .fillMaxWidth(block.widthPct.coerceIn(25, 100) / 100f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onImageClick)
                )
                if (block.alt.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(block.alt, color = c.text3, fontSize = 10.5.sp)
                }
            }
        }

        is Block.Audio -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface2)
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mic, null, tint = c.accentStrong, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rekaman audio", color = c.text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                AppIconButton(Icons.Filled.Delete, "Hapus rekaman", onDelete, size = 30.dp)
            }
        }

        is Block.Table -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .border(1.dp, c.border, RoundedCornerShape(9.dp))
        ) {
            block.rows.forEachIndexed { r, row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { col, cell ->
                        BasicTextField(
                            value = cell,
                            onValueChange = { onTableCell(r, col, it) },
                            textStyle = TextStyle(
                                color = if (block.header && r == 0) c.text else c.text2,
                                fontSize = 12.5.sp,
                                fontWeight = if (block.header && r == 0) FontWeight.W700 else FontWeight.W400
                            ),
                            cursorBrush = SolidColor(c.accent),
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

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    AppIconButton(icon = icon, contentDescription = label, onClick = onClick, size = 36.dp, enabled = enabled)
}

@Composable
private fun FieldBox(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType) {
    val c = CatatanTheme.colors
    Column {
        Text(label, color = c.text3, fontSize = 11.5.sp)
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(color = c.text, fontSize = 14.sp),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .padding(12.dp)
        )
    }
}

@Composable
private fun styled(text: String, marks: List<Mark>, base: Color): AnnotatedString = buildAnnotatedString {
    addStyle(SpanStyle(color = base), 0, text.length)
    append(text)
    marks.forEach { m ->
        val s = m.start.coerceIn(0, text.length)
        val e = m.end.coerceIn(s, text.length)
        if (s < e) {
            when (m.type) {
                MarkType.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.W700), s, e)
                MarkType.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
                MarkType.UNDERLINE -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s, e)
                MarkType.STRIKE -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s, e)
                MarkType.COLOR -> m.value?.let { addStyle(SpanStyle(color = parseHex(it)), s, e) }
                MarkType.HIGHLIGHT -> m.value?.let { addStyle(SpanStyle(background = parseHex(it)), s, e) }
                MarkType.LINK -> addStyle(
                    SpanStyle(color = CatatanTheme.colors.accentStrong, textDecoration = TextDecoration.Underline),
                    s, e
                )
            }
        }
    }
}

@Composable
private fun DataImage(dataUrl: String, modifier: Modifier = Modifier) {
    val bitmap by androidx.compose.runtime.produceState<ImageBitmap?>(initialValue = null, dataUrl) {
        value = runCatching {
            val b64 = dataUrl.substringAfter("base64,", "")
            if (b64.isEmpty()) null else {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap!!, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.background(CatatanTheme.colors.surface2))
    }
}
