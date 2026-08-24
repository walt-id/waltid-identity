package id.walt.wallet2.mobile

import id.walt.credentials.display.DisplayLocales
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal object MobileWalletRegistryIcons {
    internal val DefaultCardBlueRgb: Int = 0x1B4FDB

    @Suppress("UNUSED_PARAMETER")
    suspend fun resolveIconPng(
        metadata: JsonObject?,
        credentialData: JsonObject,
        displayName: String,
        preferredLocales: List<String> = emptyList(),
        fetchHttps: suspend (String) -> ByteArray?,
    ): ByteArray {
        val credentialDisplay = metadata.selectDisplay(key = "credentialDisplay", preferredLocales)
        val issuerDisplay = metadata.selectDisplay(key = "issuerDisplay", preferredLocales)
        val remoteUris = listOfNotNull(
            credentialDisplay.backgroundImageUri(),
            credentialDisplay.logoUri(),
            issuerDisplay.logoUri(),
        )
        remoteUris.forEach { uri ->
            fetchHttps(uri)?.takeIf(::isImageBytes)?.let { return it }
        }
        credentialData.portraitBytes()?.takeIf(::isImageBytes)?.let { return it }
        val color = parseCssRgb(credentialDisplay?.backgroundColor()) ?: DefaultCardBlueRgb
        return solidColorPng(rgb = color)
    }
}

internal const val MaxRegistryIconBytes = 2_000_000

internal const val RegistryIconFetchTimeoutMs = 5_000L

internal fun isHttpsUrl(value: String): Boolean =
    value.trim().startsWith("https://", ignoreCase = true)

internal fun JsonObject?.selectDisplay(
    key: String,
    preferredLocales: List<String>,
): JsonObject? {
    val displays = when (val element = this?.get(key)) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(element)
        else -> emptyList()
    }
    return DisplayLocales.select(displays, preferredLocales) { it.locale() }
}

private fun JsonObject.locale(): String? =
    this["locale"]?.jsonPrimitive?.contentOrNull

private fun JsonObject?.backgroundImageUri(): String? =
    this?.get("background_image")?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
        ?.takeIf(::isHttpsUrl)
        ?: this?.get("backgroundImage")?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
            ?.takeIf(::isHttpsUrl)

private fun JsonObject?.logoUri(): String? =
    this?.get("logo")?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull?.takeIf(::isHttpsUrl)

private fun JsonObject?.backgroundColor(): String? =
    this?.get("background_color")?.jsonPrimitive?.contentOrNull
        ?: this?.get("backgroundColor")?.jsonPrimitive?.contentOrNull

private val PortraitClaimNames = setOf("portrait", "picture", "photo", "image")

private fun JsonObject.portraitBytes(): ByteArray? {
    entries.forEach { (key, value) ->
        if (key.equals("org.iso.18013.5.1", ignoreCase = true) && value is JsonObject) {
            value["portrait"]?.imageBytes()?.let { return it }
        }
        if (key.lowercase() in PortraitClaimNames) {
            value.imageBytes()?.let { return it }
        }
        if (value is JsonObject) {
            value.portraitBytes()?.let { return it }
        }
    }
    return null
}

@OptIn(ExperimentalEncodingApi::class)
private fun JsonElement.imageBytes(): ByteArray? = when (this) {
    is JsonArray -> {
        if (isEmpty() || size > MaxRegistryIconBytes) {
            null
        } else {
            val bytes = ByteArray(size)
            forEachIndexed { index, element ->
                val value = (element as? JsonPrimitive)?.intOrNull ?: return null
                if (value !in 0..255) return null
                bytes[index] = value.toByte()
            }
            bytes
        }
    }
    is JsonPrimitive -> {
        val raw = contentOrNull?.trim().orEmpty()
        if (raw.isEmpty()) {
            null
        } else {
            val encoded = raw.substringAfter("base64,", raw)
            runCatching { Base64.decode(encoded) }.getOrNull()
                ?: runCatching { Base64.UrlSafe.decode(encoded) }.getOrNull()
        }
    }
    is JsonObject -> this["elementValue"]?.imageBytes() ?: this["value"]?.imageBytes()
    else -> null
}

internal fun isImageBytes(bytes: ByteArray): Boolean {
    if (bytes.size !in 8..MaxRegistryIconBytes) return false
    return bytes.isPng() || bytes.isJpeg()
}

private fun ByteArray.isPng(): Boolean =
    size >= 8 &&
        this[0] == 0x89.toByte() &&
        this[1] == 0x50.toByte() &&
        this[2] == 0x4E.toByte() &&
        this[3] == 0x47.toByte()

private fun ByteArray.isJpeg(): Boolean =
    size >= 3 &&
        this[0] == 0xFF.toByte() &&
        this[1] == 0xD8.toByte() &&
        this[2] == 0xFF.toByte()

internal fun parseCssRgb(value: String?): Int? {
    val hex = value?.trim()?.removePrefix("#").orEmpty()
    val normalized = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("")
        6 -> hex
        8 -> hex.takeLast(6)
        else -> return null
    }
    return normalized.toIntOrNull(16)
}

internal fun solidColorPng(rgb: Int, size: Int = 32): ByteArray {
    val red = (rgb shr 16) and 0xFF
    val green = (rgb shr 8) and 0xFF
    val blue = rgb and 0xFF
    val raw = ByteArray(size * (1 + size * 3))
    var offset = 0
    repeat(size) {
        raw[offset++] = 0
        repeat(size) {
            raw[offset++] = red.toByte()
            raw[offset++] = green.toByte()
            raw[offset++] = blue.toByte()
        }
    }
    val ihdr = ByteArray(13)
    writeInt(ihdr, 0, size)
    writeInt(ihdr, 4, size)
    ihdr[8] = 8
    ihdr[9] = 2
    return buildPng(ihdr, zlibStore(raw))
}

private fun buildPng(ihdr: ByteArray, idat: ByteArray): ByteArray {
    val signature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    return signature + pngChunk("IHDR", ihdr) + pngChunk("IDAT", idat) + pngChunk("IEND", ByteArray(0))
}

private fun pngChunk(type: String, data: ByteArray): ByteArray {
    val typeBytes = type.encodeToByteArray()
    val chunk = ByteArray(8 + data.size + 4)
    writeInt(chunk, 0, data.size)
    typeBytes.copyInto(chunk, 4)
    data.copyInto(chunk, 8)
    writeInt(chunk, 8 + data.size, crc32(typeBytes + data))
    return chunk
}

private fun zlibStore(data: ByteArray): ByteArray {
    val block = ByteArray(2 + 5 + data.size + 4)
    block[0] = 0x78
    block[1] = 0x01
    block[2] = 0x01
    writeShort(block, 3, data.size)
    writeShort(block, 5, data.size.inv() and 0xFFFF)
    data.copyInto(block, 7)
    writeInt(block, 7 + data.size, adler32(data))
    return block
}

private fun writeInt(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value ushr 24).toByte()
    target[offset + 1] = (value ushr 16).toByte()
    target[offset + 2] = (value ushr 8).toByte()
    target[offset + 3] = value.toByte()
}

private fun writeShort(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value and 0xFF).toByte()
    target[offset + 1] = ((value ushr 8) and 0xFF).toByte()
}

private fun crc32(data: ByteArray): Int {
    var crc = 0xFFFFFFFF.toInt()
    data.forEach { byte ->
        val index = (crc xor byte.toInt()) and 0xFF
        crc = Crc32Table[index] xor (crc ushr 8)
    }
    return crc.inv()
}

private fun adler32(data: ByteArray): Int {
    var a = 1
    var b = 0
    data.forEach { byte ->
        a = (a + (byte.toInt() and 0xFF)) % 65521
        b = (b + a) % 65521
    }
    return (b shl 16) or a
}

private val Crc32Table: IntArray = IntArray(256) { index ->
    var crc = index
    repeat(8) {
        crc = if (crc and 1 != 0) {
            0xEDB88320.toInt() xor (crc ushr 1)
        } else {
            crc ushr 1
        }
    }
    crc
}

