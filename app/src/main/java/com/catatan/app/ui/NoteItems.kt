package com.catatan.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catatan.app.data.HtmlCodec
import com.catatan.app.data.Note
import com.catatan.app.ui.theme.CatatanTheme
import com.catatan.app.util.noteTimeLabel
import com.catatan.app.util.relativeTime
import com.catatan.app.util.trashDaysLeft
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/* ============================================================
   Sorotan kata kunci pencarian
   ============================================================ */

fun highlight(text: String, query: String): AnnotatedString {
    val words = query.trim().split(Regex("\\s+"))
        .filter { it.length > 1 && !it.contains(":") && !it.startsWith("#") }
    if (words.isEmpty()) return AnnotatedString(text)

    val lower = text.lowercase()
    val found = mutableListOf<Pair<Int, Int>>()
    for (w in words) {
        val lw = w.lowercase()
        var idx = lower.indexOf(lw)
        while (idx >= 0) {
            found.add(idx to (idx + lw.length))
            idx = lower.indexOf(lw, idx + lw.length)
        }
    }
    if (found.isEmpty()) return AnnotatedString(text)

    found.sortBy { it.first }
    val merged = mutableListOf<Pair<Int, Int>>()
    for (pair in found) {
        if (merged.isEmpty() || pair.first > merged.last().second) merged.add(pair)
        else merged[merged.size - 1] = merged.last().first to maxOf(merged.last().second, pair.second)
    }

    return buildAnnotatedString {
        var cursor = 0
        for ((s, e) in merged) {
            if (s > cursor) append(text.substring(cursor, s))
            pushStyle(SpanStyle(background = Color(0x3D5B7CFA)))
            append(text.substring(s, e))
            pop()
            cursor = e
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

/* ============================================================
   Kontainer swipe (kanan = pin, kiri = hapus)
   ============================================================ */

@Composable
fun SwipeableNote(
    enabled: Boolean,
    rightLabel: String,
    leftLabel: String,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    content: @Composable () -> Unit
) {
    val c = CatatanTheme.colors
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxWidth()) {
        // Latar aksi
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        offset.value > 8 -> c.accent
                        offset.value < -8 -> c.danger
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            if (offset.value > 24) {
                Row(
                    modifier = Modifier.padding(start = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(rightLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.W600)
                }
            }
            if (offset.value < -24) {
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(leftLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.W600)
                    Spacer(Modifier.width(7.dp))
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount ->
                            drag += amount
                            val next = (offset.value + amount).coerceIn(-120f, 120f)
                            scope.launch { offset.snapTo(next) }
                        },
                        onDragEnd = {
                            val current = offset.value
                            scope.launch {
                                when {
                                    current > 72f -> {
                                        offset.animateTo(160f)
                                        haptics.performHapticFeedback(
                                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                        )
                                        onSwipeRight()
                                        offset.snapTo(0f)
                                    }
                                    current < -72f -> {
                                        offset.animateTo(-160f)
                                        haptics.performHapticFeedback(
                                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                        )
                                        onSwipeLeft()
                                        offset.snapTo(0f)
                                    }
                                    else -> offset.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offset.animateTo(0f) }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

/* ============================================================
   Baris catatan (mode daftar)
   ============================================================ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteRow(
    note: Note,
    folderName: String?,
    folderColor: String?,
    query: String,
    selectionMode: Boolean,
    selected: Boolean,
    swipeEnabled: Boolean,
    isTrash: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onMenu: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit
) {
    val c = CatatanTheme.colors
    val haptics = LocalHapticFeedback.current

    val preview = HtmlCodec.notePreview(note, 260)
    val title = note.title.ifBlank { "Tanpa judul" }
    val untitled = note.title.isBlank()
    val (totalChecks, doneChecks) = HtmlCodec.countChecklist(note.content)
    val tags = HtmlCodec.noteTags(note).take(2)
    val moreTags = HtmlCodec.noteTags(note).size - tags.size
    val timeLabel = if (isTrash) relativeTime(note.deletedAt ?: note.updatedAt)
    else noteTimeLabel(note.updatedAt)
    val daysLeft = if (isTrash) trashDaysLeft(note.deletedAt) else -1

    SwipeableNote(
        enabled = swipeEnabled && !selectionMode,
        rightLabel = if (note.isPinned) "Lepas pin" else "Sematkan",
        leftLabel = "Hapus",
        onSwipeRight = onSwipeRight,
        onSwipeLeft = onSwipeLeft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(1.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) c.accentSoft else c.surface)
                .border(
                    1.dp,
                    if (selected) c.accent.copy(alpha = 0.55f) else c.border,
                    RoundedCornerShape(14.dp)
                )
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                    onLongClick = {
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                        )
                        if (!selectionMode) onToggleSelect()
                    }
                )
                .padding(start = 13.dp, top = 13.dp, end = 9.dp, bottom = 13.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(21.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (selected) c.accent else c.surface3)
                        .border(1.5.dp, if (selected) c.accent else c.borderStrong, RoundedCornerShape(99.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        highlight(title, query),
                        color = if (untitled) c.text3 else c.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W600,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (note.isPinned) MiniBadge(Icons.Filled.PushPin, "Disematkan", c.accentSoft, c.accentStrong)
                        if (note.isFavorite) MiniBadge(Icons.Filled.Star, "Favorit", Color(0x29F5B428), Color(0xFFC78A0A))
                        if (HtmlCodec.hasAudio(note.content)) MiniBadge(Icons.Filled.Mic, "Rekaman", c.surface3, c.text2)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    highlight(preview, query),
                    color = c.text2,
                    fontSize = 12.8.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(7.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (folderName != null) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(folderColor?.let { com.catatan.app.ui.theme.parseHex(it) } ?: c.accent)
                        )
                        Text(folderName, color = c.text3, fontSize = 10.5.sp, maxLines = 1)
                    }
                    tags.forEach { t ->
                        Text("#$t", color = c.text3, fontSize = 10.5.sp, maxLines = 1)
                    }
                    if (moreTags > 0) Text("+$moreTags", color = c.text3, fontSize = 10.5.sp)
                    if (totalChecks > 0) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = c.text3,
                            modifier = Modifier.size(11.dp)
                        )
                        Text("$doneChecks/$totalChecks", color = c.text3, fontSize = 10.5.sp)
                    }
                    if (HtmlCodec.hasImage(note.content)) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            tint = c.text3,
                            modifier = Modifier.size(11.dp)
                        )
                        Text("Gambar", color = c.text3, fontSize = 10.5.sp)
                    }
                    if (isTrash) {
                        Text(
                            "$daysLeft hari lagi",
                            color = if (daysLeft <= 3) c.danger else c.text3,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            if (HtmlCodec.hasImage(note.content)) {
                Spacer(Modifier.width(9.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, c.border, RoundedCornerShape(9.dp))
                ) {
                    AsyncImage(
                        model = HtmlCodec.firstImageSrc(note.content),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(timeLabel, color = c.text3, fontSize = 10.5.sp, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(29.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.surface2)
                        .combinedClickable(onClick = onMenu),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = "Menu catatan",
                        tint = c.text2,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    bg: Color,
    fg: Color
) {
    Box(
        modifier = Modifier
            .size(19.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = fg, modifier = Modifier.size(12.dp))
    }
}

/* ============================================================
   Kartu catatan (mode grid)
   ============================================================ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteGridCard(
    note: Note,
    folderName: String?,
    folderColor: String?,
    query: String,
    selectionMode: Boolean,
    selected: Boolean,
    swipeEnabled: Boolean,
    isTrash: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onMenu: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit
) {
    val c = CatatanTheme.colors
    val haptics = LocalHapticFeedback.current

    val preview = HtmlCodec.notePreview(note, 200)
    val title = note.title.ifBlank { "Tanpa judul" }
    val untitled = note.title.isBlank()
    val (totalChecks, doneChecks) = HtmlCodec.countChecklist(note.content)
    val timeLabel = if (isTrash) relativeTime(note.deletedAt ?: note.updatedAt)
    else noteTimeLabel(note.updatedAt)
    val tags = HtmlCodec.noteTags(note).take(2)

    SwipeableNote(
        enabled = swipeEnabled && !selectionMode,
        rightLabel = if (note.isPinned) "Lepas pin" else "Sematkan",
        leftLabel = "Hapus",
        onSwipeRight = onSwipeRight,
        onSwipeLeft = onSwipeLeft
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 148.dp)
                .shadow(1.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) c.accentSoft else c.surface)
                .border(
                    1.dp,
                    if (selected) c.accent.copy(alpha = 0.55f) else c.border,
                    RoundedCornerShape(14.dp)
                )
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                    onLongClick = {
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                        )
                        if (!selectionMode) onToggleSelect()
                    }
                )
                .padding(11.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(19.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (selected) c.accent else c.surface3)
                            .border(1.5.dp, if (selected) c.accent else c.borderStrong, RoundedCornerShape(99.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    highlight(title, query),
                    color = if (untitled) c.text3 else c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (note.isPinned) MiniBadge(Icons.Filled.PushPin, "Disematkan", c.accentSoft, c.accentStrong)
                    if (note.isFavorite) MiniBadge(Icons.Filled.Star, "Favorit", Color(0x29F5B428), Color(0xFFC78A0A))
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                highlight(preview, query),
                color = c.text2,
                fontSize = 12.2.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (HtmlCodec.hasImage(note.content)) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.surface2)
                ) {
                    AsyncImage(
                        model = HtmlCodec.firstImageSrc(note.content),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeLabel, color = c.text3, fontSize = 10.sp, maxLines = 1)
                if (folderName != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(folderColor?.let { com.catatan.app.ui.theme.parseHex(it) } ?: c.accent)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(folderName, color = c.text3, fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                }
                if (totalChecks > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("$doneChecks/$totalChecks", color = c.text3, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(c.surface2)
                        .combinedClickable(onClick = onMenu),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = "Menu catatan",
                        tint = c.text2,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { t ->
                        Text(
                            "#$t",
                            color = c.text3,
                            fontSize = 9.5.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(c.surface2)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ============================================================
   Header grup
   ============================================================ */

@Composable
fun GroupHeader(label: String, count: Int) {
    val c = CatatanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 3.dp, end = 3.dp, top = 14.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = c.text2,
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.2.sp
        )
        Spacer(Modifier.width(7.dp))
        Box(
            modifier = Modifier
                .height(19.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(c.surface3)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(count.toString(), color = c.text3, fontSize = 10.sp, fontWeight = FontWeight.W600)
        }
    }
}
