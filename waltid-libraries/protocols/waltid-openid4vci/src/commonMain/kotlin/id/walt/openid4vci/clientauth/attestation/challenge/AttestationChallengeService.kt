package id.walt.openid4vci.clientauth.attestation.challenge

interface AttestationChallengeService {
    suspend fun issue(authorizationServerIssuer: String): String
    suspend fun validate(challenge: String, authorizationServerIssuer: String): Boolean
}
