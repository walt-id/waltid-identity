package id.walt.wallet2.handlers

import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.metadata.issuer.claimDescriptionsToProcess
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val log = KotlinLogging.logger {}

/**
 * Builds sidecar wallet metadata for a stored credential.
 *
 * Writes OpenID4VCI issuer display under `issuerDisplay` and credential configuration display
 * under `credentialDisplay`. Both are locale arrays so consumers can pick a preferred entry. `credentialClaims` retains
 * the issuer's ordered claim definitions and all localized labels; it contains no issued values. Repeated or
 * contradictory claim definitions are left out (OpenID4VCI 1.0 Appendix B.3), so no claim gets a label by input order.
 */
internal fun storedCredentialDisplayMetadata(
    issuerMetadata: CredentialIssuerMetadata,
    credentialConfigurationId: String? = null,
    requestMetadata: JsonObject? = null,
): JsonObject? {
    val issuerDisplayArray = issuerMetadata.display?.takeIf { it.isNotEmpty() }?.let { displays ->
        JsonArray(displays.map { it.toStoredDisplayObject() })
    }
    val configuration = credentialConfigurationId?.let { issuerMetadata.credentialConfigurationsSupported[it] }
    val credentialMetadata = configuration?.credentialMetadata
    val credentialDisplayArray = credentialMetadata?.display
        ?.takeIf { it.isNotEmpty() }
        ?.let { displays -> JsonArray(displays.map { it.toStoredDisplayObject() }) }

    val credentialClaims = configuration?.claimDescriptionsToProcess { conflicts ->
        log.warn {
            "Ignoring claim descriptions of credential configuration '$credentialConfigurationId' from " +
                "${issuerMetadata.credentialIssuer}: ${conflicts.joinToString("; ") { it.message }} (OpenID4VCI Appendix B.3)"
        }
    }?.takeIf { it.isNotEmpty() }?.let { Json.encodeToJsonElement(it) }

    if (issuerDisplayArray == null && credentialDisplayArray == null && credentialClaims == null) return requestMetadata

    val merged = (requestMetadata?.toMutableMap() ?: mutableMapOf())
    issuerDisplayArray?.let { merged["issuerDisplay"] = it }
    credentialDisplayArray?.let { merged["credentialDisplay"] = it }
    credentialClaims?.let { merged["credentialClaims"] = it }
    return JsonObject(merged)
}

private fun IssuerDisplay.toStoredDisplayObject(): JsonObject = buildJsonObject {
    name?.let { put("name", it) }
    locale?.let { put("locale", it) }
    logo?.let { logo ->
        put(
            "logo",
            buildJsonObject {
                put("uri", logo.uri)
                logo.altText?.let { put("alt_text", it) }
            },
        )
    }
}

private fun CredentialDisplay.toStoredDisplayObject(): JsonObject = buildJsonObject {
    put("name", name)
    locale?.let { put("locale", it) }
    logo?.let { logo ->
        put(
            "logo",
            buildJsonObject {
                put("uri", logo.uri)
                logo.altText?.let { put("alt_text", it) }
            },
        )
    }
    description?.let { put("description", it) }
    backgroundColor?.let { put("background_color", it) }
    backgroundImage?.let { image ->
        put(
            "background_image",
            buildJsonObject {
                put("uri", image.uri)
            },
        )
    }
    textColor?.let { put("text_color", it) }
}
