package com.leo.imessage.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

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
 * Dragging the transcript downward puts the keyboard away.
 *
 * The obvious implementation is [imeNestedScroll], which hands leftover
 * scroll to the IME in both directions - and that is exactly wrong here: the
 * moment you reach the bottom of a thread, the overscroll it can no longer
 * absorb goes straight into summoning a keyboard nobody asked for. Scrolling
 * to the end of a conversation is the most ordinary thing you can do in a
 * messaging app and it should not open anything.
 *
 * So this is one-way on purpose. Only a deliberate downward drag dismisses;
 * the keyboard is opened by tapping the field or flicking up on the composer,
 * both of which are things you have to mean.
 */
@Composable
fun rememberKeyboardDismissConnection(): NestedScrollConnection {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    return remember(keyboard, focusManager, density) {
        val thresholdPx = with(density) { 18.dp.toPx() }
        object : NestedScrollConnection {
            private var travel = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Downward finger movement is positive y here.
                if (available.y > 0) {
                    travel += available.y
                    if (travel > thresholdPx) {
                        travel = 0f
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                } else {
                    travel = 0f
                }
                return Offset.Zero
            }
        }
    }
}
