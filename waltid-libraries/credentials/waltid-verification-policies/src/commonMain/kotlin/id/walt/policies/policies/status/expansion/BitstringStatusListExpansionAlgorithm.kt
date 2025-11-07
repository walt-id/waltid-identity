package id.walt.policies.policies.status.expansion

import id.walt.policies.policies.Base64UrlHandler
import id.walt.policies.policies.Base64UrlType
import korlibs.io.compression.deflate.GZIP
import korlibs.io.compression.uncompress

class BitstringStatusListExpansionAlgorithm(
    private val base64UrlHandler: Base64UrlHandler,
) : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray {
        val base64UrlResult = base64UrlHandler.decodeBase64Url(bitstring)
        require(base64UrlResult.type == Base64UrlType.Multibase) { "Expecting multibase base64-url, got regular base64-url: $bitstring" }
        return GZIP.uncompress(base64UrlResult.decodedData)
    }
}