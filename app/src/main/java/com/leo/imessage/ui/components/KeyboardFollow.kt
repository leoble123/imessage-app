package com.leo.imessage.ui.components

import android.os.Build
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/**
 * Keeps the transcript pinned to the keyboard as it opens and closes.
 *
 * Padding the composer above the IME is only half the job: the list's
 * viewport shrinks by the same amount, and because a LazyColumn anchors to
 * the *top*, everything at the bottom - the message you were just reading,
 * the one you swiped to reply to - slides under the keyboard instead of
 * moving up out of its way.
 *
 * Scrolling by exactly the inset delta each frame cancels that out: content
 * holds its position on screen and the whole transcript rides up with the
 * keyboard, which is what iOS does and why nothing there ever gets eaten.
 * Driven off snapshotFlow rather than a keyed effect so it tracks every
 * frame of the IME animation, not just its endpoints.
 */
@Composable
fun ScrollWithKeyboard(listState: LazyListState) {
    val density = LocalDensity.current
    val insets = WindowInsets.ime

    LaunchedEffect(listState) {
        var previous = insets.getBottom(density)
        snapshotFlow { insets.getBottom(density) }.collect { current ->
            val delta = current - previous
            previous = current
            if (delta != 0) listState.scrollBy(delta.toFloat())
        }
    }
}

/**
 * Drag the transcript to pull the keyboard in and push it away.
 *
 * iOS lets you flick the conversation down to dismiss the keyboard and drag
 * back up to bring it in, with the keyboard tracking your finger the whole
 * way rather than snapping at the end of the gesture. Android exposes the
 * same thing through the IME animation-control API, which
 * [imeNestedScroll] drives - the scroll container hands unconsumed drag to
 * the IME instead of over-scrolling into nothing.
 *
 * Pairs with [ScrollWithKeyboard]: that keeps content pinned as the inset
 * changes, this decides when the inset changes. Together the transcript
 * holds still on screen while the keyboard slides under your finger.
 *
 * Requires the animation-control API (API 30+); below that the modifier is
 * simply not applied and the keyboard keeps its default behaviour.
 */
@OptIn(ExperimentalLayoutApi::class)
fun Modifier.dragToToggleKeyboard(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) this.imeNestedScroll() else this
