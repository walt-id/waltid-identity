@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireSuccess
import id.walt.credentials.presentations.W3CPresentationValidationError
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.did.dids.DidService
import kotlinx.coroutines.coroutineScope

class SignatureJwtVcJsonVPPolicy : JwtVcJsonVPPolicy("envelope_signature", "Verify signature using holders public key") {

    override suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: JwtVcJsonVPVerificationRequest
    ): Result<Unit> = coroutineScope {
        addResult("issuer", presentation.issuer)
        presentationRequireNotNull(presentation.issuer, W3CPresentationValidationError.ISSUER_NOT_FOUND)

        // Verify its signature using the Holder's public key (obtained from vpJwt.payload.iss DID or other mechanism).
        val holderKey = DidService.resolveToKey(presentation.issuer!!).getOrThrow() // TODO: handle multi key
        addOptionalJsonResult("holder_key_jwk") { holderKey.exportJWKObject() }
        val vpJwtStringVerification = holderKey.verifyJws(presentation.jwt)

        if (vpJwtStringVerification.isSuccess) {
            addResult("verified_vp_jwt_content", vpJwtStringVerification.getOrThrow())
        }

        presentationRequireSuccess(
            vpJwtStringVerification,
            W3CPresentationValidationError.SIGNATURE_VERIFICATION_FAILED
        ) { "Failed to verify VP JWT String: ${presentation.jwt}" }

        success()
    }
}
