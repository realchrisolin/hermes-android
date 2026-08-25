package com.hermes.client.domain

import com.hermes.client.data.network.LaneDto
import com.hermes.client.data.network.MessageDto
import com.hermes.client.data.network.ProjectNodeDto
import com.hermes.client.data.network.ProjectTreeDto
import com.hermes.client.data.network.RepoDto
import com.hermes.client.data.network.SessionDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val modelConfigJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val bareBillingProviders = setOf("auto", "custom")

private fun JsonPrimitive.nonBlank(): String? = content.takeIf { it.isNotBlank() }

private data class ParsedModelConfig(
    val model: String?,
    val provider: String?,
    val gatewayRuntimeProvider: String?,
)

private fun parseModelConfig(raw: String?): ParsedModelConfig? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val obj = modelConfigJson.parseToJsonElement(raw).jsonObject
        val runtime = obj["gateway_runtime"] as? JsonObject
        ParsedModelConfig(
            model = (obj["model"] as? JsonPrimitive)?.nonBlank(),
            provider = (obj["provider"] as? JsonPrimitive)?.nonBlank(),
            gatewayRuntimeProvider = (runtime?.get("provider") as? JsonPrimitive)?.nonBlank(),
        )
    }.getOrNull()
}

fun SessionDto.toDomain(): Session {
    val cfg = parseModelConfig(modelConfig)
    val resolvedProvider = sequenceOf(
        cfg?.gatewayRuntimeProvider,
        cfg?.provider,
        provider,
        billingProvider?.takeUnless { it.lowercase() in bareBillingProviders },
    ).firstOrNull { !it.isNullOrBlank() }
    val resolvedModel = sequenceOf(cfg?.model, model).firstOrNull { !it.isNullOrBlank() }
    return Session(
    id = sessionId,
    title = title?.ifBlank { "Untitled" } ?: "Untitled",
    model = resolvedModel,
    provider = resolvedProvider,
    messageCount = messageCount,
    // Normalize the default profile to a stable "default" label: the gateway may report it as
    // null or blank with is_default_profile=true, but grouping/pin-tokens/switching need one key.
    profile = profile?.ifBlank { null } ?: if (isDefaultProfile) "default" else null,
    workspace = cwd?.trimEnd('/')?.substringAfterLast('/')?.ifBlank { null } ?: "No workspace",
    archived = archived,
    source = source,
    lastActive = com.hermes.client.ui.util.secondsToEpochMs(lastActive),
    cwd = cwd?.ifBlank { null },
    gitBranch = gitBranch?.ifBlank { null },
    gitRepoRoot = gitRepoRoot?.ifBlank { null },
    )
}

fun MessageDto.toDomain() = ChatMessage(
    id = id?.toString() ?: "m-${hashCode()}",
    role = when (role.lowercase()) {
        "user" -> Role.USER
        "assistant" -> Role.ASSISTANT
        else -> Role.SYSTEM
    },
    text = content.orEmpty(),
)

fun ProjectTreeDto.toDomain() = ProjectTree(
    projects = projects.map { it.toDomain() },
    activeId = activeId,
)

fun ProjectNodeDto.toDomain() = Project(
    id = id,
    label = label,
    path = path,
    color = color?.ifBlank { null },
    isAuto = isAuto,
    sessionCount = sessionCount,
    lastActive = com.hermes.client.ui.util.secondsToEpochMs(lastActive),
    repos = repos.map { it.toDomain() },
    previewSessions = previewSessions.map { it.toDomain() },
)

fun RepoDto.toDomain() = ProjectRepo(
    id = id,
    label = label,
    path = path,
    sessionCount = sessionCount,
    lanes = groups.map { it.toDomain() },
)

fun LaneDto.toDomain() = ProjectLane(
    id = id,
    label = label,
    path = path,
    isMain = isMain,
    sessions = sessions.map { it.toDomain() },
)
