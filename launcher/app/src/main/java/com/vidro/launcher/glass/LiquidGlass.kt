package com.vidro.launcher.glass

import android.content.Context
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Vidro líquido no estilo do iOS 26, recriado com um shader AGSL.
 *
 * Como funciona:
 *  1. Tudo que fica "atrás do vidro" (papel de parede + ícones) é gravado numa
 *     GraphicsLayer (o "backdrop").
 *  2. Cada peça de vidro desenha o pedaço do backdrop que está embaixo dela e
 *     aplica, nesta ordem: desfoque -> lente (refração, dispersão, saturação,
 *     tinta e brilho da borda), tudo calculado no shader abaixo.
 */

private const val GLASS_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uRadius;
uniform float uBevel;
uniform float uRefract;
uniform float uDispersion;
uniform float2 uLight;
uniform float uHighlight;
uniform float4 uTint;
uniform float uSaturation;
uniform float uBrightness;

// distância com sinal até a borda de um retângulo arredondado (negativo = dentro)
float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 fragCoord) {
    float2 h = uSize * 0.5;
    float2 p = fragCoord - h;
    float r = min(uRadius, min(h.x, h.y));
    float d = sdRoundRect(p, h, r);
    if (d > 1.0) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }

    // normal da borda (gradiente da SDF)
    float dx = sdRoundRect(p + float2(1.0, 0.0), h, r) - sdRoundRect(p - float2(1.0, 0.0), h, r);
    float dy = sdRoundRect(p + float2(0.0, 1.0), h, r) - sdRoundRect(p - float2(0.0, 1.0), h, r);
    float2 n = float2(dx, dy);
    float nl = length(n);
    if (nl > 0.0001) {
        n = n / nl;
    } else {
        n = float2(0.0, 0.0);
    }

    // perfil do bisel: superfície circular, a curvatura forte fica junto da borda
    float inside = -d;
    float t = clamp(inside / max(uBevel, 1.0), 0.0, 1.0);
    float k = 1.0 - t;
    float bend = 1.0 - sqrt(max(1.0 - k * k, 0.0));
    float2 off = -n * bend * uRefract;

    // refração com dispersão cromática (cada canal desvia um pouco diferente)
    float4 cG = float4(content.eval(fragCoord + off));
    float4 col = cG;
    if (uDispersion > 0.001) {
        float4 cR = float4(content.eval(fragCoord + off * (1.0 + uDispersion)));
        float4 cB = float4(content.eval(fragCoord + off * (1.0 - uDispersion)));
        col = float4(cR.r, cG.g, cB.b, cG.a);
    }

    // saturação, brilho e tinta
    float lum = dot(col.rgb, float3(0.2126, 0.7152, 0.0722));
    float3 rgb = mix(float3(lum, lum, lum), col.rgb, uSaturation) * uBrightness;
    rgb = mix(rgb, uTint.rgb, uTint.a);

    // brilho especular: filete fino na borda + reflexo largo no bisel,
    // mais forte nas bordas viradas para a luz (e na borda oposta, como no iOS)
    float rim = exp(-inside / 1.6);
    float facing = abs(dot(n, uLight));
    float spec = uHighlight * (rim * (0.30 + 0.70 * facing) + 0.22 * bend * facing);
    rgb = clamp(rgb + float3(spec, spec, spec), 0.0, 1.0);

    // antisserrilhado da borda
    float a = clamp(0.5 - d, 0.0, 1.0);
    return half4(float4(rgb * a, a));
}
"""

object GlassShaders {
    var lastError: String? = null
        private set

    fun create(): RuntimeShader? = try {
        RuntimeShader(GLASS_AGSL)
    } catch (t: Throwable) {
        lastError = t.message
        Log.e("Vidro", "Shader não compilou", t)
        null
    }
}

/** Ajustes do vidro. Os valores padrão foram escolhidos para lembrar o iOS 26. */
@Immutable
data class GlassStyle(
    val cornerRadius: Dp = 28.dp,
    val bevel: Dp = 20.dp,
    val refraction: Dp = 24.dp,
    val blur: Dp = 3.dp,
    val dispersion: Float = 0.22f,
    val highlight: Float = 0.55f,
    val tint: Color = Color.White.copy(alpha = 0.06f),
    val saturation: Float = 1.35f,
    val brightness: Float = 1.04f,
)

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }
val LocalGlassLight = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset(-0.35f, -0.94f)) }

/** O que fica atrás do vidro. */
class Backdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Marca um elemento como fonte do backdrop: ele é desenhado normalmente e também gravado. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

private fun buildEffect(
    shader: RuntimeShader?,
    w: Float,
    h: Float,
    radiusPx: Float,
    bevelPx: Float,
    refractPx: Float,
    blurPx: Float,
    style: GlassStyle,
    light: Offset,
): RenderEffect {
    val blur = if (blurPx > 0.5f) {
        android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
    } else {
        null
    }
    if (shader == null) {
        return (blur ?: android.graphics.RenderEffect.createOffsetEffect(0f, 0f)).asComposeRenderEffect()
    }
    shader.setFloatUniform("uSize", w, h)
    shader.setFloatUniform("uRadius", radiusPx)
    shader.setFloatUniform("uBevel", bevelPx)
    shader.setFloatUniform("uRefract", refractPx)
    shader.setFloatUniform("uDispersion", style.dispersion)
    shader.setFloatUniform("uLight", light.x, light.y)
    shader.setFloatUniform("uHighlight", style.highlight)
    shader.setFloatUniform("uTint", style.tint.red, style.tint.green, style.tint.blue, style.tint.alpha)
    shader.setFloatUniform("uSaturation", style.saturation)
    shader.setFloatUniform("uBrightness", style.brightness)
    val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
    val effect = if (blur != null) android.graphics.RenderEffect.createChainEffect(lens, blur) else lens
    return effect.asComposeRenderEffect()
}

/**
 * Uma superfície de vidro líquido. O conteúdo (ícones, textos) é desenhado por cima do vidro.
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    cornerRadius: Dp = style.cornerRadius,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val light = LocalGlassLight.current
    val shader = remember { GlassShaders.create() }
    var pos by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Box(
        modifier.onGloballyPositioned {
            pos = it.positionInRoot()
            size = it.size
        }
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w > 0f && h > 0f) {
                        val radiusPx = cornerRadius.toPx()
                        renderEffect = buildEffect(
                            shader = shader,
                            w = w,
                            h = h,
                            radiusPx = radiusPx,
                            bevelPx = minOf(style.bevel.toPx(), minOf(w, h) * 0.5f),
                            refractPx = style.refraction.toPx(),
                            blurPx = style.blur.toPx(),
                            style = style,
                            light = light.value,
                        )
                    }
                    this.shape = shape
                    clip = true
                }
                .drawBehind {
                    val o = pos - backdrop.origin
                    translate(-o.x, -o.y) {
                        drawLayer(backdrop.layer)
                    }
                }
        )
        content()
    }
}

/** Direção da luz guiada pela gravidade: o brilho da borda "escorre" quando você inclina o celular. */
@Composable
fun rememberGravityLight(): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset(-0.35f, -0.94f)) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        var lx = state.value.x
        var ly = state.value.y
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gx = event.values[0] / SensorManager.GRAVITY_EARTH
                val gy = event.values[1] / SensorManager.GRAVITY_EARTH
                var x = -gx * 1.2f - 0.25f
                var y = -gy - 0.15f
                val len = sqrt(x * x + y * y)
                if (len < 0.05f) return
                x /= len
                y /= len
                lx += (x - lx) * 0.18f
                ly += (y - ly) * 0.18f
                val cur = state.value
                if (abs(cur.x - lx) + abs(cur.y - ly) > 0.015f) {
                    state.value = Offset(lx, ly)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}
