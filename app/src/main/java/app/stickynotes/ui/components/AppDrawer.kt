package app.stickynotes.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stickynotes.data.LabelEntity
import app.stickynotes.ui.NoteSection
import app.stickynotes.ui.Root
import app.stickynotes.ui.theme.LocalNoteTypography

/** Keep's side drawer: Notes, Reminders, your labels, Edit labels, Archive, Trash, Settings. */
@Composable
fun AppDrawer(
    labels: List<LabelEntity>,
    current: Root,
    onNavigate: (Root) -> Unit,
) {
    ModalDrawerSheet {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Sticky Notes",
                style = LocalNoteTypography.current.title,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            )
            Item("Notes", Icons.Outlined.Lightbulb, current, Root.Section(NoteSection.Notes), onNavigate)
            Item("Reminders", Icons.Outlined.Notifications, current, Root.Section(NoteSection.Reminders), onNavigate)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (labels.isNotEmpty()) {
                Text(
                    text = "Labels",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            labels.forEach { label ->
                Item(
                    text = label.name,
                    icon = Icons.AutoMirrored.Outlined.Label,
                    current = current,
                    target = Root.Section(NoteSection.Label(label.id)),
                    onNavigate = onNavigate,
                )
            }
            Item(
                text = if (labels.isEmpty()) "Create new label" else "Edit labels",
                icon = if (labels.isEmpty()) Icons.Outlined.Add else Icons.Outlined.Edit,
                current = current,
                target = Root.EditLabels,
                onNavigate = onNavigate,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Item("Archive", Icons.Outlined.Archive, current, Root.Section(NoteSection.Archive), onNavigate)
            Item("Trash", Icons.Outlined.Delete, current, Root.Trash, onNavigate)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Item("Settings", Icons.Outlined.Settings, current, Root.Settings, onNavigate)
        }
    }
}

@Composable
private fun Item(
    text: String,
    icon: ImageVector,
    current: Root,
    target: Root,
    onNavigate: (Root) -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(icon, contentDescription = null) },
        selected = current == target,
        onClick = { onNavigate(target) },
    )
}
