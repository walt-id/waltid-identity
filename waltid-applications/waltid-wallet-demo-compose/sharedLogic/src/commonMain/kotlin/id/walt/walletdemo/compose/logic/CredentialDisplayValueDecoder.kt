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
    private val renderJson: (JsonElement, ClaimPath, String?) -> DisplayValue,
) {
    fun decodedString(
        value: String,
        path: ClaimPath,
        format: String?,
        imagePolicy: ImageDecodingPolicy,
    ): DisplayValue? {
        val payload = when (val result = EncodedPayload.parse(
            rawValue = value,
            maxImageBytes = maxFallbackImageBytes.takeIf { imagePolicy.requiresDecodableContent },
        )) {
            is EncodedPayloadParseResult.Parsed -> result.payload
            EncodedPayloadParseResult.RejectedImageDataUrl ->
                return unavailableImageValue.takeIf { imagePolicy.requiresDecodableContent }
            EncodedPayloadParseResult.Invalid -> return null
        }
        val isFallbackImage = imagePolicy.requiresDecodableContent && payload.kind == EncodedPayloadKind.ImageDataUrl
        val bytes = payload.base64.decode() ?: return unavailableImageValue.takeIf { isFallbackImage }
        if (imagePolicy.accepts(payload.kind)) {
            ImageBytes.detectMime(bytes)?.let { mime ->
                if (!imagePolicy.requiresDecodableContent || platformCanDecodeImage(bytes, maxFallbackImagePixels)) {
                    return bytes.toImageValue(mime, encoded = payload.base64.value)
                }
            }
        }

        val decodedText = runCatching { bytes.decodeToString() }.getOrNull()
            ?.takeIf { it.isMostlyReadable() }
            ?: return unavailableImageValue.takeIf { isFallbackImage }

        val decodedJson = runCatching { json.parseToJsonElement(decodedText) }.getOrNull()
        if (decodedJson != null) {
            return renderJson(decodedJson, path, format)
        }

        return DisplayValue.DecodedText(decodedText)
    }

    fun imageFromByteArray(value: JsonArray, roles: Set<ClaimRole>): DisplayValue.Image? {
        if (ClaimRole.Image !in roles) return null
        val bytes = value.toByteArrayOrNull() ?: return null
        val mime = ImageBytes.detectMime(bytes) ?: return null
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

internal enum class ImageDecodingPolicy {
    SchemaImage,
    DataUrlFallback,
    Disabled,
}

private fun ImageDecodingPolicy.accepts(payloadKind: EncodedPayloadKind): Boolean =
    when (this) {
        ImageDecodingPolicy.SchemaImage -> true
        ImageDecodingPolicy.DataUrlFallback -> payloadKind == EncodedPayloadKind.ImageDataUrl
        ImageDecodingPolicy.Disabled -> false
    }

private val ImageDecodingPolicy.requiresDecodableContent: Boolean
    get() = this == ImageDecodingPolicy.DataUrlFallback

private data class EncodedPayload(
    val kind: EncodedPayloadKind,
    val base64: Base64Payload,
) {
    companion object {
        private const val schemePrefix = "data:"
        private const val base64Marker = ";base64,"

        fun parse(rawValue: String, maxImageBytes: Int? = null): EncodedPayloadParseResult {
            val value = rawValue.trim()
            if (!value.startsWith(schemePrefix, ignoreCase = true)) {
                val base64 = Base64Payload.parse(value) ?: return EncodedPayloadParseResult.Invalid
                return EncodedPayloadParseResult.Parsed(
                    EncodedPayload(
                        kind = EncodedPayloadKind.PlainBase64,
                        base64 = base64,
                    )
                )
            }

            val markerIndex = value.indexOf(base64Marker, ignoreCase = true)
            if (markerIndex < 0) {
                val metadataEnd = value.indexOf(',')
                return if (
                    metadataEnd >= 0 &&
                    MediaTypeHint.isImage(value.substring(schemePrefix.length, metadataEnd))
                ) {
                    EncodedPayloadParseResult.RejectedImageDataUrl
                } else {
                    EncodedPayloadParseResult.Invalid
                }
            }

            val metadata = value.substring(schemePrefix.length, markerIndex)
            val kind = if (MediaTypeHint.isImage(metadata)) {
                EncodedPayloadKind.ImageDataUrl
            } else {
                EncodedPayloadKind.OtherDataUrl
            }
            val payloadStart = markerIndex + base64Marker.length
            if (
                kind == EncodedPayloadKind.ImageDataUrl &&
                maxImageBytes != null &&
                !Base64Payload.fitsDecodedByteLimit(
                    encodedLength = value.length - payloadStart,
                    padding = value.trailingBase64Padding(),
                    limit = maxImageBytes,
                )
            ) {
                return EncodedPayloadParseResult.RejectedImageDataUrl
            }
            val base64 = Base64Payload.parse(value.substring(payloadStart))
                ?: return if (kind == EncodedPayloadKind.ImageDataUrl) {
                    EncodedPayloadParseResult.RejectedImageDataUrl
                } else {
                    EncodedPayloadParseResult.Invalid
                }
            return EncodedPayloadParseResult.Parsed(EncodedPayload(kind = kind, base64 = base64))
        }
    }
}

private sealed interface EncodedPayloadParseResult {
    data class Parsed(val payload: EncodedPayload) : EncodedPayloadParseResult
    data object RejectedImageDataUrl : EncodedPayloadParseResult
    data object Invalid : EncodedPayloadParseResult
}

private enum class EncodedPayloadKind {
    PlainBase64,
    ImageDataUrl,
    OtherDataUrl,
}

private object MediaTypeHint {
    fun isImage(metadata: String): Boolean =
        mediaType(metadata).startsWith("image/", ignoreCase = true)

    private fun mediaType(metadata: String): String =
        metadata.substringBefore(';').trim()
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

        fun fitsDecodedByteLimit(encodedLength: Int, padding: Int, limit: Int): Boolean {
            val length = encodedLength.toLong()
            val decodedSizeUpperBound = ((length + base64BlockSize - 1) / base64BlockSize) * 3 - padding
            return decodedSizeUpperBound <= limit
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

private fun String.trailingBase64Padding(): Int =
    when {
        endsWith("==") -> 2
        endsWith('=') -> 1
        else -> 0
    }

private object ImageMime {
    const val Png = "image/png"
    const val Jpeg = "image/jpeg"
    const val Gif = "image/gif"
    const val Webp = "image/webp"

}

private object ImageBytes {
    fun detectMime(bytes: ByteArray): String? =
        when {
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
private const val maxFallbackImageBytes = 2_000_000
private const val maxFallbackImagePixels = 2_048L * 2_048L
private val unavailableImageValue = DisplayValue.Text(CredentialDisplayText.ImageUnavailable)
