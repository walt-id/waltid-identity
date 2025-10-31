package id.walt.policies.policies.status.expansion

import id.walt.policies.policies.Base64UrlHandler
import id.walt.policies.policies.Base64UrlType
import java.io.InputStream
import java.util.zip.InflaterInputStream

class TokenStatusListExpansionAlgorithm(
    private val base64UrlHandler: Base64UrlHandler,
) : StatusListExpansionAlgorithm {
    override operator fun invoke(bitstring: String): InputStream {
        val base64UrlResult = base64UrlHandler.decodeBase64Url(bitstring)
        require(base64UrlResult.type == Base64UrlType.Regular) { "Expecting regular base64-url, got: $bitstring" }
        return InflaterInputStream(base64UrlResult.decodedData.inputStream())
    }
}