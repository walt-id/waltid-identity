package id.walt.walletdemo.compose.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        val logo = selected["logo"]?.jsonObject
        val backgroundImage = selected["background_image"]?.jsonObject
            ?: selected["backgroundImage"]?.jsonObject
        val name = selected["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val logoUri = logo?.get("uri")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val logoAltText = logo?.get("alt_text")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: logo?.get("altText")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val description = selected["description"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val backgroundColor = selected["background_color"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: selected["backgroundColor"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val backgroundImageUri = backgroundImage?.get("uri")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val textColor = selected["text_color"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: selected["textColor"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        if (
            name == null &&
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
    ): JsonObject? {
        val preferences = preferredLocales.mapNotNull(::normalizeLocale).distinct()
        preferences.forEach { preferred ->
            localeLookupTags(preferred).forEach { candidate ->
                displays.firstOrNull { normalizeLocale(it.locale()) == candidate }?.let { return it }
            }
        }
        return displays.firstOrNull { it.locale().isNullOrBlank() } ?: displays.firstOrNull()
    }

    private fun JsonObject.locale(): String? =
        this["locale"]?.jsonPrimitive?.contentOrNull

    private fun normalizeLocale(locale: String?): String? =
        locale
            ?.trim()
            ?.replace('_', '-')
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }

    private fun localeLookupTags(locale: String): List<String> = buildList {
        val subtags = locale.split('-').filter(String::isNotEmpty).toMutableList()
        while (subtags.isNotEmpty()) {
            add(subtags.joinToString("-"))
            subtags.removeAt(subtags.lastIndex)
            if (subtags.lastOrNull()?.length == 1) subtags.removeAt(subtags.lastIndex)
        }
    }
}
