package app.stickynotes.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stickynotes.data.NotesLayout
import app.stickynotes.data.Settings
import app.stickynotes.data.ThemeMode
import app.stickynotes.spen.PenHelperService
import app.stickynotes.ui.MainViewModel
import app.stickynotes.ui.components.formatReminder
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
            SectionTitle("Gemini & Google Assistant")
            Tip(
                "Say \"Hey Google, open Sticky voice note\", then say your note. It saves by itself. " +
                    "Start with \"shopping list\" or \"to-do list\" for a checklist, and say \"and\" between items.",
            )
            Tip("In Gemini, tap Share under an answer and pick Sticky Notes. Bulleted lists become checklists.")
            Tip("Select text in any app, then choose \"Add to Sticky Notes\" from the menu (it may be under ⋮).")

            Divider()
            SectionTitle("Mealie")
            MealieSection(vm)

            Divider()
            SectionTitle("S Pen")
            PenSection(vm)

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

/** Connect a Mealie server; its shopping lists become checklist notes that sync both ways. */
@Composable
private fun MealieSection(vm: MainViewModel) {
    val state by vm.mealieState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    if (!state.connected) {
        var address by rememberSaveable { mutableStateOf(state.address) }
        var token by rememberSaveable { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Bring your Mealie shopping lists in as checklists. Add, tick or remove items in " +
                    "either app and the other catches up.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it; error = null },
                label = { Text("Mealie address") },
                placeholder = { Text("https://mealie.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; error = null },
                label = { Text("API token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "To get a token: in Mealie, tap your profile picture → API Tokens, give it a name " +
                    "such as Sticky Notes, then Generate and copy it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = !busy && address.isNotBlank() && token.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        error = vm.connectMealie(address, token)
                        busy = false
                        if (error == null) token = ""
                    }
                },
            ) { Text(if (busy) "Connecting…" else "Connect") }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        return
    }

    Column(Modifier.padding(horizontal = 24.dp)) {
        val host = state.address.substringAfter("://").substringBefore('/')
        Text(
            text = "Connected to $host" + if (state.userName.isNotBlank()) " as ${state.userName}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        val error = state.lastError
        Text(
            text = when {
                state.syncing -> "Syncing…"
                error != null -> "Last sync didn't work: $error"
                state.lastSyncAt > 0 -> "Last synced ${formatReminder(state.lastSyncAt)}. Checks every 30 seconds while the app is open, and every 15 minutes in the background."
                else -> "Not synced yet."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null && !state.syncing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.lists.isEmpty()) {
        Text(
            text = "No shopping lists in Mealie yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
    }
    state.lists.forEach { list ->
        SwitchRow(
            title = list.name,
            detail = if (list.enabled) "Synced with a checklist note (label: Mealie)" else "Not synced. Trashing its note also turns this off.",
            checked = list.enabled,
            enabled = true,
            onCheckedChange = { vm.setMealieListEnabled(list.listId, it) },
        )
    }
    Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
        Button(onClick = { vm.syncMealieNow() }, enabled = !state.syncing) { Text("Sync now") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { vm.disconnectMealie() }) { Text("Disconnect") }
    }
}

/** Switches for opening Sticky Notes from the S Pen, plus a live "last seen" line to check it works. */
@Composable
private fun PenSection(vm: MainViewModel) {
    val context = LocalContext.current
    var helperOn by remember { mutableStateOf(PenHelperService.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { helperOn = PenHelperService.isEnabled(context) }
    val openOnRemove by vm.settings.openOnPenRemoved.collectAsStateWithLifecycle()
    val replaceSamsungNotes by vm.settings.replaceSamsungNotes.collectAsStateWithLifecycle()
    val lastEvent by vm.settings.lastPenEvent.collectAsStateWithLifecycle()

    Column(Modifier.padding(horizontal = 24.dp)) {
        if (helperOn) {
            Text(
                text = "S Pen helper is on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "To open Sticky Notes when you pull out the S Pen, turn on \"Sticky Notes S Pen helper\" " +
                    "in Accessibility settings (under Installed apps). It only checks which app is open, " +
                    "never what's on your screen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Accessibility settings") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("App info") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Can't switch it on? Open App info, tap ⋮ at the top right, choose " +
                    "\"Allow restricted settings\", then try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SwitchRow(
        title = "Open a new note when the S Pen is removed",
        detail = "Also set Samsung's Settings → Advanced features → S Pen → When S Pen is removed " +
            "to \"Do nothing\", so its own menu doesn't pop up on top.",
        checked = openOnRemove,
        enabled = helperOn,
        onCheckedChange = { vm.settings.setOpenOnPenRemoved(it) },
    )
    SwitchRow(
        title = "Open instead of Samsung Notes",
        detail = "Whenever Samsung Notes opens, it closes and a new sticky note opens instead. " +
            "Use this if pulling out the pen doesn't show up below: set Samsung's " +
            "\"When S Pen is removed\" to \"Create note\".",
        checked = replaceSamsungNotes,
        enabled = helperOn,
        onCheckedChange = { vm.settings.setReplaceSamsungNotes(it) },
    )

    val event = lastEvent
    Text(
        text = when {
            event == null && helperOn -> "No S Pen activity seen yet. Pull the pen out to test."
            event == null -> "No S Pen activity seen yet."
            event.removed -> "Last seen: S Pen removed, ${formatReminder(event.at)}"
            else -> "Last seen: S Pen put back, ${formatReminder(event.at)}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun Tip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 5.dp),
    )
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
