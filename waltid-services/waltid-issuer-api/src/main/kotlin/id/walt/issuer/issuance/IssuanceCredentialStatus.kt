package id.walt.issuer.issuance

import id.walt.crypto.utils.HexUtils.matchesHex
import id.walt.mdoc.mso.IdentifierListInfo
import id.walt.mdoc.mso.Status
import id.walt.mdoc.mso.StatusListInfo
import io.ktor.server.plugins.BadRequestException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URISyntaxException
import java.util.Base64

internal fun JsonObject.toMdocIssuerStatusOrNull(): Status? {
    if (isEmpty()) return null
    val statusListEl = this["status_list"]
    val identifierListEl = this["identifier_list"]
    if (statusListEl.isPresentJson() && identifierListEl.isPresentJson()) {
        throw BadRequestException(
            "Only one of status_list or identifier_list may be set. ISO/IEC 18013-5 clause 9.1.2.6 " +
                "(MSO revocation) requires the identifier list mechanism to use identifier_list and " +
                "the status list mechanism to use status_list — not both.",
        )
    }
    return when {
        statusListEl.isPresentJson() ->
            MdocIssuerStatusJsonParser.parseStatusList(statusListEl.requireJsonObject("status_list"))

        identifierListEl.isPresentJson() ->
            MdocIssuerStatusJsonParser.parseIdentifierList(identifierListEl.requireJsonObject("identifier_list"))

        else -> throw BadRequestException("mdoc status requires status_list or identifier_list")
    }
}

private fun JsonElement?.isPresentJson(): Boolean = this != null && this !is JsonNull

private fun JsonElement?.requireJsonObject(field: String): JsonObject =
    this as? JsonObject ?: throw BadRequestException("$field must be a JSON object")

private object MdocIssuerStatusJsonParser {

    fun parseStatusList(sl: JsonObject): Status {
        val idxEl = sl["idx"] ?: throw BadRequestException("status_list.idx is required")
        val uriEl = sl["uri"] ?: throw BadRequestException("status_list.uri is required")
        val idx = parseNonNegativeUInt(idxEl, "status_list.idx")
        val uri = parseAbsoluteUri(stringField(uriEl, "status_list.uri"), "status_list.uri")
        val certificate = sl["certificate"]?.let {
            decodeBinaryJsonElement(it, "status_list.certificate")
        }
        return Status(statusList = StatusListInfo(index = idx, uri = uri, certificate = certificate))
    }

    fun parseIdentifierList(il: JsonObject): Status {
        val uriEl = il["uri"] ?: throw BadRequestException("identifier_list.uri is required")
        val idEl = il["id"] ?: throw BadRequestException("identifier_list.id is required")
        val uri = parseAbsoluteUri(stringField(uriEl, "identifier_list.uri"), "identifier_list.uri")
        val idBytes = decodeBinaryJsonElement(idEl, "identifier_list.id")
        val certificate = il["certificate"]?.let {
            decodeBinaryJsonElement(it, "identifier_list.certificate")
        }
        return Status(identifierList = IdentifierListInfo(id = idBytes, uri = uri, certificate = certificate))
    }

    private fun stringField(el: JsonElement, fieldPath: String): String {
        val p = el as? JsonPrimitive ?: throw BadRequestException("$fieldPath must be a JSON string")
        if (p.isString) return p.content
        throw BadRequestException("$fieldPath must be a JSON string")
    }

    private fun parseNonNegativeUInt(el: JsonElement, fieldPath: String): UInt {
        val p = el as? JsonPrimitive ?: throw BadRequestException("$fieldPath must be a JSON primitive")
        val longVal = when {
            p.isString -> p.content.trim().toLongOrNull()
                ?: throw BadRequestException("$fieldPath must be a non-negative integer")
            else -> p.longOrNull ?: p.intOrNull?.toLong()
                ?: throw BadRequestException("$fieldPath must be a non-negative integer")
        }
        if (longVal < 0L || longVal > UInt.MAX_VALUE.toLong()) {
            throw BadRequestException("$fieldPath must be in range 0..${UInt.MAX_VALUE}")
        }
        return longVal.toUInt()
    }

    private fun parseAbsoluteUri(raw: String, fieldPath: String): String {
        val s = raw.trim()
        if (s.isEmpty()) {
            throw BadRequestException("$fieldPath must be a non-blank absolute URI")
        }
        val uri = try {
            URI(s)
        } catch (e: URISyntaxException) {
            throw BadRequestException("$fieldPath is not a valid URI: ${e.reason}")
        }
        if (!uri.isAbsolute || uri.scheme.isNullOrBlank()) {
            throw BadRequestException("$fieldPath must be an absolute URI with a non-blank scheme")
        }
        return s
    }

    private fun decodeBinaryJsonElement(el: JsonElement, fieldPath: String): ByteArray {
        val p = el as? JsonPrimitive ?: throw BadRequestException("$fieldPath must be a JSON primitive")
        return decodeBinaryString(p.content, fieldPath)
    }

    /**
     * Decodes hex (optional `0x` / `0X` prefix) or Base64 (URL-safe or standard, with padding normalized).
     * If a `0x`/`0X` prefix is present, the remainder is interpreted strictly as hex (no Base64 fallback).
     */
    private fun decodeBinaryString(rawInput: String, fieldPath: String): ByteArray {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            throw BadRequestException("$fieldPath must not be blank")
        }
        val hadHexPrefix = trimmed.startsWith("0x", ignoreCase = true)
        val body = if (hadHexPrefix) trimmed.substring(2) else trimmed
        if (body.isEmpty()) {
            throw BadRequestException("$fieldPath must not be blank after removing optional 0x prefix")
        }
        return if (hadHexPrefix) {
            if (!body.matchesHex()) {
                throw BadRequestException(
                    "$fieldPath: value with 0x/0X prefix must be even-length hex (0-9, a-f or A-F, consistent letter case per digit group)",
                )
            }
            decodeHexEven(body, fieldPath)
        } else if (body.matchesHex()) {
            decodeHexEven(body, fieldPath)
        } else {
            decodeBase64UrlOrStandard(body, fieldPath)
        }
    }

    private fun decodeHexEven(hex: String, fieldPath: String): ByteArray {
        if (hex.length % 2 != 0) {
            throw BadRequestException("$fieldPath: hex-encoded value must have even length")
        }
        return ByteArray(hex.length / 2) { i ->
            val hi = hexNibble(hex[i * 2], fieldPath)
            val lo = hexNibble(hex[i * 2 + 1], fieldPath)
            ((hi shl 4) or lo).toByte()
        }
    }

    private fun hexNibble(c: Char, fieldPath: String): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw BadRequestException("$fieldPath: invalid hex character '$c'")
    }

    private fun decodeBase64UrlOrStandard(s: String, fieldPath: String): ByteArray {
        val paddedUrl = padBase64(s)
        return runCatching { Base64.getUrlDecoder().decode(paddedUrl) }
            .recoverCatching { Base64.getDecoder().decode(padBase64(s)) }
            .getOrElse {
                throw BadRequestException(
                    "$fieldPath must be even-length hex, or valid Base64 (URL-safe or standard); ${it.message}",
                )
            }
    }

    private fun padBase64(s: String): String {
        val m = s.length % 4
        return if (m == 0) s else s + "=".repeat(4 - m)
    }
}
