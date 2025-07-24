package id.walt.policies.policies.status.expansion

import id.walt.policies.policies.Base64UrlHandler
import id.walt.policies.policies.Base64UrlType
import java.io.InputStream
import java.util.zip.GZIPInputStream

class BitstringStatusListExpansionAlgorithm(
    private val base64UrlHandler: Base64UrlHandler,
) : StatusListExpansionAlgorithm {
    override operator fun invoke(bitstring: String): InputStream {
        val base64UrlResult = base64UrlHandler.decodeBase64Url(bitstring)
        require(base64UrlResult.type == Base64UrlType.Multibase) { "Expecting multibase base64-url, got regular base64-url: $bitstring" }
        return GZIPInputStream(base64UrlResult.decodedData.inputStream())
    }
}