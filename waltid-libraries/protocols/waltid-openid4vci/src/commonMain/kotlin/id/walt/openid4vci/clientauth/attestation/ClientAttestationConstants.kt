package id.walt.openid4vci.clientauth.attestation

object ClientAttestationHeaders {
    const val CLIENT_ATTESTATION = "OAuth-Client-Attestation"
    const val CLIENT_ATTESTATION_POP = "OAuth-Client-Attestation-PoP"
}

object ClientAttestationJwtTypes {
    const val CLIENT_ATTESTATION = "oauth-client-attestation+jwt"
    const val CLIENT_ATTESTATION_POP = "oauth-client-attestation-pop+jwt"
}

object ClientAttestationSigningAlgorithms {
    const val ES256 = "ES256"
    const val ES384 = "ES384"
    const val ES512 = "ES512"
    const val ES256K = "ES256K"
    const val RS256 = "RS256"
    const val RS384 = "RS384"
    const val RS512 = "RS512"
    const val ED_DSA = "EdDSA"

    val SUPPORTED_JWS_ALGORITHMS = setOf(
        ES256,
        ES384,
        ES512,
        RS256,
        RS384,
        RS512,
        ED_DSA,
    )
}
