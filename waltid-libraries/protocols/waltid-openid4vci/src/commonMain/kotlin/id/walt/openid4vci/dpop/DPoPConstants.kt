package id.walt.openid4vci.dpop

object DPoPConstants {
    const val HEADER_NAME = "DPoP"
    const val JWT_TYPE = "dpop+jwt"
    const val HTTP_METHOD_CLAIM = "htm"
    const val HTTP_URI_CLAIM = "htu"
    const val ACCESS_TOKEN_HASH_CLAIM = "ath"
    const val ES256 = "ES256"

    val SUPPORTED_SIGNING_ALGORITHMS: Set<String> = setOf(ES256)
}
