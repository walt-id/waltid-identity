@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireSuccess
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.toPublicJwk
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "dc+sd-jwt/kb-jwt_signature"
private val defaultAllowedAlgorithms =
    DcSdJwtPresentation.DEFAULT_ALLOWED_JWS_ALGORITHMS.map(JwsAlgorithm::identifier).toSet()

@Serializable
@SerialName(policyId)
class KbJwtSignatureSdJwtVPPolicy(
    private val allowedAlgorithms: Set<String> = defaultAllowedAlgorithms,
) : DcSdJwtVPPolicy() {

    internal val hasDefaultAlgorithmConfiguration: Boolean
        get() = allowedAlgorithms == defaultAllowedAlgorithms

    override val id = policyId
    override val description = "Verify the KB-JWTs signature with the holders key"

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> = coroutineScope {
        val allowedJwsAlgorithms = allowedAlgorithms.mapTo(mutableSetOf(), JwsAlgorithm::parse)
        require(allowedJwsAlgorithms.isNotEmpty()) { "KB-JWT algorithm allowlist must not be empty" }
        requireKbJwtType(presentation.keyBindingJwt)
        val algorithm = CompactJws.decodeUnverified(presentation.keyBindingJwt).algorithm
        require(algorithm in allowedJwsAlgorithms) { "KB-JWT algorithm is not allowed: ${algorithm.identifier}" }

        // Resolve holder's public key
        val holderKey = presentation.credential.getHolderCrypto2Key()
        presentationRequireNotNull(holderKey, DcSdJwtPresentationValidationError.MISSING_CNF)
        val holderJwk = Jwk.parse(
            requireNotNull(holderKey.capabilities.publicKeyExporter) { "Holder key cannot export its public key" }
                .exportPublicKey()
                .toPublicJwk(holderKey.spec)
        )
        require(!Jwk.containsPrivateMaterial(holderJwk)) { "Holder confirmation JWK must be public" }
        addOptionalJsonResult("holder_key_jwk") { holderJwk }

        // Verify the KB-JWT's signature with the holder's key
        val kbJwtVerificationResult = verifyKbJwtWithCrypto2(
            presentation.keyBindingJwt,
            holderKey,
            allowedJwsAlgorithms,
        )

        if (kbJwtVerificationResult.isSuccess) {
            addResult("verified_kb_jwt_content", kbJwtVerificationResult.getOrThrow())
        }

        presentationRequireSuccess(kbJwtVerificationResult, DcSdJwtPresentationValidationError.SIGNATURE_VERIFICATION_FAILED)

        success()
    }
}
