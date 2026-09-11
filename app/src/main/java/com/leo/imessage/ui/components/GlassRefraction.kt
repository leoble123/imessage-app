package com.leo.imessage.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leo.imessage.ui.theme.LocalSettings

/**
 * Glass that actually bends light.
 *
 * Everything else in this app called glass is a translucent fill with a rim
 * painted on it. That is a picture of glass. This is the behaviour: a real
 * pane is thicker at the edges than in the middle, so what you see through it
 * is undisturbed across the centre and bent increasingly hard as you approach
 * the rim - the background compresses into the edge, the boundary picks up a
 * faint colour fringe because the bending is wavelength-dependent, and a
 * bright line appears where the curve happens to face the light.
 *
 * All three come out of one idea and are computed per pixel, on the GPU, from
 * the actual pixels behind the pane:
 *
 *  - **Refraction.** A signed distance field gives the distance from every
 *    pixel to the pane's edge. Near the edge the surface is treated as
 *    curving away, so the sample is displaced inward along the surface
 *    normal, by an amount that rises as the cube of how close to the rim you
 *    are. That cube is what makes it read as a lens rather than a smudge:
 *    almost nothing happens until the last few pixels, and then it happens
 *    fast.
 *  - **Dispersion.** Red, green and blue are sampled at slightly different
 *    displacements, because glass does not bend them equally. This is the
 *    detail the eye cannot name and always notices; it is why a rendered
 *    bevel with no fringe reads as plastic.
 *  - **Specular.** The same normal, dotted with a light direction, lights the
 *    edge that faces the light and darkens the one facing away. The pane
 *    gains a top and a bottom, which is most of what makes it read as having
 *    thickness rather than being a hole cut in the screen.
 *
 * Requires AGSL, which is Android 13. Below that the modifier is a no-op and
 * the painted rim carries the look on its own - it is a garnish on something
 * that already works, never the thing holding it up.
 */
@Composable
fun Modifier.refractiveGlass(
    /** Must match the shape the pane is clipped to, or the edge is in the wrong place. */
    cornerRadius: Dp,
    /** How far in from the edge the pane is still curving. */
    band: Dp = 16.dp,
    /** How far the background is dragged at the very rim. */
    refraction: Dp = 10.dp,
    /** How far apart the three channels are pulled. 0 is colourless glass. */
    dispersion: Float = 0.30f,
    /** Strength of the lit and shaded edges. */
    specular: Float = 0.75f,
    /** Where the light is, in screen terms. Up and slightly left, as ever. */
    lightX: Float = -0.45f,
    lightY: Float = -0.89f,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    if (!LocalSettings.current.glassRefraction) return this

    // One shader per pane, not one shared: uniforms live on the instance, so
    // a shared one would have every pane's size overwriting every other's.
    // Compilation is wrapped because a driver that rejects the program must
    // cost the look, never the screen.
    val shader = remember { runCatching { RuntimeShader(AGSL) }.getOrNull() } ?: return this
    // Plain array rather than state: this is written from the draw phase and
    // must not schedule a recomposition, and nothing needs to re-read it
    // except the next frame of the same pane.
    val broken = remember { booleanArrayOf(false) }

    return this.graphicsLayer {
        if (broken[0]) return@graphicsLayer
        val w = size.width
        val h = size.height
        // A pane with no area has no edge to refract, and the shader would
        // divide by its half-size looking for one.
        if (w < 1f || h < 1f) return@graphicsLayer

        // Everything measured against the pane rather than fixed: a sixteen
        // point band is a tasteful bevel on a conversation row and is most of
        // a forty point pill, which would leave a control with no flat middle
        // and a background smeared across the whole of it.
        val shortest = minOf(w, h)
        val bandPx = band.toPx().coerceAtMost(shortest * 0.32f)
        val refractPx = refraction.toPx().coerceAtMost(bandPx * 0.62f)

        // Guarded because this runs on every frame of every pane: a driver
        // that rejects the program has to cost the effect, not the screen.
        // One failure retires it rather than throwing sixty times a second.
        runCatching {
            shader.setFloatUniform("size", w, h)
            shader.setFloatUniform("radius", cornerRadius.toPx().coerceIn(0f, shortest / 2f))
            shader.setFloatUniform("band", bandPx)
            shader.setFloatUniform("refraction", refractPx)
            shader.setFloatUniform("dispersion", dispersion)
            shader.setFloatUniform("specular", specular)
            shader.setFloatUniform("light", lightX, lightY)

            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }.onFailure {
            broken[0] = true
            renderEffect = null
        }
    }
}

private const val AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float band;
uniform float refraction;
uniform float dispersion;
uniform float specular;
uniform float2 light;

// Signed distance to a rounded rectangle centred on the origin. Negative
// inside, and the magnitude is the distance to the nearest edge - which is
// the only thing this whole effect needs to know about the shape.
float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

// The surface normal, as the gradient of that field. Cheaper and steadier
// than special-casing the corners.
float2 surfaceNormal(float2 p, float2 halfSize, float r) {
    float e = 1.0;
    float dx = sdRoundRect(p + float2(e, 0.0), halfSize, r)
             - sdRoundRect(p - float2(e, 0.0), halfSize, r);
    float dy = sdRoundRect(p + float2(0.0, e), halfSize, r)
             - sdRoundRect(p - float2(0.0, e), halfSize, r);
    float2 g = float2(dx, dy);
    float len = length(g);
    if (len < 0.0001) { return float2(0.0, -1.0); }
    return g / len;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float d = sdRoundRect(p, halfSize, radius);

    // Outside the pane, and across its flat middle, the glass does nothing at
    // all. That is the point of a lens: it is only interesting at the edge.
    if (d >= 0.0 || band <= 0.0) { return content.eval(coord); }
    float t = 1.0 - clamp(-d / band, 0.0, 1.0);
    if (t <= 0.0) { return content.eval(coord); }

    float2 n = surfaceNormal(p, halfSize, radius);

    // Cubed, so the bend is imperceptible until the last few pixels and then
    // arrives all at once - the way an actual bevel behaves.
    float bevel = t * t * t;
    float2 push = -n * bevel * refraction;

    // Three wavelengths, three amounts of bending.
    float2 rUv = coord + push * (1.0 + dispersion);
    float2 gUv = coord + push;
    float2 bUv = coord + push * (1.0 - dispersion);

    half4 cr = content.eval(rUv);
    half4 cg = content.eval(gUv);
    half4 cb = content.eval(bUv);
    half4 col = half4(cr.r, cg.g, cb.b, cg.a);

    // Where the curve faces the light it catches it; where it faces away it
    // loses it. Confined tightly to the rim, because a highlight that
    // reaches the middle is a gradient, not a highlight.
    float rim = t * t * t * t * t * t;
    float lit = pow(clamp(dot(n, light), 0.0, 1.0), 3.0) * rim * specular;
    float away = pow(clamp(dot(n, -light), 0.0, 1.0), 2.0)
               * (t * t * t * t) * specular * 0.45;

    col.rgb = clamp(col.rgb + half3(lit) * col.a - half3(away) * col.a,
                    half3(0.0), half3(col.a));
    return col;
}
"""
