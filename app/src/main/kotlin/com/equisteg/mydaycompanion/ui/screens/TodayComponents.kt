package com.equisteg.mydaycompanion.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.equisteg.mydaycompanion.domain.insights.DayCount
import com.equisteg.mydaycompanion.domain.insights.Insights
import com.equisteg.mydaycompanion.domain.insights.NowNext
import com.equisteg.mydaycompanion.domain.model.BlockKind
import com.equisteg.mydaycompanion.domain.model.DayPlan
import com.equisteg.mydaycompanion.domain.model.PlanBlock
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** "Good morning" and friends, by the phone's time. */
fun greeting(now: LocalTime): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello"
}

/** A ring that fills as the day's tasks are done; it animates when a task is ticked. */
@Composable
fun ProgressRing(fraction: Float, modifier: Modifier = Modifier, label: @Composable () -> Unit) {
    val progress by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = tween(700), label = "progress")
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = 9.dp.toPx()
            drawArc(track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(stroke))
            drawArc(fill, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        label()
    }
}

/** The top card of My day: how much of the selected day is done and, for today, what's on now and next. */
@Composable
fun DayHero(count: DayCount, nowNext: NowNext?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth().testTag("day-hero"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
    ) {
        Row(
            Modifier
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)))
                .padding(20.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ProgressRing(
                count.fraction,
                Modifier.semantics { contentDescription = "${count.done} of ${count.planned} tasks done" },
            ) {
                Text("${(count.fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        count.planned == 0 -> "Nothing planned yet"
                        count.done == count.planned -> "All ${count.planned} done. Nice work!"
                        else -> "${count.done} of ${count.planned} done"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("hero-progress"),
                )
                nowNext?.now?.let { NowNextLine("Now", it, colors.primary) }
                nowNext?.next?.let { NowNextLine("Next", it, colors.secondary) }
                if (nowNext != null && nowNext.now == null && nowNext.next == null && count.planned > 0) {
                    Text("Nothing else planned today.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NowNextLine(label: String, block: PlanBlock, accent: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = accent) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${block.start} ${block.title}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("hero-${label.lowercase()}"),
        )
    }
}

/**
 * The plan as a timeline: a coloured bar per block (teal for tasks, apricot for fixed events),
 * done tasks fade and strike through, and the block running now is marked.
 */
@Composable
fun Timeline(
    plan: DayPlan,
    statusById: Map<String, TaskStatus>,
    onToggle: ((String) -> Unit)?,
    tag: String,
    now: LocalTime? = null,
    onEdit: ((String) -> Unit)? = null,
) {
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        plan.blocks.forEach { block -> TimelineRow(block, statusById[block.refId] == TaskStatus.DONE, onToggle, now, onEdit) }
        plan.issues.forEach { Text("⚠ ${it.message}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun TimelineRow(block: PlanBlock, done: Boolean, onToggle: ((String) -> Unit)?, now: LocalTime?, onEdit: ((String) -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val isTask = block.kind == BlockKind.TASK
    val current = now != null && block.start <= now && now < block.end && !done
    val accent by animateColorAsState(
        when {
            done -> colors.outlineVariant
            isTask -> colors.primary
            else -> colors.tertiary
        },
        label = "accent",
    )
    val titleColor by animateColorAsState(if (done) colors.onSurfaceVariant.copy(alpha = 0.6f) else colors.onSurface, label = "title")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (current) colors.secondaryContainer else colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(52.dp)) {
                Text("${block.start}", style = MaterialTheme.typography.labelLarge)
                Text("${block.end}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f).then(if (onEdit != null && isTask) Modifier.clickable { onEdit(block.refId) } else Modifier),
            ) {
                Text(
                    block.title,
                    color = titleColor,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    listOfNotNull(if (isTask) "Task" else "Event", if (current) "now" else null).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (onToggle != null && isTask) {
                Checkbox(checked = done, onCheckedChange = { onToggle(block.refId) })
            }
        }
    }
}

/** Seven small bars for the week, with the streak and today's finished minutes (on-phone only). */
@Composable
fun InsightsCard(insights: Insights, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth().testTag("insights"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your week", style = MaterialTheme.typography.titleMedium)
            val maxPlanned = insights.week.maxOf { maxOf(it.planned, 1) }
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                insights.week.forEach { day ->
                    val fill by animateFloatAsState(day.done.toFloat() / maxPlanned, animationSpec = tween(600), label = "bar")
                    val track = day.planned.toFloat() / maxPlanned
                    val isToday = day.date == insights.today.date
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Canvas(Modifier.width(14.dp).height(72.dp)) {
                            val w = size.width
                            val r = androidx.compose.ui.geometry.CornerRadius(w / 2, w / 2)
                            val trackH = size.height * track.coerceAtLeast(0.06f)
                            drawRoundRect(colors.outlineVariant.copy(alpha = 0.5f), topLeft = Offset(0f, size.height - trackH), size = androidx.compose.ui.geometry.Size(w, trackH), cornerRadius = r)
                            if (fill > 0f) {
                                val fillH = size.height * fill
                                drawRoundRect(if (isToday) colors.primary else colors.secondary, topLeft = Offset(0f, size.height - fillH), size = androidx.compose.ui.geometry.Size(w, fillH), cornerRadius = r)
                            }
                        }
                        Text(
                            day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                listOfNotNull(
                    "${insights.doneThisWeek} done this week",
                    insights.streakDays.takeIf { it > 0 }?.let { if (it == 1) "1-day streak" else "$it-day streak" },
                    insights.doneMinutesToday.takeIf { it > 0 }?.let { "$it min finished today" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("insights-summary"),
            )
        }
    }
}
