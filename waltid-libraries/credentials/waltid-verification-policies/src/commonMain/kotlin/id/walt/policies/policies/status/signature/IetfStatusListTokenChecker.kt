package id.walt.policies.policies.status.signature

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.keyresolver.ResolvedJwtVerificationKey
import id.walt.policies.policies.status.model.StatusVerificationError
import id.walt.policies.policies.status.reader.format.CwtFormatMatcher
import id.walt.policies.policies.status.reader.format.JwtFormatMatcher
import id.walt.policies2.vc.policies.status.signature.IetfStatusListTokenClaims
import id.walt.policies2.vc.policies.status.signature.StatusListEmbeddedKeyReference
import id.walt.policies2.vc.policies.status.signature.StatusListSignatureVerifier
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizationRequest
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import id.walt.policies2.vc.policies.status.signature.authorizeStatusListSigner

internal const val RAW_CREDENTIAL_CONTEXT_KEY = "id.walt.policies.status.raw-credential"

internal const val STATUS_LIST_SIGNER_AUTHORIZER_CONTEXT_KEY = "id.walt.policies.status.signer-authorizer"

/**
 * Verifies an IETF status-list JWT or hex-encoded CWT before its status bits are read.
 * Binary CWTs are hex because [id.walt.policies.policies.status.CredentialFetcher] returns text.
 */
class IetfStatusListTokenChecker(
    private val signatureVerifier: StatusListSignatureVerifier = StatusListSignatureVerifier(),
    private val jwtFormatMatcher: JwtFormatMatcher = JwtFormatMatcher(),
    private val cwtFormatMatcher: CwtFormatMatcher = CwtFormatMatcher(),
    private val nowEpochSeconds: () -> Long = { kotlin.time.Clock.System.now().epochSeconds },
) {
    suspend fun check(
        content: String,
        statusListUri: String,
        referencedCredential: DigitalCredential,
        signerAuthorizer: StatusListSignerAuthorizer?,
    ) {
        val token = content.trim()
        when {
            jwtFormatMatcher.matches(token) -> checkJwt(token, statusListUri, referencedCredential, signerAuthorizer)
            cwtFormatMatcher.matches(token) -> checkCwt(token, statusListUri, referencedCredential, signerAuthorizer)
            else -> throw StatusVerificationError("Unsupported unsigned status-list token")
        }
    }

    private suspend fun checkJwt(
        token: String,
        statusListUri: String,
        referencedCredential: DigitalCredential,
        signerAuthorizer: StatusListSignerAuthorizer?,
    ) {
        val verified = signatureVerifier.verifyJwtWithSigner(token, referencedCredential).getOrElse {
            throw StatusVerificationError("Status list JWT signature verification failed: ${it.message}")
        }
        authorize(verified.signer, verified.embeddedKeyReference, statusListUri, referencedCredential, signerAuthorizer)
        IetfStatusListTokenClaims.validate(verified.payload, statusListUri, nowEpochSeconds())
    }

    private suspend fun checkCwt(
        token: String,
        statusListUri: String,
        referencedCredential: DigitalCredential,
        signerAuthorizer: StatusListSignerAuthorizer?,
    ) {
        val verified = signatureVerifier.verifyCwtFromHexWithSigner(token, referencedCredential).getOrElse {
            throw StatusVerificationError("Status list CWT signature verification failed: ${it.message}")
        }
        authorize(verified.signer, verified.embeddedKeyReference, statusListUri, referencedCredential, signerAuthorizer)
        IetfStatusListTokenClaims.validate(verified.payload, statusListUri, nowEpochSeconds())
    }

    private suspend fun authorize(
        signer: ResolvedJwtVerificationKey,
        embeddedKeyReference: StatusListEmbeddedKeyReference?,
        statusListUri: String,
        referencedCredential: DigitalCredential,
        signerAuthorizer: StatusListSignerAuthorizer?,
    ) {
        authorizeStatusListSigner(
            StatusListSignerAuthorizationRequest(
                referencedCredential = referencedCredential,
                statusListUri = statusListUri,
                signer = signer,
                embeddedKeyReference = embeddedKeyReference,
            ),
            signerAuthorizer,
        )
    }
}
