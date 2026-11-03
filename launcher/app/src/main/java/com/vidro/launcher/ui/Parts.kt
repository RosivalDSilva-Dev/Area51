package com.vidro.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidro.launcher.data.AppEntry
import com.vidro.launcher.data.GlassTuning
import com.vidro.launcher.glass.Backdrop
import com.vidro.launcher.glass.GlassShaders
import com.vidro.launcher.glass.GlassSurface
import com.vidro.launcher.glass.LocalGlassStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 6f),
)

/* ---------------- Papel de parede ---------------- */

@Composable
fun Wallpaper(image: androidx.compose.ui.graphics.ImageBitmap?) {
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    } else {
        DefaultWallpaper()
    }
}

/** Papel de parede padrão: cores vivas e formas nítidas, para a refração aparecer bem. */
@Composable
fun DefaultWallpaper() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFF0B1B48), Color(0xFF3B1A6E), Color(0xFF0D4F63)),
                start = Offset.Zero,
                end = Offset(w, h),
            )
        )
        fun blob(c: Color, x: Float, y: Float, r: Float) {
            drawCircle(
                Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center = Offset(x, y), radius = r),
                radius = r,
                center = Offset(x, y),
            )
        }
        blob(Color(0xFFFF5E7E), w * 0.15f, h * 0.22f, w * 0.75f)
        blob(Color(0xFFFFB347), w * 0.95f, h * 0.45f, w * 0.65f)
        blob(Color(0xFF3CE0C2), w * 0.20f, h * 0.80f, w * 0.70f)
        blob(Color(0xFF5B7CFF), w * 0.85f, h * 0.95f, w * 0.60f)
        // anéis nítidos: são eles que denunciam a lente nas bordas do vidro
        val ring = Stroke(width = w * 0.018f)
        drawCircle(Color.White.copy(alpha = 0.55f), radius = w * 0.30f, center = Offset(w * 0.78f, h * 0.18f), style = ring)
        drawCircle(Color(0xFFFFE066).copy(alpha = 0.8f), radius = w * 0.16f, center = Offset(w * 0.26f, h * 0.52f), style = ring)
        drawCircle(Color.White.copy(alpha = 0.35f), radius = w * 0.42f, center = Offset(w * 0.55f, h * 0.88f), style = ring)
    }
}

/* ---------------- Ícone de app ---------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppEntry,
    modifier: Modifier = Modifier,
    iconSize: Dp = 60.dp,
    showLabel: Boolean = true,
    onClick: (Rect) -> Unit,
    onLongClick: (Rect) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "escala",
    )
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onClick(bounds) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(bounds)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(app.icon, contentDescription = app.label, modifier = Modifier.size(iconSize))
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                style = LabelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(iconSize + 18.dp),
            )
        }
    }
}

/* ---------------- Relógio ---------------- */

@Composable
fun ClockWidget(backdrop: Backdrop, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val pt = remember { Locale.forLanguageTag("pt-BR") }
    val time = remember(now / 60000) { SimpleDateFormat("HH:mm", pt).format(Date(now)) }
    val date = remember(now / 3_600_000) {
        SimpleDateFormat("EEEE, d 'de' MMMM", pt).format(Date(now)).replaceFirstChar { it.uppercase() }
    }
    GlassSurface(backdrop, modifier, cornerRadius = 32.dp) {
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 24.dp),
        ) {
            Text(
                time,
                style = LabelStyle.copy(fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
            )
            Text(date, style = LabelStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
        }
    }
}

/* ---------------- Menu do app (toque longo) ---------------- */

enum class MenuSource { HOME, DOCK, DRAWER }

data class MenuRequest(val app: AppEntry, val anchor: Rect, val source: MenuSource)

data class MenuAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val run: () -> Unit)

@Composable
fun AppMenuOverlay(
    backdrop: Backdrop,
    request: MenuRequest,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
    ) {
        Layout(
            content = {
                GlassSurface(
                    backdrop,
                    Modifier.width(250.dp),
                    style = LocalGlassStyle.current.copy(
                        blur = 14.dp,
                        tint = Color.White.copy(alpha = 0.16f),
                    ),
                    cornerRadius = 26.dp,
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            request.app.label,
                            style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f)),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        actions.forEach { a ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        a.run()
                                        onDismiss()
                                    }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val c = if (a.danger) Color(0xFFFF6B6B) else Color.White
                                Text(a.label, style = LabelStyle.copy(fontSize = 16.sp, color = c), modifier = Modifier.weight(1f))
                                Icon(a.icon, contentDescription = null, tint = c, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val margin = with(density) { 16.dp.roundToPx() }
            val gap = with(density) { 10.dp.roundToPx() }
            val a = request.anchor
            val x = (a.center.x - p.width / 2f).roundToInt()
                .coerceIn(margin, (constraints.maxWidth - p.width - margin).coerceAtLeast(margin))
            val below = a.bottom.roundToInt() + gap
            val above = a.top.roundToInt() - gap - p.height
            val y = if (below + p.height < constraints.maxHeight - margin * 4) below else above.coerceAtLeast(margin)
            layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
        }
    }
}

fun menuActions(
    request: MenuRequest,
    inHome: Boolean,
    inDock: Boolean,
    onToggleHome: () -> Unit,
    onToggleDock: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
): List<MenuAction> = buildList {
    add(
        if (inHome) MenuAction(Icons.Rounded.Close, "Remover da tela inicial") { onToggleHome() }
        else MenuAction(Icons.Rounded.Add, "Adicionar à tela inicial") { onToggleHome() }
    )
    add(
        if (inDock) MenuAction(Icons.Rounded.Close, "Tirar do dock") { onToggleDock() }
        else MenuAction(Icons.Rounded.Star, "Colocar no dock") { onToggleDock() }
    )
    add(MenuAction(Icons.Rounded.Info, "Informações do app") { onInfo() })
    add(MenuAction(Icons.Rounded.Delete, "Desinstalar", danger = true) { onUninstall() })
}

/* ---------------- Painel de calibração (toque longo no papel de parede) ---------------- */

@Composable
fun TuningSheet(
    backdrop: Backdrop,
    tuning: GlassTuning,
    onChange: (GlassTuning) -> Unit,
    onPickWallpaper: () -> Unit,
    onSystemWallpaper: () -> Unit,
    onResetWallpaper: () -> Unit,
    onResetTuning: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
    ) {
        GlassSurface(
            backdrop,
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            style = LocalGlassStyle.current.copy(blur = 16.dp, tint = Color.Black.copy(alpha = 0.22f)),
            cornerRadius = 34.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("Calibrar o vidro", style = LabelStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
                GlassShaders.lastError?.let {
                    Text("Shader falhou, usando só desfoque: $it", style = LabelStyle.copy(color = Color(0xFFFF8A80)))
                }
                Spacer(Modifier.height(6.dp))
                TuneRow("Refração", tuning.refraction, 0f..60f) { onChange(tuning.copy(refraction = it)) }
                TuneRow("Largura da borda", tuning.bevel, 4f..60f) { onChange(tuning.copy(bevel = it)) }
                TuneRow("Desfoque", tuning.blur, 0f..30f) { onChange(tuning.copy(blur = it)) }
                TuneRow("Dispersão (arco-íris)", tuning.dispersion, 0f..0.8f) { onChange(tuning.copy(dispersion = it)) }
                TuneRow("Brilho da borda", tuning.highlight, 0f..1.5f) { onChange(tuning.copy(highlight = it)) }
                TuneRow("Leitosidade", tuning.tint, 0f..0.5f) { onChange(tuning.copy(tint = it)) }
                TuneRow("Saturação", tuning.saturation, 0.5f..2.5f) { onChange(tuning.copy(saturation = it)) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Papel de parede", Modifier.weight(1f), onPickWallpaper)
                    Chip("Usar o do celular", Modifier.weight(1f), onSystemWallpaper)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Fundo padrão", Modifier.weight(1f), onResetWallpaper)
                    Chip("Vidro padrão", Modifier.weight(1f), onResetTuning)
                }
                Spacer(Modifier.height(8.dp))
                Chip("Definir como tela inicial padrão", Modifier.fillMaxWidth(), onSetDefaultLauncher)
            }
        }
    }
}

@Composable
private fun TuneRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = LabelStyle.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
            Text(
                if (range.endInclusive > 5f) value.roundToInt().toString() else "%.2f".format(value),
                style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)),
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.9f),
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier.height(32.dp),
        )
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}
