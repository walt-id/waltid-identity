package id.walt.policies2.vc.policies.status

import id.walt.crypto.utils.Base64Utils.base64UrlDecode

private const val MULTIBASE_BASE64_URL_PREFIX = 'u'

class Base64UrlHandler {
    companion object {
        private val base64UrlRegex = Regex("^[A-Za-z0-9_-]*$")
    }

    /**
     * Decodes a base64-url string (regular or multibase) and returns detailed result
     */
    fun decodeBase64Url(input: String): id.walt.policies2.vc.policies.status.Base64UrlResult = input.run {
        require(isValidBase64Url()) { "Invalid base64-url string: $this" }
        val type = identifyType()
        val cleanString = when (type) {
            _root_ide_package_.id.walt.policies2.vc.policies.status.Base64UrlType.Multibase -> drop(1)
            _root_ide_package_.id.walt.policies2.vc.policies.status.Base64UrlType.Regular -> this
        }
        _root_ide_package_.id.walt.policies2.vc.policies.status.Base64UrlResult(
            type = type,
            originalString = this,
            decodedData = cleanString.base64UrlDecode(),
            cleanEncodedString = cleanString
        )
    }

    /**
     * Identifies the type of base64-url encoding
     */
    private fun String.identifyType(): id.walt.policies2.vc.policies.status.Base64UrlType = when {
        startsWith(_root_ide_package_.id.walt.policies2.vc.policies.status.MULTIBASE_BASE64_URL_PREFIX) -> _root_ide_package_.id.walt.policies2.vc.policies.status.Base64UrlType.Multibase
        else -> _root_ide_package_.id.walt.policies2.vc.policies.status.Base64UrlType.Regular
    }

    /**
     * Validates if a string is a valid base64-url string (regular or multibase)
     */
    private fun String.isValidBase64Url(): Boolean = takeIf { it.isNotEmpty() }?.runCatching {
        when (identifyType()) {
            _root_ide_package_.id.walt.policies2.vc.policies.status.Base64UrlType.Multibase -> takeIf { length > 1 }?.drop(1)
                ?.let { it.matches(_root_ide_package_.id.walt.policies2.vc.policies.status.Base64UrlHandler.Companion.base64UrlRegex) && it.isValidBase64UrlContent() } ?: false

            _root_ide_package_.id.walt.policies2.vc.policies.status.Base64UrlType.Regular -> matches(_root_ide_package_.id.walt.policies2.vc.policies.status.Base64UrlHandler.Companion.base64UrlRegex) && isValidBase64UrlContent()
        }
    }?.getOrElse { false } ?: false

    private fun String.isValidBase64UrlContent(): Boolean = runCatching { base64UrlDecode() }.isSuccess
}

sealed class Base64UrlType {
    object Regular : id.walt.policies2.vc.policies.status.Base64UrlType()
    object Multibase : id.walt.policies2.vc.policies.status.Base64UrlType()
}

data class Base64UrlResult(
    val type: id.walt.policies2.vc.policies.status.Base64UrlType,
    val originalString: String,
    val decodedData: ByteArray,
    val cleanEncodedString: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is id.walt.policies2.vc.policies.status.Base64UrlResult) return false

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
