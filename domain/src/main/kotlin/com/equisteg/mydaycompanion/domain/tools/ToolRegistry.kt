package com.equisteg.mydaycompanion.domain.tools

/** The allowlist. A tool that is not registered here does not exist as far as the agent is concerned. */
class ToolRegistry(tools: List<AgentTool>) {
    private val byId: Map<String, AgentTool>

    init {
        val ids = tools.map { it.spec.id }
        require(ids.size == ids.toSet().size) { "Duplicate tool ids: $ids" }
        tools.forEach { require(toolIdPattern.matches(it.spec.id)) { "Bad tool id ${it.spec.id}" } }
        byId = tools.associateBy { it.spec.id }
    }

    fun find(id: String): AgentTool? = byId[id]

    fun specs(): List<ToolSpec> = byId.values.map { it.spec }.sortedBy { it.id }

    private companion object {
        val toolIdPattern = Regex("^[a-z][a-z0-9]*(\\.[a-z][a-zA-Z0-9]*)+$")
    }
}
