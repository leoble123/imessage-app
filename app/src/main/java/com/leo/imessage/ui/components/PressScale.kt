package com.leo.imessage.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.leo.imessage.ui.theme.Motion

/**
 * Touch-down scale, kept off the composition.
 *
 * Every one of these in the app used to be `val s by animateFloatAsState(…)`
 * followed by `.scale(s)`. Unwrapping the state with `by` reads it during
 * composition, so each frame of the spring re-runs the composable - and if
 * that composable wraps anything (a bubble, a row, a whole list item) it
 * re-runs all of that too. Handing the State itself to a graphicsLayer block
 * moves the read into the draw phase: the spring then costs one matrix
 * update per frame regardless of what's inside it.
 */
@Composable
fun pressScale(
    pressed: Boolean,
    pressedScale: Float = 0.94f,
    spec: AnimationSpec<Float> = Motion.pressIn(),
    label: String = "pressScale",
): State<Float> = animateFloatAsState(
    targetValue = if (pressed) pressedScale else 1f,
    animationSpec = spec,
    label = label,
)

/** Applies a [pressScale] (or any float State) without recomposing. */
fun Modifier.scaleFrom(state: State<Float>): Modifier = graphicsLayer {
    val s = state.value
    scaleX = s
    scaleY = s
}
