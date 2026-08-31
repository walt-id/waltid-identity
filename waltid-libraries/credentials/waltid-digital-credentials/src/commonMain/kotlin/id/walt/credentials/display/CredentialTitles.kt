package id.walt.credentials.display

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Format-aware display title for a stored credential when OpenID4VCI card art is not used.
 */
object CredentialTitles {
    fun fromPayload(
        format: String,
        credentialDataJson: String?,
        displayName: String? = null,
        fallback: String? = null,
    ): String {
        displayName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val payload = credentialDataJson.toJsonObject()
        val extracted = when {
            format.isMdoc() -> mdocTitle(payload)
            format.isSdJwt() -> sdJwtTitle(payload)
            else -> w3cTitle(payload)
        }
        return extracted ?: fallback?.trim()?.takeIf { it.isNotBlank() } ?: format
    }

    private fun w3cTitle(payload: JsonObject?): String? {
        val types = payload.stringValues("type")
            ?: payload.obj("vc")?.stringValues("type")
            ?: return null
        return types
            .firstOrNull { !it.isGenericVerifiableCredentialType() }
            ?.let(::humanizeToken)
    }

    private fun sdJwtTitle(payload: JsonObject?): String? {
        val vct = payload.string("vct") ?: return null
        return humanizeToken(vct)
    }

    private fun mdocTitle(payload: JsonObject?): String? {
        val docType = payload.string("docType")
            ?: payload.string("doctype")
            ?: payload.string("doc_type")
            ?: return null
        return MdocFriendlyNames[docType]
            ?: MdocFriendlyNames[docType.substringBeforeLast('.')]
            ?: humanizeToken(docType)
    }

    private fun humanizeToken(raw: String): String? {
        val token = trailingToken(raw) ?: return null
        if (token.isGenericVerifiableCredentialType()) return null
        val words = token
            .replace(camelCaseBoundary, "$1 $2")
            .split(wordSeparators)
            .filter { it.isNotBlank() }
            .map { word -> word.lowercase().replaceFirstChar { it.titlecase() } }
        return words.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun trailingToken(raw: String): String? {
        val value = raw.trim().takeIf { it.isNotBlank() } ?: return null
        val fromUrl = value.substringAfterLast('/').substringBefore('?').takeIf {
            value.contains("://") && it.isNotBlank()
        }
        val source = fromUrl ?: value
        val parts = source.split('/', '#', ':', '.').filter { it.isNotBlank() }
        val last = parts.lastOrNull() ?: return source
        return if (last.all(Char::isDigit) && parts.size >= 2) {
            "${parts[parts.lastIndex - 1]}_$last"
        } else {
            last
        }
    }

    private fun String.isMdoc(): Boolean {
        val normalized = lowercase()
        return normalized == "mso_mdoc" || normalized == "mdoc" || normalized.contains("mdoc")
    }

    private fun String.isSdJwt(): Boolean {
        val normalized = lowercase()
        return normalized.contains("sd-jwt") || normalized.contains("sd_jwt") || normalized == "dc+sd-jwt" || normalized == "vc+sd-jwt"
    }

    private fun String.isGenericVerifiableCredentialType(): Boolean =
        trailingToken(this)?.equals("VerifiableCredential", ignoreCase = true) == true

    private fun String?.toJsonObject(): JsonObject? =
        this?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        }

    private fun JsonObject?.string(key: String): String? =
        this?.get(key)?.let { element ->
            (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        }

    private fun JsonObject?.obj(key: String): JsonObject? =
        this?.get(key) as? JsonObject

    private fun JsonObject?.stringValues(key: String): List<String>? {
        val element = this?.get(key) ?: return null
        return when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { value -> value.isNotBlank() } }
                .takeIf { it.isNotEmpty() }
            is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let { listOf(it) }
            else -> null
        }
    }

    private val wordSeparators = Regex("[_\\-. ]+")
    private val camelCaseBoundary = Regex("([a-z])([A-Z])")

    internal val MdocFriendlyNames = mapOf(
        "org.iso.18013.5.1.mDL" to "Mobile Driving Licence",
        "org.iso.18013.5.1" to "Mobile Driving Licence",
        "eu.europa.ec.eudi.pid.1" to "PID",
        "eu.europa.ec.eudi.pid" to "PID",
        "org.iso.23220.photoid.1" to "Photo ID",
        "org.iso.23220.1" to "Photo ID",
        "eu.europa.ec.eudi.mdl.1" to "Mobile Driving Licence",
    )
}
