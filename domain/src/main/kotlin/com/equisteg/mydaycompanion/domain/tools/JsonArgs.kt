package com.equisteg.mydaycompanion.domain.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Canonical JSON (sorted keys) so a confirmation fingerprint covers exactly one set of arguments. */
fun JsonElement.canonical(): String = when (this) {
    is JsonObject -> entries.sortedBy { it.key }.joinToString(",", "{", "}") { "${JsonPrimitive(it.key)}:${it.value.canonical()}" }
    is JsonArray -> joinToString(",", "[", "]") { it.canonical() }
    else -> toString()
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.optionalString(key: String): Result<String?> = when (val v = this[key]) {
    null, JsonNull -> Result.success(null)
    is JsonPrimitive -> if (v.isString) Result.success(v.content) else Result.failure(IllegalArgumentException(key))
    else -> Result.failure(IllegalArgumentException(key))
}

internal fun JsonElement.asObject(): JsonObject? = this as? JsonObject

internal fun JsonObject.onlyKeys(vararg allowed: String): Boolean = keys.all { it in allowed }

internal fun JsonElement.primitiveContent(): String? = (this as? JsonPrimitive)?.jsonPrimitive?.content
