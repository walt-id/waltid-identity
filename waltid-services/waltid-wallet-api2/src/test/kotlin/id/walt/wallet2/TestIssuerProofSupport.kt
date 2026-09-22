package id.walt.wallet2

import id.walt.crypto.keys.Key
import id.walt.openid4vci.proofs.CredentialNonceBinding
import id.walt.openid4vci.proofs.CredentialNonceValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.IssuedCredentialNonce
import id.walt.openid4vci.proofs.JwtCredentialNonceService
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenVerifier
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
    private val accessTokenVerifier = JwtAccessTokenVerifier(resolver = { signingKey })

    suspend fun issueNonce(): IssuedCredentialNonce = nonceService.issue(binding)

    /**
     * These inline issuers do not run the credential endpoint's access-token middleware, so the
     * proof context has to take `client_id` from the bearer token the wallet just received.
     * Otherwise a client-bound proof `iss` is checked against a missing client id.
     */
    suspend fun validationContext(
        request: CredentialRequest,
        authorization: String? = null,
    ): CredentialProofValidationContext {
        val bound = request.withAccessTokenFrom(authorization)
        return CredentialProofValidationContext(
            credentialIssuer = binding.credentialIssuer,
            clientId = bound.accessTokenClientId,
            anonymousPreAuthorizedAccess = bound.anonymousPreAuthorizedAccess,
            nonceValidation = CredentialNonceValidationContext(
                service = nonceService,
                binding = binding,
            ),
        )
    }

    private suspend fun CredentialRequest.withAccessTokenFrom(authorization: String?): CredentialRequest {
        val token = authorization
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != authorization }
            ?: return this
        val claims = accessTokenVerifier.verify(
            token = token,
            expectedIssuer = binding.credentialIssuer,
            expectedAudience = null,
        )
        val clientId = claims[JwtPayloadClaims.CLIENT_ID]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        val anonymous = clientId == null &&
            claims[JwtPayloadClaims.PRE_AUTHORIZED_CODE]?.jsonPrimitive?.contentOrNull != null
        return withAccessTokenClient(clientId, anonymous)
    }
}
