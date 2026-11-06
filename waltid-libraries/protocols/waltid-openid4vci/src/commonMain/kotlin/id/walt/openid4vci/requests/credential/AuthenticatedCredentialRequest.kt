package id.walt.openid4vci.requests.credential

import id.walt.openid4vci.Session
import id.walt.openid4vci.clientauth.attestation.VerifiedClientAttestation

/** Populated only after access-token verification; never decoded from credential-request parameters. */
internal data class CredentialRequestAuthentication(
    val clientId: String?,
    val anonymousPreAuthorizedAccess: Boolean,
    val clientAttestation: VerifiedClientAttestation?,
    val clientAttestationRequired: Boolean,
)

/** Preserve verified authentication when the embedding issuer adds its session or signing identity. */
internal class AuthenticatedCredentialRequest(
    private val original: CredentialRequest,
    val authentication: CredentialRequestAuthentication,
) : CredentialRequest by original {
    override fun withSession(session: Session?): CredentialRequest =
        AuthenticatedCredentialRequest(original.withSession(session), authentication)
    override fun withIssuer(issClaim: String?): CredentialRequest =
        AuthenticatedCredentialRequest(original.withIssuer(issClaim), authentication)
    override fun withAccessTokenClient(clientId: String?, anonymousPreAuthorizedAccess: Boolean): CredentialRequest =
        AuthenticatedCredentialRequest(original.withAccessTokenClient(clientId, anonymousPreAuthorizedAccess), authentication)
}
