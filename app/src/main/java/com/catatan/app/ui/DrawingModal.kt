package com.catatan.app.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import com.catatan.app.ui.theme.CatatanTheme
import java.io.ByteArrayOutputStream
import android.util.Base64
import kotlin.math.max

private val DRAW_COLORS = listOf(
    "#111827", "#ef4444", "#f97316", "#eab308",
    "#22c55e", "#0ea5e9", "#6366f1", "#ec4899"
)
private val DRAW_SIZES = listOf(2, 4, 8, 14)

/** Modal menggambar tangan → PNG data URL. */
@Composable
fun DrawingModal(onDismiss: () -> Unit, onInsert: (String) -> Unit) {
    val c = CatatanTheme.colors
    val bitmap = remember {
        Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
    }
    val history = remember { mutableListOf<Bitmap>() }
    var revision by remember { mutableIntStateOf(0) }
    val preview by remember(revision) {
        mutableStateOf(bitmap.copy(Bitmap.Config.ARGB_8888, false).asImageBitmap())
    }
    var tool by remember { mutableStateOf("pen") }
    var colorIndex by remember { mutableIntStateOf(0) }
    var sizeIndex by remember { mutableIntStateOf(1) }
    var hasStroke by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    var canvasW by remember { mutableFloatStateOf(1000f) }
    var canvasH by remember { mutableFloatStateOf(700f) }
    var last by remember { mutableStateOf<Offset?>(null) }

    fun stroke(from: Offset, to: Offset) {
        val sx = bitmap.width.toFloat() / max(canvasW, 1f)
        val sy = bitmap.height.toFloat() / max(canvasH, 1f)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = if (tool == "eraser") android.graphics.Color.WHITE
            else android.graphics.Color.parseColor(DRAW_COLORS[colorIndex])
            alpha = if (tool == "marker") 82 else 255
            strokeWidth = DRAW_SIZES[sizeIndex] * if (tool == "marker") 3f else 2f
        }
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawLine(from.x * sx, from.y * sy, to.x * sx, to.y * sy, paint)
        hasStroke = true
        revision++
    }

    fun insert() {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
        onInsert("data:image/png;base64,$b64")
    }

    AppModal(
        icon = Icons.Filled.Gesture,
        title = "Gambar tangan",
        subtitle = "Gambar langsung di kertas catatan",
        onClose = onDismiss,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                AppButton("Batal", onDismiss, BtnType.GHOST)
                AppButton(
                    "Sisipkan gambar",
                    { insert() },
                    BtnType.PRIMARY,
                    enabled = hasStroke
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Alat
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf("pen" to "Pena", "marker" to "Stabilo", "eraser" to "Penghapus").forEach { (value, label) ->
                    val active = tool == value
                    Text(
                        label,
                        color = if (active) c.accentFg else c.text2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) c.accent else c.surface2)
                            .clickable { tool = value }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                AppIconButton(
                    icon = Icons.Filled.Undo,
                    contentDescription = "Urungkan",
                    onClick = {
                        val lastBitmap = history.removeLastOrNull()
                        if (lastBitmap != null) {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            android.graphics.Canvas(bitmap).drawBitmap(lastBitmap, 0f, 0f, null)
                            revision++
                        }
                    },
                    size = 34.dp,
                    enabled = history.isNotEmpty()
                )
            }
            Spacer(Modifier.height(12.dp))

            // Warna
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DRAW_COLORS.forEachIndexed { idx, hex ->
                    val color = com.catatan.app.ui.theme.parseHex(hex)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                if (idx == colorIndex) 2.5.dp else 1.dp,
                                if (idx == colorIndex) c.text else c.border,
                                CircleShape
                            )
                            .clickable { colorIndex = idx; tool = "pen" }
                    )
                }
            }
            Spacer(Modifier.height(11.dp))

            // Ukuran
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ukuran", color = c.text3, fontSize = 11.5.sp)
                DRAW_SIZES.forEachIndexed { idx, size ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (idx == sizeIndex) c.accentSoft else c.surface2)
                            .clickable { sizeIndex = idx },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size((size + 2).dp)
                                .clip(CircleShape)
                                .background(if (idx == sizeIndex) c.accentStrong else c.text3)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextActionButton("Bersihkan", onClick = { askClear = true })
            }
            Spacer(Modifier.height(12.dp))

            // Kanvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                    .onSizeChanged { size ->
                        canvasW = size.width.toFloat()
                        canvasH = size.height.toFloat()
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (history.size >= 20) history.removeAt(0)
                                history.add(bitmap.copy(Bitmap.Config.ARGB_8888, false))
                                last = offset
                                stroke(offset, Offset(offset.x + 0.1f, offset.y + 0.1f))
                            },
                            onDrag = { change, _ ->
                                val from = last ?: change.position
                                stroke(from, change.position)
                                last = change.position
                                change.consume()
                            },
                            onDragEnd = { last = null },
                            onDragCancel = { last = null }
                        )
                    }
            ) {
                Image(preview, contentDescription = "Kanvas gambar", modifier = Modifier.fillMaxSize())
                if (!hasStroke) {
                    Text(
                        "Mulai menggambar di sini",
                        color = c.text3,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Gambar disimpan sebagai bagian dari catatan dengan latar putih.",
                color = c.text3,
                fontSize = 10.5.sp
            )
        }
    }

    if (askClear) {
        AppModal(
            icon = Icons.Filled.Gesture,
            title = "Bersihkan gambar?",
            subtitle = "Semua goresan pada kanvas akan dihapus.",
            onClose = { askClear = false },
            footer = {
                ModalFooterButtons(
                    confirmText = "Bersihkan",
                    confirmType = BtnType.DANGER,
                    onConfirm = {
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        hasStroke = false
                        history.clear()
                        revision++
                        askClear = false
                    },
                    onCancel = { askClear = false }
                )
            }
        ) { }
    }
}
