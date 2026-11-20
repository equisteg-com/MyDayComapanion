package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Consistent Material 3 hero shared by every main tab. Content is intentionally compact
 * so text scaling and small devices do not lose the most important controls.
 */
@Composable
fun PremiumPageHero(
    section: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    footnote: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(colors.primary, colors.secondary)))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("premium-header-${section.lowercase()}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "MYDAY  /  ${section.uppercase()}",
                color = colors.onPrimary.copy(alpha = 0.88f),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                title,
                color = colors.onPrimary,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                subtitle,
                color = colors.onPrimary.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (footnote != null) {
                Text(
                    footnote,
                    color = colors.onPrimary.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Surface(shape = CircleShape, color = colors.onPrimary.copy(alpha = 0.16f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

/**
 * Visible, task-oriented controls rather than decorative quick-action buttons.
 * Each callback is wired to an existing real planner operation.
 */
@Composable
fun PlannerCommandCenter(
    onWeek: () -> Unit,
    onFind: () -> Unit,
    onReplan: () -> Unit,
    onAsk: (String) -> Unit,
    busy: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().testTag("planner-command-center"),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("COMMAND CENTER  ·  Offline tools",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onWeek,
                    modifier = Modifier.testTag("premium-open-week"),
                ) { Text("7-day view") }
                OutlinedButton(
                    onClick = onFind,
                    modifier = Modifier.testTag("premium-open-search"),
                ) { Text("Find tasks") }
                OutlinedButton(
                    onClick = onReplan,
                    enabled = !busy,
                    modifier = Modifier.testTag("premium-replan"),
                ) { Text("Replan") }
                OutlinedButton(
                    onClick = { onAsk("show my deadlines") },
                    enabled = !busy,
                    modifier = Modifier.testTag("premium-deadlines"),
                ) { Text("Deadlines") }
                OutlinedButton(
                    onClick = { onAsk("week summary") },
                    enabled = !busy,
                    modifier = Modifier.testTag("premium-week-summary"),
                ) { Text("Weekly insight") }
            }
        }
    }
}

@Composable
fun RoutinePremiumHero(active: Int, total: Int) =
    PremiumPageHero(
        section = "Routines",
        title = "Make it a habit.",
        subtitle = "Automate the things that matter, without relying on a server.",
        icon = Icons.Filled.Refresh,
        footnote = "$active active of $total routines",
    )

@Composable
fun MemoryPremiumHero(remembered: Int, waiting: Int) =
    PremiumPageHero(
        section = "Memory",
        title = "Made for you.",
        subtitle = "Your assistant only remembers what you approve.",
        icon = Icons.Filled.Star,
        footnote = "$remembered kept · $waiting waiting for review",
    )

@Composable
fun HistoryPremiumHero(count: Int) =
    PremiumPageHero(
        section = "History",
        title = "Stay in control.",
        subtitle = "A private record of actions and decisions on this device.",
        icon = Icons.Filled.Info,
        footnote = "$count visible entries",
    )

@Composable
fun SettingsPremiumHero() =
    PremiumPageHero(
        section = "Settings",
        title = "Your space.",
        subtitle = "Appearance, privacy, security, voice and backups.",
        icon = Icons.Filled.Settings,
        footnote = "MyDayCompanion 0.2.0 · Local-first",
    )

@Composable
fun PlanPremiumHero(dateLabel: String) =
    PremiumPageHero(
        section = "Planner",
        title = "Own your day.",
        subtitle = "Plan, prioritize and adapt with an assistant that works on your phone.",
        icon = Icons.Filled.DateRange,
        footnote = dateLabel,
    )
