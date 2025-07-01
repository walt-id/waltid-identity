package id.walt.policies.policies

import java.util.*

private const val MULTIBASE_BASE64_URL_PREFIX = 'u'

class Base64UrlHandler {
    private val base64UrlRegex = Regex("^[A-Za-z0-9_-]*$")
    private val urlDecoder = Base64.getUrlDecoder()
    private val urlEncoder = Base64.getUrlEncoder()

    /**
     * Decodes a base64-url string (regular or multibase) and returns detailed result
     */
    fun decodeBase64Url(input: String): Base64UrlResult = input.run {
        require(isValidBase64Url()) { "Invalid base64-url string: $this" }
        val type = identifyType()
        val cleanString = when (type) {
            Base64UrlType.Multibase -> drop(1)
            Base64UrlType.Regular -> this
        }
        Base64UrlResult(
            type = type,
            originalString = this,
            decodedData = cleanString.decodeBase64UrlBytes(),
            cleanEncodedString = cleanString
        )
    }

    /**
     * Identifies the type of base64-url encoding
     */
    private fun String.identifyType(): Base64UrlType = when {
        startsWith(MULTIBASE_BASE64_URL_PREFIX) -> Base64UrlType.Multibase
        else -> Base64UrlType.Regular
    }

    /**
     * Validates if a string is a valid base64-url string (regular or multibase)
     */
    private fun String.isValidBase64Url(): Boolean = takeIf { it.isNotEmpty() }?.runCatching {
        when (identifyType()) {
            Base64UrlType.Multibase -> takeIf { length > 1 }?.drop(1)
                ?.let { it.matches(base64UrlRegex) && it.isValidBase64UrlContent() } ?: false

            Base64UrlType.Regular -> matches(base64UrlRegex) && isValidBase64UrlContent()
        }
    }?.getOrElse { false } ?: false

    private fun String.isValidBase64UrlContent(): Boolean = runCatching { decodeBase64UrlBytes() }.isSuccess

    private fun String.decodeBase64UrlBytes(): ByteArray = urlDecoder.decode(this)

    /**
     * Decodes a base64-url string and returns only the byte array
     */
    fun String.decodeBase64UrlToBytes(): ByteArray = decodeBase64Url(this).decodedData

    /**
     * Decodes a base64-url string and returns UTF-8 string
     */
    fun String.decodeBase64UrlToString(): String = decodeBase64Url(this).decodedData.toString(Charsets.UTF_8)

    /**
     * Encodes byte array to regular base64-url
     */
    fun ByteArray.encodeToBase64Url(): String = urlEncoder.withoutPadding().encodeToString(this)

    /**
     * Encodes byte array to multibase base64-url
     */
    fun ByteArray.encodeToMultibaseBase64Url(): String = MULTIBASE_BASE64_URL_PREFIX + encodeToBase64Url()

    /**
     * Encodes string to regular base64-url
     */
    fun String.encodeToBase64Url(): String = toByteArray(Charsets.UTF_8).encodeToBase64Url()

    /**
     * Encodes string to multibase base64-url
     */
    fun String.encodeToMultibaseBase64Url(): String = toByteArray(Charsets.UTF_8).encodeToMultibaseBase64Url()

    /**
     * Converts between regular and multibase formats
     */
    fun String.convertBase64UrlType(targetType: Base64UrlType): String =
        decodeBase64Url(this).cleanEncodedString.let { cleanEncoded ->
            when (targetType) {
                Base64UrlType.Regular -> cleanEncoded
                Base64UrlType.Multibase -> "$MULTIBASE_BASE64_URL_PREFIX$cleanEncoded"
            }
        }
}

sealed class Base64UrlType {
    object Regular : Base64UrlType()
    object Multibase : Base64UrlType()
}

data class Base64UrlResult(
    val type: Base64UrlType,
    val originalString: String,
    val decodedData: ByteArray,
    val cleanEncodedString: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Base64UrlResult

        if (type != other.type) return false
        if (originalString != other.originalString) return false
        if (!decodedData.contentEquals(other.decodedData)) return false
        if (cleanEncodedString != other.cleanEncodedString) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + originalString.hashCode()
        result = 31 * result + decodedData.contentHashCode()
        result = 31 * result + cleanEncodedString.hashCode()
        return result
    }
}