package id.walt.policies2.vc.policies.status.expansion

import id.walt.policies2.vc.policies.status.Base64UrlHandler
import id.walt.policies2.vc.policies.status.Base64UrlType
import korlibs.io.compression.deflate.ZLib
import korlibs.io.compression.uncompress

class TokenStatusListExpansionAlgorithm(
    private val base64UrlHandler: Base64UrlHandler,
) : StatusListExpansionAlgorithm {
    override suspend operator fun invoke(bitstring: String): ByteArray {
        val base64UrlResult = base64UrlHandler.decodeBase64Url(bitstring)
        require(base64UrlResult.type == Base64UrlType.Regular) { "Expecting regular base64-url, got: $bitstring" }
        return ZLib.uncompress(base64UrlResult.decodedData)
    }
}
