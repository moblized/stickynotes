package app.stickynotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Swipe a note left or right to archive it (or unarchive it, in Archive).
 * A quick swipe archives; long-press-and-drag still reorders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToArchive(
    isArchived: Boolean,
    onSwiped: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val latestOnSwiped by rememberUpdatedState(onSwiped)

    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) {
            latestOnSwiped()
            // Normally the card leaves the list and this never runs. If it's still here, slide it back.
            delay(800)
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        backgroundContent = { SwipeBackground(state.dismissDirection, isArchived) },
    ) {
        content()
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, isArchived: Boolean) {
    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }
    if (direction == SwipeToDismissBoxValue.Settled) return
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        contentAlignment = alignment,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                contentDescription = null,
                tint = ink,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isArchived) "Unarchive" else "Archive",
                style = MaterialTheme.typography.labelLarge,
                color = ink,
                maxLines = 1,
            )
        }
    }
}
