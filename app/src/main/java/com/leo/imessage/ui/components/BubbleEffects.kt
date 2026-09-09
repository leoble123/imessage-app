package com.leo.imessage.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.leo.imessage.data.MessageEffect
import com.leo.imessage.ui.theme.Motion
import kotlinx.coroutines.launch

/**
 * Plays iMessage's send effects when a bubble first appears.
 *
 * These are deliberately physical rather than decorative: SLAM lands hard and
 * rebounds, LOUD swells past its size before settling, GENTLE creeps up from
 * nothing. Each one runs once, on first composition of that message.
 */
@Composable
fun Modifier.bubbleEffect(effect: MessageEffect, messageId: String): Modifier {
    if (effect == MessageEffect.NONE) return this

    val scale = remember(messageId) { Animatable(1f) }
    val rotation = remember(messageId) { Animatable(0f) }
    val alpha = remember(messageId) { Animatable(1f) }

    LaunchedEffect(messageId) {
        when (effect) {
            MessageEffect.SLAM -> {
                scale.snapTo(2.6f)
                rotation.snapTo(-7f)
                alpha.snapTo(0.4f)
                launch { alpha.animateTo(1f, tween(120)) }
                launch { rotation.animateTo(0f, Motion.bouncy()) }
                scale.animateTo(
                    1f,
                    keyframes {
                        durationMillis = 520
                        2.6f at 0
                        0.86f at 180
                        1.06f at 300
                        0.98f at 400
                        1f at 520
                    },
                )
            }

            MessageEffect.LOUD -> {
                scale.snapTo(0.8f)
                launch { alpha.animateTo(1f, tween(80)) }
                launch {
                    rotation.animateTo(
                        0f,
                        keyframes {
                            durationMillis = 700
                            0f at 0
                            -3f at 220
                            3f at 320
                            -2f at 420
                            0f at 700
                        },
                    )
                }
                scale.animateTo(
                    1f,
                    keyframes {
                        durationMillis = 700
                        0.8f at 0
                        1.45f at 260
                        1.12f at 420
                        1f at 700
                    },
                )
            }

            MessageEffect.GENTLE -> {
                scale.snapTo(0.35f)
                alpha.snapTo(0.5f)
                launch { alpha.animateTo(1f, tween(600)) }
                scale.animateTo(1f, tween(760, easing = Motion.AppleEase))
            }

            MessageEffect.INVISIBLE_INK -> {
                alpha.snapTo(0.06f)
                alpha.animateTo(1f, tween(1400, easing = Motion.AppleEase))
            }

            MessageEffect.NONE -> Unit
        }
    }

    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        rotationZ = rotation.value
        this.alpha = alpha.value
        transformOrigin = TransformOrigin(0.5f, 1f)
    }
}
