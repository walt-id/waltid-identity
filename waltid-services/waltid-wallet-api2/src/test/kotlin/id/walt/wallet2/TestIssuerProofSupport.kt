package id.walt.wallet2

import id.walt.crypto.keys.Key
import id.walt.openid4vci.proofs.CredentialNonceBinding
import id.walt.openid4vci.proofs.CredentialNonceValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.IssuedCredentialNonce
import id.walt.openid4vci.proofs.JwtCredentialNonceService
import id.walt.openid4vci.requests.credential.CredentialRequest

/** Shared proof support for the inline OpenID4VCI issuers used by wallet integration tests. */
class TestIssuerProofSupport(
    issuer: String,
    signingKey: Key,
) {
    private val binding = CredentialNonceBinding(
        credentialIssuer = issuer,
        credentialEndpoint = "$issuer/credential",
        nonceEndpoint = "$issuer/nonce",
    )
    private val nonceService = JwtCredentialNonceService(
        signingKeyResolver = { signingKey },
        verificationKeyResolver = { signingKey },
    )

    suspend fun issueNonce(): IssuedCredentialNonce = nonceService.issue(binding)

    fun validationContext(request: CredentialRequest): CredentialProofValidationContext =
        CredentialProofValidationContext(
            credentialIssuer = binding.credentialIssuer,
            clientId = request.accessTokenClientId,
            anonymousPreAuthorizedAccess = request.anonymousPreAuthorizedAccess,
            nonceValidation = CredentialNonceValidationContext(
                service = nonceService,
                binding = binding,
            ),
        )
}
