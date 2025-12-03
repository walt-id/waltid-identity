@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireSuccess
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import kotlinx.coroutines.coroutineScope

class KbJwtSignatureSdJwtVPPolicy : DcSdJwtVPPolicy("kb-jwt_signature", "Verify the KB-JWTs signature with the holders key") {

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: DcSdJwtVPVerificationRequest
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
