package app.stickynotes.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.stickynotes.data.ImageStore
import app.stickynotes.data.LabelEntity
import app.stickynotes.data.Note
import app.stickynotes.data.NoteImageEntity
import app.stickynotes.data.hasText
import app.stickynotes.ui.theme.LocalDarkTheme
import app.stickynotes.ui.theme.LocalNoteTypography
import app.stickynotes.ui.theme.NoteColor
import app.stickynotes.ui.theme.foldColor
import app.stickynotes.ui.theme.noteInk
import kotlin.math.min

private const val MAX_CARD_ITEMS = 8

/** Rectangle with the bottom-right corner cut off, where the paper curls up. */
class FoldedCornerShape(private val fold: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val f = with(density) { fold.toPx() }.coerceAtMost(min(size.width, size.height) / 3f)
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - f)
            lineTo(size.width - f, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Each note leans a little, like a real sticky note. Same note, same angle. */
fun tiltFor(id: Long): Float {
    val h = (id * 2654435761L) ushr 13
    return ((h % 5L).toInt() - 2) * 0.55f
}

/**
 * A sticky note: colored paper, a slight tilt, an adhesive strip at the top,
 * a curled corner and a soft shadow. [lifted] makes it rise while it's being dragged.
 */
@Composable
fun NoteCard(
    note: Note,
    labels: List<LabelEntity>,
    images: List<NoteImageEntity>,
    imageStore: ImageStore,
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
    lifted: Boolean = false,
    onClick: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val dark = LocalDarkTheme.current
    val typo = LocalNoteTypography.current
    val paper = NoteColor.of(note.color).color(dark)
    val fold = foldColor(paper, dark)
    val ink = noteInk(dark)
    val foldSize = 18.dp
    val shape = remember { FoldedCornerShape(foldSize) }
    val tilt = remember(note.id) { tiltFor(note.id) }

    val elevation by animateDpAsState(
        targetValue = when {
            lifted -> 14.dp
            elevated -> 3.dp
            else -> 0.dp
        },
        label = "noteElevation",
    )
    val scale by animateFloatAsState(if (lifted) 1.04f else 1f, label = "noteScale")
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    CompositionLocalProvider(LocalContentColor provides ink) {
        Box(
            modifier
                .graphicsLayer {
                    rotationZ = if (lifted) tilt * 2.5f else tilt
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.6f),
                )
                .clip(shape)
                .background(paper)
                .then(clickModifier)
                .drawWithContent {
                    drawContent()
                    // Adhesive strip: the top of a sticky note is a touch darker.
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = if (dark) 0.10f else 0.045f),
                            1f to Color.Transparent,
                            startY = 0f,
                            endY = 14.dp.toPx(),
                        ),
                        size = Size(size.width, 14.dp.toPx()),
                    )
                    // Curled corner.
                    val f = foldSize.toPx().coerceAtMost(min(size.width, size.height) / 3f)
                    val w = size.width
                    val h = size.height
                    val corner = Path().apply {
                        moveTo(w - f, h)
                        lineTo(w - f, h - f)
                        lineTo(w, h - f)
                        close()
                    }
                    drawPath(
                        path = corner,
                        brush = Brush.linearGradient(
                            colors = listOf(fold, paper.copy(alpha = 1f)),
                            start = Offset(w - f, h - f),
                            end = Offset(w - f * 0.35f, h - f * 0.35f),
                        ),
                    )
                    drawLine(
                        color = Color.Black.copy(alpha = if (dark) 0.35f else 0.14f),
                        start = Offset(w - f, h),
                        end = Offset(w, h - f),
                        strokeWidth = 1.2.dp.toPx(),
                    )
                },
        ) {
            Column(Modifier.fillMaxWidth()) {
                NoteCardImage(images, imageStore)
                val hasActions = onTogglePin != null || onDelete != null
                val hasContent = note.hasText() || note.reminderAt != null || labels.isNotEmpty()
                if (hasContent || images.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = if (hasActions) 2.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CardText(note, ink)
                        note.reminderAt?.let { ReminderTag(it, ink) }
                        if (labels.isNotEmpty()) LabelChips(labels, Modifier.padding(top = 2.dp))
                    }
                }
                if (hasActions) {
                    // Pin and delete, kept clear of the curled corner.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp, end = 24.dp, top = 2.dp, bottom = 4.dp),
                    ) {
                        if (onTogglePin != null) {
                            CardAction(
                                icon = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                description = if (note.isPinned) "Unpin note" else "Pin note",
                                tint = ink.copy(alpha = if (note.isPinned) 0.9f else 0.55f),
                                onClick = onTogglePin,
                            )
                        }
                        if (onDelete != null) {
                            CardAction(
                                icon = Icons.Outlined.Delete,
                                description = "Delete note",
                                tint = ink.copy(alpha = 0.55f),
                                onClick = onDelete,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A small round button on a card. Smaller than a normal icon button so the note stays the focus. */
@Composable
private fun CardAction(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun CardText(note: Note, ink: Color) {
    val typo = LocalNoteTypography.current
    if (note.title.isNotBlank()) {
        Text(
            text = note.title.trim(),
            style = typo.cardTitle,
            color = ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (!note.isChecklist) {
        if (note.body.isNotBlank()) {
            Text(
                text = note.body.trim(),
                style = typo.cardBody,
                color = ink,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (note.title.isBlank()) {
            Text("Empty note", style = typo.cardBody, color = ink.copy(alpha = 0.45f))
        }
        return
    }
    val open = note.items.filter { !it.checked && it.text.isNotBlank() }
    val done = note.items.count { it.checked && it.text.isNotBlank() }
    open.take(MAX_CARD_ITEMS).forEach { item ->
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = ink.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = item.text.trim(),
                style = typo.cardBody,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (open.size > MAX_CARD_ITEMS) {
        Text("…", style = typo.cardBody, color = ink.copy(alpha = 0.6f))
    }
    if (done > 0) {
        Text(
            text = if (done == 1) "+ 1 checked item" else "+ $done checked items",
            style = MaterialTheme.typography.labelMedium,
            color = ink.copy(alpha = 0.6f),
        )
    }
    if (open.isEmpty() && done == 0 && note.title.isBlank()) {
        Text("Empty list", style = typo.cardBody, color = ink.copy(alpha = 0.45f))
    }
}

@Composable
fun ReminderTag(at: Long, ink: Color, modifier: Modifier = Modifier) {
    val past = at < System.currentTimeMillis()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(ink.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(
            Icons.Outlined.Alarm,
            contentDescription = "Reminder",
            tint = ink.copy(alpha = if (past) 0.5f else 0.85f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = formatReminder(at),
            style = MaterialTheme.typography.labelSmall,
            color = ink.copy(alpha = if (past) 0.5f else 0.85f),
            textDecoration = if (past) TextDecoration.LineThrough else null,
            maxLines = 1,
        )
    }
}

/** Small spacer used under section headers. */
@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))
