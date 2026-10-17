package app.stickynotes.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.stickynotes.data.ChecklistItem

/**
 * Keep-style list: unchecked items, "+ List item", then the checked ones (collapsible).
 * Enter splits an item into a new one below; checking an item moves it to the checked section.
 */
@Composable
fun ChecklistEditor(
    items: List<ChecklistItem>,
    onItemsChange: (List<ChecklistItem>) -> Unit,
    style: TextStyle,
    paper: Color,
    focusItemId: Long?,
    onRequestFocus: (Long?) -> Unit,
) {
    val ink = LocalContentColor.current
    val open = items.filter { !it.checked }
    val done = items.filter { it.checked }
    var showDone by rememberSaveable { mutableStateOf(true) }

    fun normalized(list: List<ChecklistItem>) = list.filter { !it.checked } + list.filter { it.checked }

    fun update(id: Long, change: (ChecklistItem) -> ChecklistItem) {
        onItemsChange(normalized(items.map { if (it.id == id) change(it) else it }))
    }

    fun remove(id: Long) {
        val index = open.indexOfFirst { it.id == id }
        onItemsChange(items.filter { it.id != id })
        if (index > 0) onRequestFocus(open[index - 1].id)
    }

    fun split(id: Long, before: String, after: String) {
        val newItem = ChecklistItem(text = after)
        val result = mutableListOf<ChecklistItem>()
        items.forEach { item ->
            if (item.id == id) {
                result += item.copy(text = before)
                result += newItem.copy(checked = item.checked)
            } else {
                result += item
            }
        }
        onItemsChange(normalized(result))
        onRequestFocus(newItem.id)
    }

    Column(Modifier.fillMaxWidth()) {
        open.forEach { item ->
            key(item.id) {
                ItemRow(
                    item = item,
                    style = style,
                    ink = ink,
                    paper = paper,
                    focusRequested = focusItemId == item.id,
                    onFocusHandled = { onRequestFocus(null) },
                    onTextChange = { text -> update(item.id) { it.copy(text = text) } },
                    onCheckedChange = { checked -> update(item.id) { it.copy(checked = checked) } },
                    onDelete = { remove(item.id) },
                    onEnter = { before, after -> split(item.id, before, after) },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val newItem = ChecklistItem()
                    onItemsChange(open + newItem + done)
                    onRequestFocus(newItem.id)
                }
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = ink.copy(alpha = 0.7f))
            Spacer(Modifier.width(14.dp))
            Text("List item", style = style, color = ink.copy(alpha = 0.55f))
        }

        if (done.isNotEmpty()) {
            HorizontalDivider(color = ink.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDone = !showDone }
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Icon(
                    if (showDone) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = ink.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = if (done.size == 1) "1 checked item" else "${done.size} checked items",
                    style = MaterialTheme.typography.titleSmall,
                    color = ink.copy(alpha = 0.7f),
                )
            }
            if (showDone) {
                done.forEach { item ->
                    key(item.id) {
                        ItemRow(
                            item = item,
                            style = style,
                            ink = ink,
                            paper = paper,
                            focusRequested = focusItemId == item.id,
                            onFocusHandled = { onRequestFocus(null) },
                            onTextChange = { text -> update(item.id) { it.copy(text = text) } },
                            onCheckedChange = { checked -> update(item.id) { it.copy(checked = checked) } },
                            onDelete = { remove(item.id) },
                            onEnter = { before, after -> split(item.id, before, after) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: ChecklistItem,
    style: TextStyle,
    ink: Color,
    paper: Color,
    focusRequested: Boolean,
    onFocusHandled: () -> Unit,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEnter: (before: String, after: String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(focusRequested) {
        if (focusRequested) {
            runCatching { focusRequester.requestFocus() }
            onFocusHandled()
        }
    }

    val textColor = if (item.checked) ink.copy(alpha = 0.5f) else ink
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = ink.copy(alpha = 0.6f),
                uncheckedColor = ink.copy(alpha = 0.6f),
                checkmarkColor = paper,
            ),
        )
        BasicTextField(
            value = item.text,
            onValueChange = { value ->
                val newline = value.indexOf('\n')
                if (newline >= 0) {
                    onEnter(value.substring(0, newline), value.substring(newline + 1).replace("\n", " "))
                } else {
                    onTextChange(value)
                }
            },
            textStyle = style.copy(
                color = textColor,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            ),
            cursorBrush = SolidColor(ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
        )
        if (focused) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove item", tint = ink.copy(alpha = 0.7f))
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}
