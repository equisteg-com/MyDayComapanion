package com.equisteg.mydaycompanion.domain.config

/**
 * Feature flags. Flags marked [locked] cannot be turned on in a V1 build at all: they guard
 * capabilities the build documents defer (cloud, sync, accounts, analytics, external integrations).
 */
enum class Flag(val key: String, val defaultEnabled: Boolean, val locked: Boolean = false) {
    REMINDERS("reminders", defaultEnabled = true),
    VOICE("voice", defaultEnabled = true),
    CALENDAR_READ("calendar_read", defaultEnabled = true),
    ROUTINES("routines", defaultEnabled = true),
    LOCAL_LLM("local_llm", defaultEnabled = false),

    CLOUD_AI("cloud_ai", defaultEnabled = false, locked = true),
    CLOUD_SYNC("cloud_sync", defaultEnabled = false, locked = true),
    ACCOUNTS("accounts", defaultEnabled = false, locked = true),
    ANALYTICS("analytics", defaultEnabled = false, locked = true),
    EXTERNAL_INTEGRATIONS("external_integrations", defaultEnabled = false, locked = true),
}

class FeatureFlags(overrides: Map<Flag, Boolean> = emptyMap()) {
    private val values: Map<String, Boolean> = Flag.entries.associate { flag ->
        val requested = overrides[flag] ?: flag.defaultEnabled
        require(!(flag.locked && requested)) { "${flag.key} is locked off in V1" }
        flag.key to requested
    }

    fun isEnabled(key: String): Boolean = values[key] ?: false

    fun isEnabled(flag: Flag): Boolean = isEnabled(flag.key)
}
