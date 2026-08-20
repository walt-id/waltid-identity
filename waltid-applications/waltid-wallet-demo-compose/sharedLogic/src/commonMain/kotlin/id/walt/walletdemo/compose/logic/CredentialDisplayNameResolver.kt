package id.walt.walletdemo.compose.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Preserves configured display labels and derives a readable fallback only when they are absent. */
object CredentialDisplayNameResolver {
    private val json = Json { ignoreUnknownKeys = true }
    private val typeKeys = setOf("doctype", "docType", "vct")

    fun resolve(
        label: String?,
        format: String,
        credentialDataJson: String? = null,
        credentialType: String? = null,
    ): String {
        label
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val resolvedType = credentialType
            ?.takeIf { it.isNotBlank() }
            ?: credentialDataJson.findCredentialType()
        resolvedType?.standardCredentialName()?.let { return it }

        return when (format.lowercase()) {
            "mso_mdoc" -> "Mobile document"
            "dc+sd-jwt", "vc+sd-jwt", "jwt_vc_json", "jwt_vc_json-ld" -> "Digital credential"
            else -> "Credential"
        }
    }

    private fun String?.findCredentialType(): String? {
        val root = this?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            ?: return null
        return root.findCredentialType()
    }

    private fun JsonElement.findCredentialType(): String? = when (this) {
        is JsonObject -> entries.firstNotNullOfOrNull { (key, value) ->
            (value as? JsonPrimitive)
                ?.contentOrNull
                ?.takeIf { key in typeKeys && it.isNotBlank() }
        } ?: values.firstNotNullOfOrNull { it.findCredentialType() }
        is JsonArray -> firstNotNullOfOrNull { it.findCredentialType() }
        else -> null
    }
}

internal fun String.standardCredentialName(): String? = when (lowercase()) {
    "org.iso.18013.5.1.mdl" -> "Mobile driving licence"
    "org.iso.23220.photoid.1" -> "Photo ID"
    "eu.europa.ec.eudi.pid.1",
    "urn:eudi:pid:1",
    "urn:eu.europa.ec.eudi:pid:1",
    -> "Personal ID"
    else -> null
}
