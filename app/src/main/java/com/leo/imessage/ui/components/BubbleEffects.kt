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
 * Tracks which messages have already played their send effect.
 *
 * Without this, a LazyColumn re-composes items as they scroll back into view
 * and every old bubble replays its effect - so scrolling a thread becomes a
 * slideshow of slamming bubbles. Effects are a one-time event per message
 * for the life of the process, not a property of being on screen.
 */
private val playedEffects = mutableSetOf<String>()

/**
 * Plays iMessage's send effects the first time a message appears.
 *
 * SLAM lands hard and rebounds, LOUD swells past its size before settling,
 * GENTLE creeps up from nothing, INVISIBLE INK fades in from a smudge.
 */
@Composable
fun Modifier.bubbleEffect(effect: MessageEffect, messageId: String): Modifier {
    if (effect == MessageEffect.NONE) return this

    val alreadyPlayed = remember(messageId) { messageId in playedEffects }
    if (alreadyPlayed) return this

    val scale = remember(messageId) { Animatable(1f) }
    val rotation = remember(messageId) { Animatable(0f) }
    val alpha = remember(messageId) { Animatable(1f) }

    LaunchedEffect(messageId) {
        playedEffects += messageId
        when (effect) {
            MessageEffect.SLAM -> {
                scale.snapTo(2.4f)
                rotation.snapTo(-6f)
                alpha.snapTo(0.5f)
                launch { alpha.animateTo(1f, tween(110)) }
                launch { rotation.animateTo(0f, Motion.bouncy()) }
                scale.animateTo(
                    1f,
                    keyframes {
                        durationMillis = 460
                        2.4f at 0
                        0.9f at 170
                        1.04f at 290
                        1f at 460
                    },
                )
            }

            MessageEffect.LOUD -> {
                scale.snapTo(0.85f)
                launch { alpha.animateTo(1f, tween(80)) }
                launch {
                    rotation.animateTo(
                        0f,
                        keyframes {
                            durationMillis = 620
                            0f at 0
                            -2.5f at 210
                            2.5f at 310
                            0f at 620
                        },
                    )
                }
                scale.animateTo(
                    1f,
                    keyframes {
                        durationMillis = 620
                        0.85f at 0
                        1.35f at 240
                        1.08f at 390
                        1f at 620
                    },
                )
            }

            MessageEffect.GENTLE -> {
                scale.snapTo(0.4f)
                alpha.snapTo(0.5f)
                launch { alpha.animateTo(1f, tween(560)) }
                scale.animateTo(1f, tween(700, easing = Motion.AppleEase))
            }

            MessageEffect.INVISIBLE_INK -> {
                alpha.snapTo(0.06f)
                alpha.animateTo(1f, tween(1300, easing = Motion.AppleEase))
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
