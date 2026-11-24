package com.catatan.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catatan.app.ui.theme.CatatanTheme
import com.catatan.app.ui.theme.rgba
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/* ============================================================
   Tombol & ikon
   ============================================================ */

enum class BtnType { PRIMARY, GHOST, DANGER, DANGER_GHOST, NEUTRAL }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    type: BtnType = BtnType.NEUTRAL,
    icon: ImageVector? = null,
    small: Boolean = false,
    block: Boolean = false,
    enabled: Boolean = true
) {
    val c = CatatanTheme.colors
    val bg = when (type) {
        BtnType.PRIMARY -> c.accent
        BtnType.GHOST -> Color.Transparent
        BtnType.DANGER -> c.danger
        BtnType.DANGER_GHOST -> c.dangerSoft
        BtnType.NEUTRAL -> c.surface2
    }
    val fg = when (type) {
        BtnType.PRIMARY -> c.accentFg
        BtnType.GHOST -> c.text2
        BtnType.DANGER -> Color.White
        BtnType.DANGER_GHOST -> c.danger
        BtnType.NEUTRAL -> c.text
    }
    val borderColor = when (type) {
        BtnType.GHOST -> c.border
        BtnType.NEUTRAL -> Color.Transparent
        else -> Color.Transparent
    }
    val h = if (small) 34.dp else 40.dp
    val shape = RoundedCornerShape(if (small) 9.dp else 11.dp)

    Row(
        modifier = Modifier
            .then(if (block) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = h)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 7.dp else 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text,
            color = if (enabled) fg else fg.copy(alpha = 0.45f),
            fontSize = if (small) 12.5.sp else 14.sp,
            fontWeight = FontWeight.W600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    size: Dp = 38.dp,
    active: Boolean = false,
    tint: Color? = null,
    enabled: Boolean = true
) {
    val c = CatatanTheme.colors
    val background = if (active) c.accentSoft else Color.Transparent
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint ?: if (active) c.accentStrong else c.text2,
            modifier = Modifier.size((size.value * 0.53f).dp)
        )
    }
}

@Composable
fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    accent: Boolean = false
) {
    val c = CatatanTheme.colors
    Text(
        text,
        color = if (accent) c.accentStrong else c.text2,
        fontSize = 13.sp,
        fontWeight = FontWeight.W600,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

/* ============================================================
   App bar
   ============================================================ */

@Composable
fun CatatanTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    centerContent: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null
) {
    val c = CatatanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg.copy(alpha = 0.92f))
            .statusBarsPadding()
            .height(62.dp)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        navigationIcon?.invoke()
        if (centerContent != null) {
            centerContent()
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 6.dp)
            ) {
                Text(
                    title,
                    color = c.text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = c.text3,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.W400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        actions()
    }
}

/* ============================================================
   Chip / badge
   ============================================================ */

@Composable
fun Pill(
    label: String,
    count: Int? = null,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null
) {
    val c = CatatanTheme.colors
    val bg = if (active) c.accentSoft else c.surface
    val border = if (active) c.accent.copy(alpha = 0.3f) else c.border
    val fg = if (active) c.accentStrong else c.text2

    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(99.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.W500, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                count.toString(),
                color = if (active) fg else c.text3,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.W600
            )
        }
    }
}

@Composable
fun CountBadge(text: String, danger: Boolean = false) {
    val c = CatatanTheme.colors
    Box(
        modifier = Modifier
            .height(19.dp)
            .widthIn(min = 20.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(if (danger) c.dangerSoft else c.surface3)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (danger) c.danger else c.text3,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.W600
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = CatatanTheme.colors
    Text(
        text.uppercase(),
        color = c.text3,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.7.sp,
        modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    val c = CatatanTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.border)
    )
}

/* ============================================================
   Switch kustom (46 x 27)
   ============================================================ */

@Composable
fun AppSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CatatanTheme.colors
    val trackColor = if (checked) c.accent else c.surface3
    val thumbColor = Color.White

    Box(
        modifier = modifier
            .width(46.dp)
            .height(27.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onChange(!checked) }
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(23.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

/* ============================================================
   Baris pengaturan
   ============================================================ */

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val c = CatatanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.W500)
            if (subtitle != null) {
                Text(subtitle, color = c.text3, fontSize = 11.5.sp, lineHeight = 15.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked = checked, onChange = onChange)
    }
}

@Composable
fun SegmentedRow(
    title: String,
    subtitle: String? = null,
    options: List<Pair<String, String>>,   // value -> label
    value: String,
    onChange: (String) -> Unit
) {
    val c = CatatanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.W500)
        if (subtitle != null) {
            Text(subtitle, color = c.text3, fontSize = 11.5.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(9.dp))
        SegmentedControl(options = options, value = value, onChange = onChange)
    }
}

@Composable
fun SegmentedControl(
    options: List<Pair<String, String>>,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CatatanTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .padding(3.dp)
    ) {
        options.forEach { (optValue, label) ->
            val active = optValue == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) c.surface else Color.Transparent)
                    .clickable { onChange(optValue) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) c.text else c.text3,
                    fontSize = 11.5.sp,
                    fontWeight = if (active) FontWeight.W600 else FontWeight.W500
                )
            }
        }
    }
}

@Composable
fun SliderRow(
    title: String,
    subtitle: String? = null,
    value: Int,
    range: IntRange,
    suffix: String = "",
    onChange: (Int) -> Unit
) {
    val c = CatatanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.W500)
                if (subtitle != null) {
                    Text(subtitle, color = c.text3, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
            }
            Text(
                "$value$suffix",
                color = c.accentStrong,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.W600
            )
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.surface3
            )
        )
    }
}

@Composable
fun LinkRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val c = CatatanTheme.colors
    val fg = if (danger) c.danger else c.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(33.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (danger) c.dangerSoft else c.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) c.danger else c.accentStrong,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.W500)
            if (subtitle != null) {
                Text(subtitle, color = c.text3, fontSize = 11.5.sp, maxLines = 2)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = c.text3,
            modifier = Modifier.size(18.dp)
        )
    }
}

/* ============================================================
   Bottom sheet / modal
   ============================================================ */

@Composable
fun AppModal(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    maxWidth: Dp = 560.dp,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = CatatanTheme.colors
    val screenH = LocalConfiguration.current.screenHeightDp.dp

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
                .imePadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .heightIn(max = screenH - 16.dp),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                color = c.surface,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 15.dp, end = 8.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier
                                    .size(37.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(c.accentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = c.accentStrong,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                color = c.text,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.W700,
                                letterSpacing = (-0.3).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle != null) {
                                Text(
                                    subtitle,
                                    color = c.text3,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        AppIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "Tutup",
                            onClick = onClose,
                            size = 36.dp
                        )
                    }
                    AppDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        content()
                    }
                    if (footer != null) {
                        AppDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, top = 11.dp, end = 14.dp, bottom = 11.dp)
                                .navigationBarsPadding()
                        ) {
                            footer()
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ModalFooterButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    cancelText: String = "Batal",
    onCancel: () -> Unit,
    confirmType: BtnType = BtnType.PRIMARY,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End)
    ) {
        AppButton(cancelText, onCancel, BtnType.GHOST)
        AppButton(confirmText, onConfirm, confirmType, enabled = enabled)
    }
}

/* ============================================================
   Empty state
   ============================================================ */

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    val c = CatatanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(c.accentSofter)
                .border(1.dp, c.border, RoundedCornerShape(27.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = c.accentStrong, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            color = c.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.W700,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text,
            color = c.text3,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp)
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            AppButton(actionText, onAction, BtnType.PRIMARY, icon = Icons.Filled.Add)
        }
        if (secondaryText != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            AppButton(secondaryText, onSecondary, BtnType.GHOST, small = true)
        }
    }
}

/* ============================================================
   Progress bar & toast
   ============================================================ */

@Composable
fun ThinProgress(progress: Float, large: Boolean = false) {
    val c = CatatanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (large) 7.dp else 5.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(c.text3.copy(alpha = 0.16f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(99.dp))
                .background(c.accent)
        )
    }
}

@Composable
fun ToastHost(state: AppState) {
    val toast = state.toast
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(toast.duration)
            state.dismissToast()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 66.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 }
        ) {
            if (toast != null) ToastView(toast)
        }
    }
}

@Composable
private fun ToastView(data: ToastData) {
    val c = CatatanTheme.colors
    val icon = when (data.kind) {
        "error" -> Icons.Filled.ErrorOutline
        "info" -> Icons.Filled.Info
        else -> Icons.Filled.CheckCircleOutline
    }
    val tint = when (data.kind) {
        "error" -> c.danger
        "info" -> c.accentStrong
        else -> c.success
    }
    Row(
        modifier = Modifier
            .widthIn(max = 430.dp)
            .padding(horizontal = 16.dp)
            .shadow(10.dp, RoundedCornerShape(11.dp))
            .clip(RoundedCornerShape(11.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            data.text,
            color = c.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.W500,
            lineHeight = 17.sp
        )
    }
}

/* ============================================================
   util kecil
   ============================================================ */

fun alpha(color: Color, a: Float): Color = color.copy(alpha = a)

fun accentRgba(hex: String, a: Float): Color = rgba(hex, a)
