@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.keyresolver.JwtKeyResolver
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireSuccess
import id.walt.credentials.presentations.W3CPresentationValidationError
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.toPublicJwk
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val policyId = "jwt_vc_json/envelope_signature"

@Serializable
@SerialName(policyId)
class SignatureJwtVcJsonVPPolicy(
    private val allowedAlgorithms: Set<String> = JwsAlgorithm.entries.mapTo(mutableSetOf(), JwsAlgorithm::identifier),
) : JwtVcJsonVPPolicy() {

    internal val hasDefaultAlgorithmConfiguration: Boolean
        get() = allowedAlgorithms == JwsAlgorithm.entries.mapTo(mutableSetOf(), JwsAlgorithm::identifier)

    override val id = policyId
    override val description = "Verify the presentation envelope signature; signer trust is evaluated separately"

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
        val decoded = CompactJws.decodeUnverified(presentation.jwt)
        val algorithm = decoded.algorithm
        require(algorithm.identifier in allowedAlgorithms) { "VP JWT algorithm is not allowed: ${algorithm.identifier}" }
        val crypto2Verification = verifyJwtVpWithCrypto2(presentation.jwt, presentation.payload)
        val vpJwtStringVerification = if (crypto2Verification == null) {
            require(algorithm == JwsAlgorithm.ES256K) {
                "Legacy VP verification is only permitted for ES256K"
            }
            val kid = decoded.protectedHeader["kid"]?.jsonPrimitive?.contentOrNull
            if (presentation.issuer?.startsWith("did:") == true) {
                requireNotNull(kid) { "ES256K DID presentation must include kid" }
            }
            val holderKey = JwtKeyResolver.resolveFromJwt(
                jwtHeader = decoded.protectedHeader,
                jwtPayload = presentation.payload,
            )
            presentationRequireNotNull(holderKey, W3CPresentationValidationError.ISSUER_NOT_FOUND) {
                "Could not resolve ES256K VP signer key for issuer '${presentation.issuer}'"
            }
            val holderJwk = holderKey.exportJWKObject()
            require(!Jwk.containsPrivateMaterial(holderJwk)) { "VP verification key must be public" }
            holderJwk["alg"]?.jsonPrimitive?.contentOrNull?.let { declaredAlgorithm ->
                require(declaredAlgorithm == JwsAlgorithm.ES256K.identifier) {
                    "VP signer JWK alg does not permit ES256K"
                }
            }
            val legacySource = when {
                presentation.issuer?.startsWith("did:") == true -> "DID"
                "x5c" in decoded.protectedHeader -> "X5C"
                presentation.issuer?.startsWith("https://") == true -> "WELL_KNOWN"
                else -> "UNKNOWN"
            }
            if (legacySource == "DID") {
                require(holderKey.getKeyId() == kid) { "Resolved ES256K DID key does not exactly match kid" }
            }
            addResult("holder_key_source", legacySource)
            addOptionalJsonResult("holder_key_jwk") { holderJwk }
            holderKey.verifyJws(presentation.jwt).mapCatching { verifiedPayload ->
                require(verifiedPayload == presentation.payload) {
                    "Verified ES256K VP JWT payload does not match parsed presentation payload"
                }
                verifiedPayload
            }
        } else {
            addResult("holder_key_source", crypto2Verification.source)
            crypto2Verification.signerIdentifier?.let { addResult("signer_identifier", it) }
            if (crypto2Verification.certificateChain.isNotEmpty()) {
                addResult("holder_certificate_chain", crypto2Verification.certificateChain)
            }
            addOptionalJsonResult("holder_key_jwk") {
                Jwk.parse(
                    requireNotNull(crypto2Verification.key.capabilities.publicKeyExporter)
                        .exportPublicKey().toPublicJwk(crypto2Verification.key.spec)
                )
            }
            crypto2Verification.result
        }

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
