@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.keyresolver.JwtKeyResolver
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireSuccess
import id.walt.credentials.presentations.W3CPresentationValidationError
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.crypto.utils.JwsUtils.decodeJws
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "jwt_vc_json/envelope_signature"

@Serializable
@SerialName(policyId)
class SignatureJwtVcJsonVPPolicy : JwtVcJsonVPPolicy() {

    override val id = policyId
    override val description = "Verify signature using holders public key"

    override suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> = coroutineScope {
        addResult("issuer", presentation.issuer)
        presentationRequireNotNull(presentation.issuer, W3CPresentationValidationError.ISSUER_NOT_FOUND)

        // Resolve the holder key using the same priority chain used for credential issuers:
        // 1. DID (iss claim is a DID URL)
        // 2. x5c header in the VP JWT
        // 3. HTTPS well-known issuer metadata endpoint
        val jwtHeader = presentation.jwt.decodeJws().header
        val holderKey = JwtKeyResolver.resolveFromJwt(
            jwtHeader = jwtHeader,
            jwtPayload = presentation.payload,
        )
        presentationRequireNotNull(holderKey, W3CPresentationValidationError.ISSUER_NOT_FOUND) {
            "Could not resolve VP signer key for issuer '${presentation.issuer}' — no supported key resolution method succeeded (tried DID, x5c, HTTPS well-known)"
        }
        addOptionalJsonResult("holder_key_jwk") { holderKey!!.exportJWKObject() }

        val vpJwtStringVerification = holderKey!!.verifyJws(presentation.jwt)

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
