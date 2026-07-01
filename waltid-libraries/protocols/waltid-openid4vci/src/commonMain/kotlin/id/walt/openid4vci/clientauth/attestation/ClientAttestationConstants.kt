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
}
