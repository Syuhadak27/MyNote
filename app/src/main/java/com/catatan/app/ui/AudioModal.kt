package com.catatan.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.catatan.app.ui.theme.CatatanTheme
import com.catatan.app.util.formatDuration
import kotlinx.coroutines.delay
import java.io.File

private const val MAX_RECORD_SECONDS = 600

/** Modal rekam audio → data URL audio/mp4. */
@Composable
fun AudioModal(onDismiss: () -> Unit, onInsert: (String) -> Unit) {
    val c = CatatanTheme.colors
    val context = LocalContext.current

    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var hasRecording by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    val player = remember { MediaPlayer() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            errorText = "Akses mikrofon ditolak. Izinkan mikrofon di pengaturan lalu coba lagi."
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun stopRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            errorText = "Rekaman terlalu singkat. Coba rekam kembali."
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        recording = false
        hasRecording = true
    }

    fun startRecording() {
        errorText = null
        if (!hasPermission()) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec-${System.currentTimeMillis()}.m4a")
        try {
            val r = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            outputFile = file
            elapsed = 0
            playing = false
            hasRecording = false
            progress = 0f
            recording = true
        } catch (_: Exception) {
            errorText = "Tidak dapat memulai rekaman. Periksa izin mikrofon dan penyimpanan."
        }
    }

    fun togglePlay() {
        val file = outputFile ?: return
        if (playing) {
            try { player.pause() } catch (_: Exception) {}
            playing = false
            return
        }
        try {
            player.reset()
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.setOnCompletionListener {
                playing = false
                progress = 0f
            }
            player.start()
            playing = true
        } catch (_: Exception) {
            errorText = "Gagal memutar rekaman."
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            elapsed++
            if (elapsed >= MAX_RECORD_SECONDS) {
                stopRecording()
                errorText = "Batas 10 menit tercapai. Rekaman dihentikan otomatis."
            }
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            delay(200)
            val duration = try { player.duration } catch (_: Exception) { 0 }
            progress = if (duration > 0) player.currentPosition.toFloat() / duration else 0f
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { recorder?.stop() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
            try { player.release() } catch (_: Exception) {}
        }
    }

    AppModal(
        icon = Icons.Filled.Mic,
        title = "Rekam audio",
        subtitle = "Maksimal 10 menit · tersimpan di catatan",
        onClose = onDismiss,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                AppButton("Batal", { if (recording) stopRecording(); onDismiss() }, BtnType.GHOST)
                AppButton(
                    "Sisipkan rekaman",
                    {
                        val bytes = outputFile?.readBytes()
                        if (bytes != null) {
                            val b64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                            onInsert("data:audio/mp4;base64,$b64")
                        }
                    },
                    BtnType.PRIMARY,
                    enabled = hasRecording && !recording
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                formatDuration(elapsed),
                color = if (recording) c.danger else c.text,
                fontSize = 32.sp,
                fontWeight = FontWeight.W700
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (recording) "Merekam…" else if (hasRecording) "Rekaman siap" else "Siap merekam",
                color = c.text3,
                fontSize = 11.5.sp
            )
            Spacer(Modifier.height(14.dp))

            // Visualizer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surface2)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(24) { i ->
                    val height = if (recording) {
                        12 + ((elapsed * 7 + i * 13) % 55)
                    } else if (hasRecording) {
                        10 + ((i * 17) % 30)
                    } else 6
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(height.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (recording) c.danger
                                else if (hasRecording) c.accent
                                else c.borderStrong
                            )
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Tombol rekam / stop
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(if (recording) c.danger else c.accent)
                    .clickable { if (recording) stopRecording() else startRecording() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (recording) "Hentikan rekaman" else "Mulai rekaman",
                    tint = if (recording) androidx.compose.ui.graphics.Color.White else c.accentFg,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                if (recording) "Ketuk untuk menghentikan" else "Ketuk untuk mulai merekam",
                color = c.text3,
                fontSize = 11.sp
            )

            if (hasRecording) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(11.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(c.accentSoft)
                            .clickable { togglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Jeda" else "Putar",
                            tint = c.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.borderStrong)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(c.accent)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    TextActionButton("Rekam ulang", onClick = {
                        try { player.reset() } catch (_: Exception) {}
                        playing = false
                        progress = 0f
                        startRecording()
                    })
                }
            }

            if (errorText != null) {
                Spacer(Modifier.height(10.dp))
                Text(errorText ?: "", color = c.danger, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Rekaman disimpan sebagai bagian dari catatan. Maksimal 10 menit.",
                color = c.text3,
                fontSize = 10.5.sp
            )
        }
    }
}

private fun readFile(context: Context, path: String): File = File(path)
