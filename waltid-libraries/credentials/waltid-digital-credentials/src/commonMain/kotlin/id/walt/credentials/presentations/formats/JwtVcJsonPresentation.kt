package id.walt.credentials.presentations.formats

import id.walt.credentials.CredentialParser
import id.walt.credentials.CredentialParser.getString
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.keyresolver.JwtKeyResolver
import id.walt.credentials.presentations.PresentationFormat
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireSuccess
import id.walt.credentials.presentations.W3CPresentationValidationError
import id.walt.crypto.utils.JwsUtils.decodeJws
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Represents a W3C Verifiable Presentation presented as a single JWT string.
 * The JWT payload itself contains the 'vp' object with the credential(s).
 */
@Serializable
@SerialName("jwt_vc_json")
data class JwtVcJsonPresentation(
    val jwt: String,

    val payload: JsonObject,

    /** iss / issuer.id */
    val issuer: String?,
    /** aud */
    val audience: String?,
    /** nonce */
    val nonce: String?,

    /** vp */
    val vp: JsonObject?,

    /** parsed credentials from vp.verifiableCredential */
    val credentials: List<DigitalCredential>?
) : VerifiablePresentation(format = PresentationFormat.jwt_vc_json) {

    suspend fun presentationVerification(
        expectedAudience: String?,
        expectedNonce: String
    ) {
        presentationRequireNotNull(issuer, W3CPresentationValidationError.ISSUER_NOT_FOUND)

        // Resolve the holder key using DID, x5c header, or HTTPS well-known, in that priority order.
        val jwtHeader = jwt.decodeJws().header
        val holderKey = JwtKeyResolver.resolveFromJwt(jwtHeader, payload)
        presentationRequireNotNull(holderKey, W3CPresentationValidationError.ISSUER_NOT_FOUND) {
            "Could not resolve VP signer key for issuer '$issuer'"
        }
        val vpJwtStringVerification = holderKey!!.verifyJws(jwt)

        presentationRequireSuccess(
            vpJwtStringVerification,
            W3CPresentationValidationError.SIGNATURE_VERIFICATION_FAILED
        ) { "Failed to verify VP JWT String: $jwt" }

        // Verify aud == expectedAudience.
        presentationRequire(
            audience == expectedAudience || (audience.isNullOrBlank() && expectedAudience.isNullOrBlank()),
            W3CPresentationValidationError.AUDIENCE_MISMATCH
        ) { "Expected $expectedAudience, got $audience" }

        // Verify nonce == expectedNonce.
        presentationRequire(
            nonce == expectedNonce,
            W3CPresentationValidationError.NONCE_MISMATCH
        ) { "Expected $expectedNonce, got $nonce" }

        presentationRequireNotNull(vp, W3CPresentationValidationError.MISSING_VP)
    }

    companion object {
        suspend fun parse(vpJwtString: String): Result<JwtVcJsonPresentation> {
            // Parse the vpJwtString as a JWS.
            val parsedJws = vpJwtString.decodeJws()

            val payload = parsedJws.payload

            val issuer = parsedJws.payload.getString("iss") ?: parsedJws.payload["issuer"].getString("id")

            // RFC 7519 §4.1.3 allows aud to be either a single string or an array of strings.
            val aud = payload["aud"]?.let { audElement ->
                when (audElement) {
                    is JsonPrimitive -> audElement.contentOrNull
                    is JsonArray -> audElement.firstOrNull()?.jsonPrimitive?.contentOrNull
                    else -> null
                }
            }
            val nonce = payload["nonce"]?.jsonPrimitive?.contentOrNull
            val vpClaim = payload["vp"]?.jsonObject


            // Extract the verifiableCredential array from vp.verifiableCredential.
            val vcArray = vpClaim?.get("verifiableCredential")?.jsonArray

            // For each VC string/object in the array:
            // use credentialParser to get DigitalCredential object.
            val credentials = vcArray?.mapNotNull { credentialElement ->
                credentialElement.jsonPrimitive.contentOrNull?.let { vcString ->
                    val (_, digitalCredential) = CredentialParser.detectAndParse(vcString)
                    digitalCredential
                }
            }

            return Result.success(
                JwtVcJsonPresentation(
                    vpJwtString,
                    payload,
                    issuer = issuer,
                    audience = aud,
                    nonce = nonce,
                    vp = vpClaim,
                    credentials = credentials
                )
            )
        }
    }


}
