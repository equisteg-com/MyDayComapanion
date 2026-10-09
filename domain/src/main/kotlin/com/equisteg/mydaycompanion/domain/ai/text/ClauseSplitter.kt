package com.equisteg.mydaycompanion.domain.ai.text

import com.equisteg.mydaycompanion.domain.ai.text.TextSpans.rx
import com.equisteg.mydaycompanion.domain.safety.ContentSafety

/**
 * Splits one message into items without breaking items that merely contain "and" or a list:
 *  - always on new lines, ";", bullets and sentence ends;
 *  - on "," unless the piece just continues a shopping-style list ("buy eggs, milk, bread");
 *  - on "and" / "then" / "also" / "plus" only when what follows starts a new action
 *    ("call Raj and pay the bill" splits, "meeting with Tom and Jerry at 3" does not).
 */
object ClauseSplitter {

    fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        for (sentence in sentences(text)) {
            val pieces = mutableListOf<String>()
            for (raw in sentence.split(',')) {
                val piece = raw.trim()
                if (piece.isEmpty()) continue
                val prev = pieces.lastOrNull()
                if (prev != null && continuesList(prev, piece)) {
                    pieces[pieces.lastIndex] = "$prev, $piece"
                } else {
                    pieces += piece
                }
            }
            pieces.flatMapTo(out) { splitOnConnectors(it) }
        }
        mergeBareWhen(out)
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** True when [s] begins a new item: an action verb, a pronoun lead-in, a date or a time. */
    fun startsAction(s: String): Boolean {
        val t = s.trim().replace(softLead, "").lowercase()
        if (t.isEmpty()) return false
        if (pronounLead.containsMatchIn(t) || timeLead.containsMatchIn(t)) return true
        val first = t.split(wordBreak, limit = 2).first()
        if (first in verbs) return true
        return eventNoun.containsMatchIn(t) && t.any { it.isDigit() }
    }

    private fun sentences(text: String): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val atBoundary = i + 1 >= text.length || text[i + 1].isWhitespace()
            when {
                c == '\n' || c == ';' || c == '•' -> { parts += sb.toString(); sb.clear() }
                (c == '.' || c == '!' || c == '?') && atBoundary && !endsWithAbbreviation(sb) -> {
                    sb.append(c); parts += sb.toString(); sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        parts += sb.toString()
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun endsWithAbbreviation(sb: StringBuilder): Boolean {
        val tail = sb.takeLast(5)
        return abbreviation.containsMatchIn(tail) || amPm.containsMatchIn(tail)
    }

    /** "at 5 pm, call mom": a piece that is only a time or date belongs to the item after it. */
    private fun mergeBareWhen(items: MutableList<String>) {
        var i = items.size - 2
        while (i >= 0) {
            if (isOnlyWhen(items[i])) {
                items[i] = items[i] + " " + items[i + 1]
                items.removeAt(i + 1)
            }
            i--
        }
    }

    private fun isOnlyWhen(piece: String): Boolean {
        val times = TimeParser.findAll(piece)
        if (times.isEmpty()) return false
        val rest = TextSpans.cut(piece, times.map { it.range } + whenDates.findAll(piece, spanDay).map { it.range })
        return rest.replace(fillerOnly, "").isBlank()
    }

    private fun continuesList(prev: String, piece: String): Boolean {
        val words = piece.trim().split(wordBreak).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 3) return false
        if (startsAction(piece) || connectorLead.containsMatchIn(piece) && startsAction(piece.replace(connectorLead, ""))) return false
        if (ContentSafety.looksLikeInstructionToAssistant(piece) || ContentSafety.looksLikeInstructionToAssistant(prev)) return false
        if (TimeParser.findAll(piece).isNotEmpty()) return false
        val prevFirstVerb = prev.lowercase().replace(leadIns, "").trim().split(wordBreak).firstOrNull()
        return prevFirstVerb in listVerbs
    }

    private fun splitOnConnectors(piece: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        for (m in connector.findAll(piece)) {
            val left = piece.substring(start, m.range.first)
            val right = piece.substring(m.range.last + 1)
            if (left.isBlank()) continue
            if (m.value.trim().equals("and", ignoreCase = true) && betweenTail.containsMatchIn(left)) continue
            if (!startsAction(right)) continue
            result += left
            start = m.range.last + 1
        }
        result += piece.substring(start)
        return result.map { it.trim().replace(connectorLead, "").trim() }.filter { it.isNotEmpty() }
    }

    private val wordBreak = Regex("[\\s:]+")
    private val whenDates = DateParser()
    private val spanDay = java.time.LocalDate.of(2026, 1, 1)
    private val fillerOnly = rx("[\\s,.:;!?-]|\\b(?:and|then|at|on|by|from|around)\\b")
    private val abbreviation = rx("\\b(?:mr|mrs|ms|dr|st|vs|no)$")
    private val amPm = rx("[ap]\\.m\\.?$")
    private val connector = rx("\\s+(?:and\\s+then|and\\s+also|and|then|also|plus|after\\s+that|afterwards)\\s+")
    private val connectorLead = rx("^(?:(?:and|then|also|plus|after\\s+that|afterwards|so)\\s+)+")
    private val betweenTail = rx("\\bbetween\\s+\\S+\\s*$")
    private val softLead = rx("^(?:(?:and|then|also|plus|maybe|please|probably|definitely|quickly|just|so|ok|okay|urgent|urgently|important)[:,]?\\s+)+")
    private val pronounLead = rx(
        "^(?:i|i'll|i'm|i've|i'd|ill|im|we|we'll|we're|let's|lets|need|needs|have\\s+to|has\\s+to|got\\s+to|gotta|must|should|" +
            "remind|remember|don't|dont|do\\s+not|there's|there\\s+is|my|our)\\b",
    )
    private val timeLead = rx(
        "^(?:at|@|by|before|after|from|around|on|this|next|tomorrow|today|tonight|every|each|" +
            "monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b|^\\d{1,2}(?:[:.]\\d{2})?\\s*(?:am|pm|a\\.m\\.|p\\.m\\.|a|p)\\b|^\\d{1,2}:\\d{2}",
    )
    private val eventNoun = rx("^(?:a\\s+|an\\s+|the\\s+)?(?:meeting|appointment|call|interview|class|lecture|flight|standup|stand-up|lunch|dinner|breakfast|dentist|doctor|party|session|demo|review)\\b")
    private val leadIns = rx("^(?:i\\s+)?(?:need\\s+to|have\\s+to|must|should|want\\s+to|will|gotta|got\\s+to|remember\\s+to|remind\\s+me\\s+to)?\\s*")
    private val listVerbs = setOf("buy", "get", "pick", "order", "bring", "grab", "collect", "pack", "carry", "fetch", "purchase")

    val verbs: Set<String> = setOf(
        "call", "phone", "ring", "text", "email", "e-mail", "mail", "message", "whatsapp", "reply", "respond", "write", "draft",
        "prepare", "finish", "complete", "submit", "send", "buy", "get", "pick", "drop", "collect", "order", "book", "pay",
        "renew", "cancel", "schedule", "plan", "review", "read", "study", "learn", "practice", "practise", "revise", "clean",
        "wash", "cook", "make", "bake", "fix", "repair", "check", "visit", "meet", "see", "go", "take", "bring", "return",
        "water", "feed", "walk", "run", "jog", "swim", "work", "exercise", "attend", "join", "watch", "listen", "update",
        "file", "print", "sign", "apply", "organize", "organise", "sort", "pack", "move", "start", "continue", "stop",
        "research", "look", "find", "ask", "tell", "follow", "post", "upload", "download", "install", "charge", "iron",
        "fold", "vacuum", "mop", "sweep", "shop", "do", "talk", "chat", "contact", "confirm", "transfer", "deposit",
        "withdraw", "edit", "code", "test", "deploy", "design", "draw", "paint", "play", "meditate", "pray", "sleep", "wake",
        "shower", "drive", "fly", "catch", "leave", "arrive", "submit", "grab", "fetch", "invite", "celebrate", "wish",
        "gym", "workout", "lift", "stretch", "rest", "nap", "eat", "have", "hit", "clear", "empty", "tidy", "polish",
    )
}
