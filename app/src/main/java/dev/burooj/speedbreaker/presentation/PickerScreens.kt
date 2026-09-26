package dev.burooj.speedbreaker.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.burooj.speedbreaker.presentation.components.AppGlyph
import dev.burooj.speedbreaker.presentation.components.ContentMaxWidth
import dev.burooj.speedbreaker.presentation.components.Eyebrow
import dev.burooj.speedbreaker.presentation.components.Glyphs
import dev.burooj.speedbreaker.presentation.components.PrimaryAction
import dev.burooj.speedbreaker.presentation.components.QuietAction

/* ---------- Target apps ---------- */

@Composable
internal fun AppPickerScreen(
    apps: List<LaunchableApp>,
    selected: Set<String>,
    onBack: () -> Unit,
    onSetSelected: (LaunchableApp, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Group by what was chosen on arrival, so rows don't jump while toggling.
    val pinned = remember { selected }
    PickerFrame(
        title = "Choose apps",
        subtitle = "Pick the apps that pull at you. Your launcher, keyboards, and safety-critical apps " +
            "can’t be chosen. Choosing one of your redirects clears that set of four.",
        query = query,
        onQuery = { query = it },
        onBack = onBack,
    ) {
        val matches = apps.filter { it.matches(query) }
        val (chosen, others) = matches.partition { it.packageName in pinned }
        val toggle = { app: LaunchableApp -> onSetSelected(app, app.packageName !in selected) }
        val isChecked = { app: LaunchableApp -> app.packageName in selected }
        if (chosen.isNotEmpty()) {
            groupLabel("Chosen")
            appRows(chosen, isChecked, isEnabled = { true }, onToggle = toggle)
        }
        if (others.isNotEmpty()) {
            groupLabel(if (chosen.isEmpty()) "All apps" else "Everything else")
            appRows(others, isChecked, isEnabled = { true }, onToggle = toggle)
        }
        if (matches.isEmpty()) emptyResult(query)
    }
}

/* ---------- Redirects ---------- */

@Composable
internal fun RedirectPickerScreen(
    title: String,
    apps: List<LaunchableApp>,
    initial: List<String>,
    onBack: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    val full = draft.size == REDIRECT_COUNT
    PickerFrame(
        title = title,
        subtitle = "Choose exactly four places to go instead. They’re shown only when all four are set.",
        query = query,
        onQuery = { query = it },
        onBack = onBack,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                QuietAction("Clear", onClick = { onSave(emptyList()) }, enabled = initial.isNotEmpty())
                PrimaryAction(
                    text = if (full) "Save" else "${draft.size} of 4 chosen",
                    onClick = { onSave(draft) },
                    enabled = full,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        val labels = apps.associate { it.packageName to it.label }
        item(key = "slots") {
            ChosenStrip(draft.map { it to (labels[it] ?: it) }) { removed -> draft = draft - removed }
        }
        val matches = apps.filter { it.matches(query) }
        appRows(
            matches,
            isChecked = { it.packageName in draft },
            isEnabled = { it.packageName in draft || !full },
        ) { app ->
            draft = if (app.packageName in draft) draft - app.packageName else draft + app.packageName
        }
        if (matches.isEmpty()) emptyResult(query)
    }
}

@Composable
private fun ChosenStrip(chosen: List<Pair<String, String>>, onRemove: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(REDIRECT_COUNT) { index ->
            val entry = chosen.getOrNull(index)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (entry != null) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 84.dp)
                    .then(
                        if (entry != null) {
                            Modifier.clickable(role = Role.Button, onClickLabel = "Remove ${entry.second}") {
                                onRemove(entry.first)
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Column(
                    Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (entry != null) {
                        AppGlyph(entry.first, entry.second, size = 32.dp)
                        Text(
                            entry.second,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

/* ---------- Shared frame ---------- */

@Composable
private fun PickerFrame(
    title: String,
    subtitle: String,
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
            Box(Modifier.padding(horizontal = 8.dp).heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
                IconButton(onClick = onBack) { Icon(Glyphs.Back, contentDescription = "Back") }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp).semantics { heading() },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            SearchField(query, onQuery, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                content = content,
            )
            if (footer != null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) { footer() }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search apps") },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

private fun LazyListScope.groupLabel(text: String) {
    item(key = "label-$text") {
        Eyebrow(text, Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp))
    }
}

private fun LazyListScope.appRows(
    apps: List<LaunchableApp>,
    isChecked: (LaunchableApp) -> Boolean,
    isEnabled: (LaunchableApp) -> Boolean,
    onToggle: (LaunchableApp) -> Unit,
) {
    items(apps, key = { it.packageName }) { app ->
        val checked = isChecked(app)
        val enabled = isEnabled(app)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) { onToggle(app) }
                .heightIn(min = 64.dp)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyph(app.packageName, app.label)
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            )
            CheckMark(checked)
        }
    }
}

@Composable
private fun CheckMark(checked: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = if (checked) null else androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(26.dp),
    ) {
        if (checked) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Glyphs.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun LazyListScope.emptyResult(query: String) {
    item(key = "empty") {
        Text(
            if (query.isBlank()) "No apps available." else "Nothing matches “$query”.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

private fun LaunchableApp.matches(query: String): Boolean =
    label.contains(query, ignoreCase = true) || packageName.contains(query, ignoreCase = true)

private const val REDIRECT_COUNT = 4
