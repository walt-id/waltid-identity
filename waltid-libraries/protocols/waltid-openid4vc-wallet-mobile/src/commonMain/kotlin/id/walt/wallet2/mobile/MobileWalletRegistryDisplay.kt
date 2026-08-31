package id.walt.wallet2.mobile

import id.walt.credentials.display.CredentialTitles
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Credential Manager picker title and subtitle for a registered credential.
 *
 * Known-document mappings win over a stored label. Unmapped types, or mapped claims that are
 * missing, fall back to a humanized type rather than the raw docType or vct.
 */
internal object MobileWalletRegistryDisplay {
    data class Resolved(
        val title: String,
        val subtitle: String,
    )

    fun resolve(
        format: MobileWalletDigitalCredentialFormat,
        type: String,
        credentialData: JsonObject,
        storedLabel: String?,
    ): Resolved {
        val mapping = mappingFor(format, type, credentialData)
        val humanizedType = humanizedType(format, type)
        val title = mapping?.title?.resolve(credentialData)
            ?: storedLabel?.trim()?.takeIf { it.isNotBlank() }
            ?: humanizedType
        val subtitle = mapping?.subtitle?.resolve(credentialData) ?: humanizedType
        return Resolved(title = title, subtitle = subtitle)
    }

    private fun mappingFor(
        format: MobileWalletDigitalCredentialFormat,
        type: String,
        credentialData: JsonObject,
    ): DisplayMapping? {
        val keys = matchKeys(format, type, credentialData)
        return Mappings.firstOrNull { mapping ->
            mapping.kind == format.toMappingKind() && mapping.keys.any { it in keys }
        }
    }

    private fun matchKeys(
        format: MobileWalletDigitalCredentialFormat,
        type: String,
        credentialData: JsonObject,
    ): Set<String> = buildSet {
        add(type)
        when (format) {
            MobileWalletDigitalCredentialFormat.MDOC ->
                credentialData.keys.filter { it != "docType" && it != "doctype" && it != "doc_type" }
                    .forEach(::add)
            MobileWalletDigitalCredentialFormat.SD_JWT_VC ->
                credentialTypes(credentialData).forEach(::add)
        }
    }

    private fun humanizedType(
        format: MobileWalletDigitalCredentialFormat,
        type: String,
    ): String = CredentialTitles.fromPayload(
        format = format.identifier,
        credentialDataJson = when (format) {
            MobileWalletDigitalCredentialFormat.MDOC -> buildJsonObject { put("docType", type) }.toString()
            MobileWalletDigitalCredentialFormat.SD_JWT_VC -> buildJsonObject { put("vct", type) }.toString()
        },
    )

    private fun credentialTypes(credentialData: JsonObject): List<String> {
        val element = credentialData["type"] ?: credentialData["vc"]?.jsonObject?.get("type") ?: return emptyList()
        return when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
            is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let { listOf(it) }.orEmpty()
            else -> emptyList()
        }
    }

    private fun MobileWalletDigitalCredentialFormat.toMappingKind(): MappingKind = when (this) {
        MobileWalletDigitalCredentialFormat.MDOC -> MappingKind.MDOC
        MobileWalletDigitalCredentialFormat.SD_JWT_VC -> MappingKind.SD_JWT
    }

    private val Mappings = listOf(
        DisplayMapping(
            kind = MappingKind.MDOC,
            keys = setOf(PaymentCardDocType),
            title = DisplaySource.Claim(listOf(PaymentCardDocType, "card_holder_name")),
            subtitle = DisplaySource.Claim(listOf(PaymentCardDocType, "card_last4"), ::maskLastFour),
        ),
        DisplayMapping(
            kind = MappingKind.MDOC,
            keys = setOf(MdlNamespace, MdlDocType),
            title = DisplaySource.Static("Mobile Driving License"),
            subtitle = DisplaySource.Claim(listOf(MdlNamespace, "document_number")),
        ),
        DisplayMapping(
            kind = MappingKind.MDOC,
            keys = setOf(PidDocType),
            title = DisplaySource.Static("Personal ID"),
            subtitle = DisplaySource.Claim(listOf(PidDocType, "document_number")),
        ),
    )

    private fun maskLastFour(value: String): String = "$LastFourMask$value"

    private const val PaymentCardDocType = "eu.europa.ec.eudi.sca.payment_card.1"
    private const val MdlNamespace = "org.iso.18013.5.1"
    private const val MdlDocType = "org.iso.18013.5.1.mDL"
    private const val PidDocType = "eu.europa.ec.eudi.pid.1"
    private const val LastFourMask = "********"
}

private enum class MappingKind { MDOC, SD_JWT, W3C }

private data class DisplayMapping(
    val kind: MappingKind,
    val keys: Set<String>,
    val title: DisplaySource,
    val subtitle: DisplaySource,
)

private sealed interface DisplaySource {
    data class Static(val text: String) : DisplaySource
    data class Claim(
        val path: List<String>,
        val transform: (String) -> String = { it },
    ) : DisplaySource

    fun resolve(credentialData: JsonObject): String? = when (this) {
        is Static -> text
        is Claim -> credentialData.claimText(path)?.let(transform)
    }
}

private fun JsonObject.claimText(path: List<String>): String? {
    var current: JsonElement = this
    for (segment in path) {
        current = (current as? JsonObject)?.get(segment) ?: return null
    }
    return (current as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
}
