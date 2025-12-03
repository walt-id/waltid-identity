@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireSuccess
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "dc+sd-jwt/kb-jwt_signature"

@Serializable
@SerialName(policyId)
class KbJwtSignatureSdJwtVPPolicy : DcSdJwtVPPolicy() {

    override val id = policyId
    override val description = "Verify the KB-JWTs signature with the holders key"

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext
    ): Result<Unit> = coroutineScope {
        // Resolve holder's public key
        val holderKey = presentation.credential.getHolderKey()
        presentationRequireNotNull(holderKey, DcSdJwtPresentationValidationError.MISSING_CNF)
        addOptionalJsonResult("holder_key_jwk") { holderKey.exportJWKObject() }

        // Verify the KB-JWT's signature with the holder's key
        val kbJwtVerificationResult = holderKey.verifyJws(presentation.keyBindingJwt)

        if (kbJwtVerificationResult.isSuccess) {
            addResult("verified_kb_jwt_content", kbJwtVerificationResult.getOrThrow())
        }

        presentationRequireSuccess(kbJwtVerificationResult, DcSdJwtPresentationValidationError.SIGNATURE_VERIFICATION_FAILED)

        success()
    }
}
