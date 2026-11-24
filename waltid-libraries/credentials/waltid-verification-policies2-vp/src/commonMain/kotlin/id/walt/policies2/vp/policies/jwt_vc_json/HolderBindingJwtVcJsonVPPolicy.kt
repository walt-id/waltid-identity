@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.CredentialHolderBinding
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Binds every embedded credential to the cryptographically verified envelope signer. */
@Serializable
@SerialName("jwt_vc_json/holder-binding")
class HolderBindingJwtVcJsonVPPolicy : JwtVcJsonVPPolicy() {
    override val id = "jwt_vc_json/holder-binding"
    override val description = "Bind credentials to the authenticated presentation signer"

    override suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: VerificationSessionContext?,
    ): Result<Unit> {
        // Resolve the signing key only through the signature policy, including its DID/kid checks.
        val signature = SignatureJwtVcJsonVPPolicy().runPolicy(presentation, verificationContext)
        require(signature.success) { "Presentation signature must be valid for holder binding" }
        val publicJwk = requireNotNull(signature.results["holder_key_jwk"] as? JsonObject) {
            "Verified presentation signing key is unavailable"
        }
        val thumbprint = Jwk.sha256Thumbprint(EncodedKey.Jwk(
            data = BinaryData(publicJwk.toString().encodeToByteArray()), privateMaterial = false,
        ))
        val issuer = requireNotNull(presentation.issuer)
        val credentials = requireNotNull(presentation.credentials).also { require(it.isNotEmpty()) }
        for (credential in credentials) {
            CredentialHolderBinding.requireBoundToPresenter(credential, issuer, thumbprint)
        }
        return success()
    }
}
