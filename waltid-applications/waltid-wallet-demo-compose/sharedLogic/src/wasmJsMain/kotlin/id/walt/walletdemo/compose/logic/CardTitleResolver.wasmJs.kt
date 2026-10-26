package id.walt.walletdemo.compose.logic

import kotlinx.serialization.json.*

// The browser uses Wallet API2 rather than the mobile credentials SDK. Keep its
// small display adapter covered by the same title contract on both targets.
internal actual fun resolveCardTitle(
    format: String,
    credentialDataJson: String?,
    displayName: String?,
    fallback: String,
): String {
    displayName?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val payload = credentialDataJson?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    val normalizedFormat = format.lowercase()
    val type = when {
        "mdoc" in normalizedFormat -> payload.text("docType") ?: payload.text("doctype") ?: payload.text("doc_type")
        "sd-jwt" in normalizedFormat || "sd_jwt" in normalizedFormat -> payload.text("vct")
        else -> {
            val types = payload?.get("type") ?: (payload?.get("vc") as? JsonObject)?.get("type")
            val values = (types as? JsonArray) ?: listOfNotNull(types)
            values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .firstOrNull { !CredentialDisplayVocabulary.isGenericCredentialType(it) }
        }
    }
    val friendly = if ("mdoc" in normalizedFormat) mdocTitles[type] ?: mdocTitles[type?.substringBeforeLast('.')] else null
    return friendly ?: type?.let(CredentialDisplayVocabulary::readableCredentialType)
        ?.split(' ')?.joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
        ?: fallback.trim().takeIf(String::isNotEmpty) ?: format
}

private fun JsonObject?.text(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

private val mdocTitles = mapOf(
    "org.iso.18013.5.1.mDL" to "Mobile Driving Licence",
    "org.iso.18013.5.1" to "Mobile Driving Licence",
    "eu.europa.ec.eudi.mdl.1" to "Mobile Driving Licence",
    "eu.europa.ec.eudi.pid.1" to "PID",
    "eu.europa.ec.eudi.pid" to "PID",
    "org.iso.23220.photoid.1" to "Photo ID",
    "org.iso.23220.1" to "Photo ID",
)
