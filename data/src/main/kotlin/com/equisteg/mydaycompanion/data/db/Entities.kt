package com.equisteg.mydaycompanion.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Dates/times are stored as ISO-8601 strings so exported databases stay readable and
// migrations do not depend on converter classes.

@Entity(tableName = "tasks", indices = [Index("date"), Index("parentId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val priority: String,
    val durationMinutes: Int,
    val date: String?,
    val deadline: String?,
    val status: String,
    val parentId: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "fixed_events", indices = [Index("date")])
data class FixedEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val date: String,
    val start: String,
    val durationMinutes: Int,
    val source: String,
)

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val date: String,
    val plannerVersion: String,
    /** Comma-separated task ids. */
    val unscheduledTaskIds: String,
    /** One issue per line: TYPE|refId|message. */
    val issues: String,
)

@Entity(tableName = "plan_blocks", primaryKeys = ["date", "position"])
data class PlanBlockEntity(
    val date: String,
    val position: Int,
    val refId: String,
    val title: String,
    val kind: String,
    val start: String,
    val end: String,
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val title: String,
    val frequency: String,
    val preferredStart: String?,
    val durationMinutes: Int,
    val enabled: Boolean,
    val createdAt: String,
)

@Entity(tableName = "memories", indices = [Index("status")])
data class MemoryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val content: String,
    val source: String,
    val confidence: Double,
    val status: String,
    val sensitivity: String,
    val retention: String,
    val visibility: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

@Entity(tableName = "audit_log", indices = [Index("timestamp")])
data class AuditEntity(
    @PrimaryKey val id: String,
    val timestamp: String,
    val actor: String,
    val action: String,
    val riskLevel: String?,
    val decision: String,
    val outcome: String,
    val reasonCode: String?,
    val detail: String?,
)

@Entity(tableName = "preferences")
data class PreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val dayStart: String,
    val dayEnd: String,
    val bufferMinutes: Int,
    val speechOutputEnabled: Boolean,
)
