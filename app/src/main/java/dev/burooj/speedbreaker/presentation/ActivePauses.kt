package dev.burooj.speedbreaker.presentation

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.burooj.speedbreaker.model.PauseState
import dev.burooj.speedbreaker.presentation.components.AppGlyph
import dev.burooj.speedbreaker.presentation.components.QuietAction
import dev.burooj.speedbreaker.presentation.components.RowDivider
import dev.burooj.speedbreaker.presentation.components.RowFrame
import dev.burooj.speedbreaker.presentation.components.RowText
import dev.burooj.speedbreaker.presentation.components.Section
import dev.burooj.speedbreaker.presentation.components.appCatalog
import java.util.Date

/** A pause remains controllable even after its notification has been dismissed. */
@Composable
internal fun ActivePausesSection(
    pauses: Map<String, PauseState>,
    nowEpochMs: Long,
    onEndPause: (String) -> Unit,
) {
    val active = pauses.filterValues { it.untilEpochMs > nowEpochMs }
    if (active.isEmpty()) return
    val catalog = appCatalog()
    val timeFormat = DateFormat.getTimeFormat(LocalContext.current)
    Section(title = "Active pauses", footnote = "Ending a pause doesn’t return its daily token.") {
        active.entries.sortedBy { catalog.label(it.key).lowercase() }
            .forEachIndexed { index, (packageName, pause) ->
                key(packageName) {
                    if (index > 0) RowDivider()
                    val label = catalog.label(packageName)
                    RowFrame(leading = { AppGlyph(packageName, label) }) {
                        // Stacking keeps the action reachable with large text and narrow screens.
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            RowText(label, "Paused until ${timeFormat.format(Date(pause.untilEpochMs))}")
                            QuietAction(
                                text = "End now",
                                onClick = { onEndPause(packageName) },
                                modifier = Modifier.semantics { contentDescription = "End pause for $label" },
                            )
                        }
                    }
                }
            }
    }
}
