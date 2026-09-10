package com.leo.imessage.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.leo.imessage.ui.theme.LocalPalette
import com.leo.imessage.ui.theme.Motion

/**
 * iOS's segmented control. The selected pill slides between options on a
 * spring rather than cutting, which is the whole character of the control.
 */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    var totalWidth by remember { mutableIntStateOf(0) }
    val segmentWidth = if (options.isEmpty()) 0f else totalWidth.toFloat() / options.size

    val offset by animateFloatAsState(
        targetValue = segmentWidth * index,
        animationSpec = Motion.standard(),
        label = "segmentSlide",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(palette.fieldBackground)
            .onSizeChanged { totalWidth = it.width }
            .padding(2.dp),
    ) {
        if (segmentWidth > 0f) {
            val pillWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
                segmentWidth.toDp()
            }
            Box(
                Modifier
                    .graphicsLayer { translationX = offset }
                    .fillMaxHeight()
                    .width(pillWidth)
                    .clip(RoundedCornerShape(7.dp))
                    .background(palette.surfaceElevated)
            )
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { (value, label) ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (value == selected) palette.label else palette.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
