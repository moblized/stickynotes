package app.stickynotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.stickynotes.data.LabelEntity

/**
 * Small label tags for the bottom of a note card (or under the text in the editor).
 * Colors follow the current content color, so they read well on every note color and in dark mode.
 */
@Composable
fun LabelChips(
    labels: List<LabelEntity>,
    modifier: Modifier = Modifier,
    maxShown: Int = 3,
    onClick: (() -> Unit)? = null,
) {
    if (labels.isEmpty()) return
    val shown = labels.take(maxShown)
    val extra = labels.size - shown.size
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    WrapRow(spacing = 4.dp, modifier = modifier.then(clickModifier)) {
        shown.forEach { LabelChip(it.name) }
        if (extra > 0) LabelChip("+$extra")
    }
}

@Composable
private fun LabelChip(text: String) {
    val ink = LocalContentColor.current
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ink.copy(alpha = 0.85f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(ink.copy(alpha = 0.08f), shape)
            .border(1.dp, ink.copy(alpha = 0.18f), shape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A row that wraps onto new lines when it runs out of width. */
@Composable
internal fun WrapRow(
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }

        var x = 0
        var y = 0
        var rowHeight = 0
        var widest = 0
        val positions = placeables.map { p ->
            if (x > 0 && x + p.width > constraints.maxWidth) {
                x = 0
                y += rowHeight + gap
                rowHeight = 0
            }
            val position = IntOffset(x, y)
            widest = maxOf(widest, x + p.width)
            x += p.width + gap
            rowHeight = maxOf(rowHeight, p.height)
            position
        }
        val height = if (placeables.isEmpty()) 0 else y + rowHeight
        layout(
            width = widest.coerceIn(constraints.minWidth, constraints.maxWidth),
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
            placeables.forEachIndexed { i, p -> p.place(positions[i]) }
        }
    }
}
