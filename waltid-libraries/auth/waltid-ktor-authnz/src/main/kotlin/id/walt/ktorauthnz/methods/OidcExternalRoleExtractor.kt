package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.methods.config.OidcAuthConfiguration
import id.walt.ktorauthnz.methods.sessiondata.OidcExternalRoles
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object OidcExternalRoleExtractor {

    fun extract(
        idTokenPayload: JsonObject,
        config: OidcAuthConfiguration,
        issuer: String,
        subject: String,
    ): OidcExternalRoles? {
        val extractionConfig = config.externalRoleExtraction
        if (!extractionConfig.enabled) {
            return null
        }

        val realmRoles = resolvePath(idTokenPayload, extractionConfig.realmRolesClaimPath)
            ?.asStringSet()
            ?: emptySet()

        val configuredClientId = extractionConfig.clientId ?: config.clientId
        val clientRolesRoot = resolvePath(idTokenPayload, extractionConfig.clientRolesClaimPath)
            ?.jsonObject

        val allClientRoles = clientRolesRoot
            ?.mapValues { (_, value) ->
                value.jsonObject["roles"]?.asStringSet().orEmpty()
            }
            .orEmpty()
            .filterValues { it.isNotEmpty() }

        val clientRoles = allClientRoles
            .filterKeys { it == configuredClientId }

        return OidcExternalRoles(
            issuer = issuer,
            subject = subject,
            realmRoles = realmRoles,
            clientRoles = clientRoles,
        )
    }

    private fun resolvePath(input: JsonObject, path: String): JsonElement? {
        if (path.isBlank()) return null

        val segments = path.split('.')
        var current: JsonElement = input
        for (segment in segments) {
            current = (current as? JsonObject)?.get(segment) ?: return null
        }
        return current
    }

    private fun JsonElement.asStringSet(): Set<String> = when (this) {
        is JsonArray -> this.mapNotNull {
            runCatching { it.jsonPrimitive.content }.getOrNull()
        }.toSet()
        else -> emptySet()
    }
}
