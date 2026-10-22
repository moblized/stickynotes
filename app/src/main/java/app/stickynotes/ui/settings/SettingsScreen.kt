package app.stickynotes.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stickynotes.data.NotesLayout
import app.stickynotes.data.Settings
import app.stickynotes.data.ThemeMode
import app.stickynotes.ui.MainViewModel
import app.stickynotes.ui.theme.NoteFont
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onOpenDrawer: () -> Unit) {
    val theme by vm.settings.theme.collectAsStateWithLifecycle()
    val fontName by vm.settings.font.collectAsStateWithLifecycle()
    val undoSeconds by vm.settings.undoSeconds.collectAsStateWithLifecycle()
    val layout by vm.settings.layout.collectAsStateWithLifecycle()
    val pureBlack by vm.settings.pureBlack.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            SectionTitle("Theme")
            ThemeMode.entries.forEach { mode ->
                ChoiceRow(
                    selected = theme == mode,
                    onClick = { vm.setTheme(mode) },
                ) { Text(mode.label, style = MaterialTheme.typography.bodyLarge) }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = pureBlack,
                        role = Role.Switch,
                        onValueChange = { vm.settings.setPureBlack(it) },
                    )
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pure black", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (theme == ThemeMode.LIGHT) {
                            "Black background in dark mode. Switch the theme to Dark or Follow system to see it."
                        } else {
                            "Black background in dark mode. Saves battery on OLED screens."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = pureBlack, onCheckedChange = null)
            }

            Divider()
            SectionTitle("Handwriting")
            NoteFont.entries.forEach { font ->
                ChoiceRow(
                    selected = fontName == font.name,
                    onClick = { vm.setFont(font.name) },
                ) {
                    Column {
                        Text(font.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Milk, eggs & a little note to self",
                            style = TextStyle(fontFamily = font.family, fontSize = (19 * font.scale).sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Divider()
            SectionTitle("Undo bar")
            var sliderValue by remember(undoSeconds) { mutableFloatStateOf(undoSeconds.toFloat()) }
            Text(
                text = "Show Undo for ${sliderValue.roundToInt()} seconds after deleting or archiving",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { vm.setUndoSeconds(sliderValue.roundToInt()) },
                valueRange = Settings.MIN_UNDO_SECONDS.toFloat()..Settings.MAX_UNDO_SECONDS.toFloat(),
                steps = Settings.MAX_UNDO_SECONDS - Settings.MIN_UNDO_SECONDS - 1,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Divider()
            SectionTitle("Notes layout")
            NotesLayout.entries.forEach { option ->
                ChoiceRow(
                    selected = layout == option,
                    onClick = { vm.settings.setLayout(option) },
                ) {
                    Text(
                        text = if (option == NotesLayout.GRID) "Grid (2 columns)" else "List (1 column)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Divider()
            SectionTitle("About")
            Text(
                text = "Sticky Notes $version\nSwipe a note to archive it. Long-press and drag to reorder. " +
                    "Deleted notes stay in Trash for 7 days.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        content()
    }
}
