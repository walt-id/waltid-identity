package id.walt.policies2.vc.policies.status.entry

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.mdoc.objects.mso.Status
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val IETF_STATUS_LIST =
    "credential-status evaluates an IETF Token Status List (status.status_list.idx and status.status_list.uri)"

internal fun DigitalCredential.statusAudience(): String = when (this) {
    is MdocsCredential -> "mdoc"
    is SdJwtCredential -> "SD-JWT VC"
    else -> "$format credential"
}

internal fun JsonElement?.requireIetfStatusClaim(audience: String): JsonElement? {
    if (this == null || this is JsonNull) return null
    val status = this as? JsonObject ?: throw IllegalArgumentException(
        "$audience status claim must be a JSON object with status_list, but was ${jsonValueKind(this)}."
    )
    val statusListElement = status["status_list"] ?: throw IllegalArgumentException(
        "$audience status claim has no status_list. $IETF_STATUS_LIST. Found members: ${memberList(status)}."
    )
    val statusList = statusListElement as? JsonObject ?: throw IllegalArgumentException(
        "$audience status.status_list must be a JSON object with idx and uri, but was ${jsonValueKind(statusListElement)}."
    )
    val missing = listOf("idx", "uri").filter { it !in statusList }
    if (missing.isNotEmpty()) {
        throw IllegalArgumentException(
            "$audience status.status_list is missing ${missing.joinToString(" and ")}."
        )
    }
    val idx = statusList.getValue("idx")
    if (idx !is JsonPrimitive || idx.isString || idx.content.toULongOrNull() == null) {
        throw IllegalArgumentException(
            "$audience status.status_list.idx must be an unsigned integer, but was ${jsonValueKind(idx)}."
        )
    }
    val uri = statusList.getValue("uri")
    if (uri !is JsonPrimitive || !uri.isString) {
        throw IllegalArgumentException(
            "$audience status.status_list.uri must be a non-blank URI string, but was ${jsonValueKind(uri)}."
        )
    }
    if (uri.content.isBlank()) {
        throw IllegalArgumentException(
            "$audience status.status_list.uri must be a non-blank URI string, but was blank."
        )
    }
    return this
}

internal fun Status?.requireEvaluableStatusList(audience: String): Status.StatusListInfo? {
    if (this == null) return null
    statusList?.let { return it }
    throw IllegalArgumentException(unsupportedMsoStatusMessage(audience, this))
}

private fun unsupportedMsoStatusMessage(audience: String, status: Status): String {
    val present = buildList {
        if (status.identifierList != null) add("ISO/IEC 18013-5 identifier_list")
        val etsiFields = listOfNotNull(
            status.type?.let { "type" },
            status.purpose?.let { "purpose" },
            status.index?.let { "index" },
            status.uri?.let { "uri" },
        )
        if (etsiFields.isNotEmpty()) {
            add("ETSI TS 119 472-1 flat status (${etsiFields.joinToString(", ")})")
        }
    }
    val found = if (present.isEmpty()) {
        "a status object with no status_list, identifier_list, or ETSI flat status members"
    } else {
        present.joinToString(" and ") + " and no status_list"
    }
    return "$audience has $found. $IETF_STATUS_LIST."
}

private fun memberList(status: JsonObject): String =
    status.keys.sorted().joinToString().ifEmpty { "(none)" }

private fun jsonValueKind(element: JsonElement): String = when (element) {
    is JsonNull -> "null"
    is JsonArray -> "an array"
    is JsonObject -> "an object"
    is JsonPrimitive -> when {
        element.isString -> "a string"
        element.content == "true" || element.content == "false" -> "a boolean"
        else -> "a number"
    }
}
