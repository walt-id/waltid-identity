@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package id.walt.walletdemo.compose.logic

import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class CredentialDisplayValueDecoder(
    private val json: Json,
    private val renderJson: (JsonElement, ClaimPath) -> DisplayValue,
) {
    fun decodedString(value: String, path: ClaimPath): DisplayValue? {
        val payload = EncodedPayload.parse(value) ?: return null
        val bytes = payload.base64.decode() ?: return null
        ImageBytes.detectMime(bytes, payload.imageMimeTypeHint)?.let { mime ->
            return bytes.toImageValue(mime, encoded = payload.base64.value)
        }

        val decodedText = runCatching { bytes.decodeToString() }.getOrNull()
            ?.takeIf { it.isMostlyReadable() }
            ?: return null

        val decodedJson = runCatching { json.parseToJsonElement(decodedText) }.getOrNull()
        if (decodedJson != null) {
            return renderJson(decodedJson, path)
        }

        return DisplayValue.DecodedText(decodedText)
    }

    fun imageFromByteArray(value: JsonArray, roles: Set<ClaimRole>): DisplayValue.Image? {
        if (ClaimRole.Image !in roles) return null
        val bytes = value.toByteArrayOrNull() ?: return null
        val mime = ImageBytes.detectMime(bytes, mimeHint = null) ?: return null
        return bytes.toImageValue(mime)
    }

    private fun JsonArray.toByteArrayOrNull(): ByteArray? {
        if (isEmpty()) return null
        return map { element ->
            val number = (element as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return null
            if (number !in -128..255) return null
            number.toByte()
        }.toByteArray()
    }

    private fun ByteArray.toImageValue(mime: String, encoded: String = Base64.Default.encode(this)): DisplayValue.Image {
        return DisplayValue.Image(
            encoded = encoded,
            bytes = this,
            mimeType = mime,
            byteCount = size,
        )
    }
}

private data class EncodedPayload(
    val imageMimeTypeHint: String?,
    val base64: Base64Payload,
) {
    companion object {
        private const val schemePrefix = "data:"
        private const val base64Marker = ";base64,"

        fun parse(rawValue: String): EncodedPayload? {
            val value = rawValue.trim()
            val plainPayload = { payload: String ->
                Base64Payload.parse(payload)?.let { base64 ->
                    EncodedPayload(imageMimeTypeHint = null, base64 = base64)
                }
            }

            if (!value.startsWith(schemePrefix, ignoreCase = true)) {
                return plainPayload(value)
            }

            val markerIndex = value.indexOf(base64Marker, ignoreCase = true)
            if (markerIndex < 0) {
                return plainPayload(value)
            }

            val metadata = value.substring(schemePrefix.length, markerIndex)
            val base64 = Base64Payload.parse(value.substring(markerIndex + base64Marker.length))
                ?: return null
            return EncodedPayload(
                imageMimeTypeHint = MediaTypeHint.imageType(metadata),
                base64 = base64,
            )
        }
    }
}

private object MediaTypeHint {
    fun imageType(metadata: String): String? {
        val mediaType = metadata.substringBefore(';').trim().lowercase()
        return mediaType.takeIf { it.startsWith(ImageMime.Prefix) }
    }
}

private class Base64Payload private constructor(val value: String) {
    fun decode(): ByteArray? {
        val padded = value.padEnd(value.length + ((base64BlockSize - value.length % base64BlockSize) % base64BlockSize), '=')
        return runCatching { Base64.Default.decode(padded) }.getOrNull()
            ?: runCatching { Base64.UrlSafe.decode(padded) }.getOrNull()
    }

    companion object {
        fun parse(rawValue: String): Base64Payload? {
            val value = rawValue.trim()
            return Base64Payload(value).takeIf { looksValid(value) }
        }

        private fun looksValid(value: String): Boolean {
            if (value.length < minimumPayloadLength || value.any { it.isWhitespace() }) return false
            val allowed = value.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '-' || it == '_' || it == '=' }
            return allowed && value.length % base64BlockSize != invalidBase64Remainder
        }

        private const val minimumPayloadLength = 12
        private const val base64BlockSize = 4
        private const val invalidBase64Remainder = 1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Base64Payload) return false
        return value == other.value
    }

    override fun hashCode(): Int =
        value.hashCode()
}

private object ImageMime {
    const val Prefix = "image/"
    const val Png = "image/png"
    const val Jpeg = "image/jpeg"
    const val Gif = "image/gif"
    const val Webp = "image/webp"
}

private object ImageBytes {
    fun detectMime(bytes: ByteArray, mimeHint: String?): String? =
        when {
            mimeHint?.startsWith(ImageMime.Prefix) == true -> mimeHint
            bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> ImageMime.Png
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> ImageMime.Jpeg
            bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a") -> ImageMime.Gif
            bytes.size >= 12 && bytes.startsWithAscii("RIFF") && bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> ImageMime.Webp
            else -> null
        }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }

    private fun ByteArray.startsWithAscii(prefix: String): Boolean =
        size >= prefix.length && prefix.indices.all { this[it].toInt().toChar() == prefix[it] }
}

private fun String.isMostlyReadable(): Boolean =
    isNotBlank() &&
            '\uFFFD' !in this &&
            count { it == '\n' || it == '\r' || it == '\t' || !it.isISOControl() } >= length * readableCharacterRatio

private const val readableCharacterRatio = 0.9
