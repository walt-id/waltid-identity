package id.walt.issuer.issuance

import id.walt.crypto.utils.HexUtils.matchesHex
import id.walt.mdoc.mso.IdentifierListInfo
import id.walt.mdoc.mso.Status
import id.walt.mdoc.mso.StatusListInfo
import io.ktor.server.plugins.BadRequestException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

internal fun JsonObject.toMdocIssuerStatusOrNull(): Status? {
    if (isEmpty()) return null
    val statusListEl = this["status_list"]
    val identifierListEl = this["identifier_list"]
    when {
        statusListEl != null && identifierListEl != null ->
            throw BadRequestException("mdoc status_list and identifier_list are mutually exclusive")

        statusListEl != null -> {
            val sl = statusListEl.jsonObject
            val idxEl = sl["idx"] ?: throw BadRequestException("status_list.idx is required")
            val uriEl = sl["uri"] ?: throw BadRequestException("status_list.uri is required")
            val idx = idxEl.jsonPrimitive.longOrNull?.toUInt()
                ?: idxEl.jsonPrimitive.intOrNull?.toUInt()
                ?: throw BadRequestException("status_list.idx must be a non-negative integer")
            val uri = uriEl.jsonPrimitive.content
            val certificate = sl["certificate"]?.let { cert ->
                decodeBinaryJsonPrimitive(cert.jsonPrimitive)
            }
            return Status(statusList = StatusListInfo(index = idx, uri = uri, certificate = certificate))
        }

        identifierListEl != null -> {
            val il = identifierListEl.jsonObject
            val uriEl = il["uri"] ?: throw BadRequestException("identifier_list.uri is required")
            val uri = uriEl.jsonPrimitive.content
            val idBytes = il["id"]?.jsonPrimitive?.let(::decodeBinaryJsonPrimitive)
                ?: throw BadRequestException("identifier_list.id is required (hex or base64)")
            val certificate = il["certificate"]?.let { decodeBinaryJsonPrimitive(it.jsonPrimitive) }
            return Status(identifierList = IdentifierListInfo(id = idBytes, uri = uri, certificate = certificate))
        }

        else -> throw BadRequestException("mdoc status requires status_list or identifier_list")
    }
}

private fun decodeBinaryJsonPrimitive(p: kotlinx.serialization.json.JsonPrimitive): ByteArray {
    val raw = p.content.trim().removePrefix("0x")
    return when {
        raw.matchesHex() -> decodeHexEven(raw)
        else -> decodeBase64Lenient(raw)
    }
}

private fun decodeHexEven(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex-encoded id must have even length" }
    return ByteArray(hex.length / 2) { i ->
        val hi = hexNibble(hex[i * 2])
        val lo = hexNibble(hex[i * 2 + 1])
        ((hi shl 4) or lo).toByte()
    }
}

private fun hexNibble(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> throw BadRequestException("Invalid hex character in binary field: $c")
}

private fun decodeBase64Lenient(s: String): ByteArray =
    runCatching { Base64.getUrlDecoder().decode(s) }
        .recoverCatching { Base64.getDecoder().decode(s) }
        .getOrElse { throw BadRequestException("Value must be hex or valid Base64 (standard or URL-safe)") }
