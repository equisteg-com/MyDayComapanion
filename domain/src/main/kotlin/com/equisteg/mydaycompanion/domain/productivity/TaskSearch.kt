package com.equisteg.mydaycompanion.domain.productivity

import com.equisteg.mydaycompanion.domain.model.Priority
import com.equisteg.mydaycompanion.domain.model.Task
import com.equisteg.mydaycompanion.domain.model.TaskStatus
import java.util.Locale
import java.util.PriorityQueue

/** Local-only search: never sends titles, notes or search terms to a service. */
enum class TaskStateFilter { ALL, OPEN, DONE }

data class TaskSearchResult(val items: List<Task>, val totalMatches: Int) {
    val hasMore: Boolean get() = totalMatches > items.size
}

/**
 * A bounded newest-first search over the owner's tasks, including the backlog.
 * Retains only [limit] results in memory rather than sorting every matching task,
 * so even a large imported database cannot create an unbounded result list.
 */
object TaskSearch {
    const val MAX_RESULTS = 30
    const val MAX_QUERY_CHARS = 120
    private val space = Regex("\\s+")
    private val oldestFirst = compareBy<Task>({ it.updatedAt }, { it.id })

    fun find(
        tasks: List<Task>,
        query: String,
        state: TaskStateFilter = TaskStateFilter.ALL,
        priority: Priority? = null,
        limit: Int = MAX_RESULTS,
    ): TaskSearchResult {
        val terms = query.take(MAX_QUERY_CHARS).trim().lowercase(Locale.ROOT)
            .split(space).filter { it.isNotBlank() }.take(10)
        val cap = limit.coerceIn(1, MAX_RESULTS)
        val top = PriorityQueue<Task>(cap, oldestFirst)
        var matching = 0
        for (task in tasks) {
            if (task.status == TaskStatus.CANCELLED) continue
            if (state == TaskStateFilter.OPEN && task.status != TaskStatus.OPEN) continue
            if (state == TaskStateFilter.DONE && task.status != TaskStatus.DONE) continue
            if (priority != null && task.priority != priority) continue
            if (terms.isNotEmpty()) {
                val searchable = (task.title + " " + task.notes.orEmpty()).lowercase(Locale.ROOT)
                if (terms.any { it !in searchable }) continue
            }
            matching++
            if (top.size < cap) top.add(task)
            else if (oldestFirst.compare(task, top.peek()) > 0) {
                top.poll()
                top.add(task)
            }
        }
        return TaskSearchResult(top.toList().sortedWith(oldestFirst.reversed()), matching)
    }
}
