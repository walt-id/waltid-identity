package id.walt.walletdemo.compose.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Parses sidecar [CredentialSummary.metadataJson] written by the wallet on receive.
 *
 * The wallet stores OpenID4VCI issuer display under `issuerDisplay` and credential configuration
 * display under `credentialDisplay` as arrays of locale-tagged objects.
 */
object StoredCredentialMetadataParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun issuerDisplay(
        metadataJson: String?,
        preferredLocales: List<String> = emptyList(),
    ): WalletDemoMetadataDisplay? =
        parseDisplay(metadataJson, key = "issuerDisplay", preferredLocales = preferredLocales)

    fun credentialDisplay(
        metadataJson: String?,
        preferredLocales: List<String> = emptyList(),
    ): WalletDemoMetadataDisplay? =
        parseDisplay(metadataJson, key = "credentialDisplay", preferredLocales = preferredLocales)

    /** Claim paths are kept as components; equal leaf names in different namespaces stay distinct. */
    fun claims(metadataJson: String?, preferredLocales: List<String> = emptyList()): List<WalletDemoCredentialClaimMetadata> {
        val root = runCatching { json.parseToJsonElement(metadataJson ?: return emptyList()) as? JsonObject }.getOrNull()
            ?: return emptyList()
        val entries = root["credentialClaims"] as? JsonArray ?: return emptyList()
        val claims = entries.map { entry ->
            val claim = entry as? JsonObject ?: return emptyList()
            val path = (claim["path"] as? JsonArray)?.map { part -> (part as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } ?: return emptyList() }
                ?.takeIf { it.isNotEmpty() } ?: return emptyList()
            val displays = (claim["display"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            WalletDemoCredentialClaimMetadata(path = path,
                mandatory = (claim["mandatory"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull,
                displayName = selectPreferredDisplay(displays, preferredLocales)?.get("name").stringValue())
        }
        // Stored malformed or contradictory display data cannot override a credential's readable fallback.
        return claims.takeIf { it.map { claim -> claim.path }.distinct().size == it.size }.orEmpty()
    }

    private fun parseDisplay(
        metadataJson: String?,
        key: String,
        preferredLocales: List<String>,
    ): WalletDemoMetadataDisplay? {
        val raw = metadataJson?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val displays = root[key]?.let { element ->
            when (element) {
                is JsonArray -> element.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(element)
                else -> emptyList()
            }
        }.orEmpty()
        if (displays.isEmpty()) return null

        val selected = selectPreferredDisplay(displays, preferredLocales) ?: return null
        val logo = selected["logo"] as? JsonObject
        val backgroundImage = (selected["background_image"] as? JsonObject)
            ?: (selected["backgroundImage"] as? JsonObject)
        val name = selected["name"].stringValue()
        val logoUri = logo?.get("uri").stringValue()
        val logoAltText = logo?.get("alt_text").stringValue()
            ?: logo?.get("altText").stringValue()
        val description = selected["description"].stringValue()
        val backgroundColor = selected["background_color"].stringValue()
            ?: selected["backgroundColor"].stringValue()
        val backgroundImageUri = backgroundImage?.get("uri").stringValue()
        val textColor = selected["text_color"].stringValue()
            ?: selected["textColor"].stringValue()

        if (
            name == null &&
            description == null &&
            logoUri == null &&
            backgroundColor == null &&
            backgroundImageUri == null &&
            textColor == null
        ) {
            return null
        }
        return WalletDemoMetadataDisplay(
            name = name,
            logoUri = logoUri,
            logoAltText = logoAltText,
            description = description,
            backgroundColor = backgroundColor,
            backgroundImageUri = backgroundImageUri,
            textColor = textColor,
        )
    }

    private fun selectPreferredDisplay(
        displays: List<JsonObject>,
        preferredLocales: List<String>,
    ): JsonObject? = DisplayLocaleSelector.select(displays, preferredLocales) { it.locale() }

    private fun kotlinx.serialization.json.JsonElement?.stringValue(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.locale(): String? =
        this["locale"].stringValue()
}
