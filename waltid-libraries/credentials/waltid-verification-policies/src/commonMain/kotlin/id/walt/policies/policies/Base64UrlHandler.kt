package id.walt.policies.policies

import id.walt.sdjwt.utils.Base64Utils.base64UrlDecode

private const val MULTIBASE_BASE64_URL_PREFIX = 'u'

class Base64UrlHandler {
    companion object {
        private val base64UrlRegex = Regex("^[A-Za-z0-9_-]*$")
    }

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
            decodedData = cleanString.base64UrlDecode(),
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

    private fun String.isValidBase64UrlContent(): Boolean = runCatching { base64UrlDecode() }.isSuccess
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
        if (other !is Base64UrlResult) return false

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
