package id.walt.credentials.presentations.formats

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.DcqlValidationError
import id.walt.credentials.presentations.PresentationFormat
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireNotNull
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequireSuccess
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.ShaUtils
import id.walt.dcql.DcqlMatcher.resolveClaimPath
import id.walt.dcql.models.ClaimsQuery
import id.walt.did.dids.DidService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val log = KotlinLogging.logger { "DcSdJwtPresentationParser" }

/**
 * Represents an IETF SD-JWT Verifiable Credential presentation.
 * This is a single string composed of the SD-JWT, disclosures, and a Key-Binding JWT,
 * separated by '~'.
 */
@Serializable
@SerialName("dc+sd-jwt")
data class DcSdJwtPresentation(
    /** The core, issuer-signed SD-JWT part of the presentation. */
    val sdJwt: String,

    /** A list of the base64url-encoded disclosure strings being presented. */
    val disclosures: List<String> = emptyList(),

    /** The holder-signed Key-Binding JWT that proves possession and binds to the transaction. */
    val keyBindingJwt: String,

    val credential: DigitalCredential,

    // claims:
    val holderKid: String?,
    val audience: String?,
    val nonce: String?,
    val sdHash: String?,
    val presentationStringHashable: String
) : VerifiablePresentation(format = PresentationFormat.`dc+sd-jwt`) {

    suspend fun presentationVerification(
        expectedAudience: String,
        expectedNonce: String,
        originalClaimsQuery: List<ClaimsQuery>?
    ) {
        // Validate Key Binding JWT

        // Resolve holder's public key
        presentationRequireNotNull(holderKid, DcSdJwtPresentationValidationError.MISSING_CNF_KID)
        val holderKey = DidService.resolveToKey(holderKid).getOrThrow()

        // Verify the KB-JWT's signature with the holder's key
        val kbJwtVerificationResult = holderKey.verifyJws(keyBindingJwt)
        presentationRequireSuccess(kbJwtVerificationResult, DcSdJwtPresentationValidationError.SIGNATURE_VERIFICATION_FAILED)

        // Validate SD-JWT Core + Disclosures
        presentationRequire(
            audience == expectedAudience,
            DcSdJwtPresentationValidationError.AUDIENCE_MISMATCH
        ) { "Expected $expectedAudience, got $audience" }

        presentationRequire(
            nonce == expectedNonce,
            DcSdJwtPresentationValidationError.NONCE_MISMATCH
        ) { "Expected $expectedNonce, got $nonce" }

        presentationRequireNotNull(
            sdHash,
            DcSdJwtPresentationValidationError.MISSING_SD_HASH
        )

        // Reconstruct and verify sd_hash
        // The hash must be calculated over the presented disclosures in the exact same way the Wallet did.
        val recalculatedSdHash = ShaUtils.calculateSha256Base64Url(presentationStringHashable)

        presentationRequire(sdHash == recalculatedSdHash, DcSdJwtPresentationValidationError.SD_HASH_MISMATCH)
        log.trace { "KB-JWT validated successfully. sd_hash matches." }


        // (Optional but Recommended) Validate that the claims presented match what was requested
        if (originalClaimsQuery != null) {
            val claimsValidationResult = validateClaimsAgainstCredential(credential, originalClaimsQuery)
            presentationRequireSuccess(claimsValidationResult, DcSdJwtPresentationValidationError.MISMATCH_PRESENTED_CLAIMS)
        }
    }

    companion object {
        suspend fun parse(sdJwtPresentationString: String): Result<DcSdJwtPresentation> {
            // 1. Split the presentation string by '~'
            val parts = sdJwtPresentationString.split('~')
            if (parts.size < 2) { // Must have at least SD-JWT core and KB-JWT
                return Result.failure(IllegalArgumentException("Invalid SD-JWT presentation format: not enough parts separated by '~'."))
            }

            val sdJwtCore = parts.first()
            val kbJwt = parts.last()
            val presentedDisclosures = if (parts.size > 2) parts.subList(1, parts.size - 1) else emptyList()

            log.trace { "Parsed SD-JWT Presentation: Core parts=${parts.size - presentedDisclosures.size - 1}, Disclosures=${presentedDisclosures.size}, KB-JWT parts=1" }
            log.trace { "SD-JWT Core Part: $sdJwtCore" }
            log.trace { "Disclosures: $presentedDisclosures" }
            log.trace { "KB JWT: $kbJwt" }


            // 2.1 Parse the SD-JWT core (without verifying signature yet) to get the holder's public key reference (`cnf` claim)
            val sdJwtCorePayload = sdJwtCore.decodeJws().payload
            val cnfClaim = sdJwtCorePayload["cnf"]?.jsonObject
            val holderKid = cnfClaim?.get("kid")?.jsonPrimitive?.contentOrNull

            val kbJwtPayload = kbJwt.decodeJws().payload

            // 2.4 Extract and verify claims from KB-JWT
            val aud = kbJwtPayload["aud"]?.jsonPrimitive?.contentOrNull
            val nonce = kbJwtPayload["nonce"]?.jsonPrimitive?.contentOrNull
            val sdHash = kbJwtPayload["sd_hash"]?.jsonPrimitive?.contentOrNull

            val presentationStringHashable = if (presentedDisclosures.isNotEmpty()) presentedDisclosures.joinToString("~") else ""

            // CredentialParser needs to handle this reconstruction and validation.
            // It should verify that the digests in the `_sd` array of the core match the hashes of the provided disclosures.
            val (_, reconstructedCredential) = CredentialParser.detectAndParse("$sdJwtCore~$presentationStringHashable")
                ?: return Result.failure(IllegalArgumentException("Failed to parse/reconstruct credential from SD-JWT core and disclosures."))

            return Result.success(
                DcSdJwtPresentation(
                    sdJwt = sdJwtCore,
                    disclosures = presentedDisclosures,
                    keyBindingJwt = kbJwt,
                    credential = reconstructedCredential,
                    holderKid = holderKid,
                    audience = aud,
                    nonce = nonce,
                    sdHash = sdHash,
                    presentationStringHashable = presentationStringHashable
                )
            )
        }

        /**
         * A helper to check if the claims in a validated credential match the original DCQL claims query.
         */
        private fun validateClaimsAgainstCredential(
            credential: DigitalCredential,
            claimsQuery: List<ClaimsQuery>
        ): Result<Unit> {
            for (claimQuery in claimsQuery) {
                // Use a helper to resolve the path in the credential's data
                val resolvedValue = resolveClaimPath(credential.credentialData, claimQuery.path)

                presentationRequireNotNull(resolvedValue, DcqlValidationError.MISSING_CLAIM) { "Claim is: ${claimQuery.path}" }

                // Optionally, re-check value constraints if they were present in the query
                if (!claimQuery.values.isNullOrEmpty()) {
                    presentationRequire(
                        !claimQuery.values!!.none { it == resolvedValue },
                        DcqlValidationError.CLAIM_MISMATCH
                    ) { "Claim is ${claimQuery.path}" }
                }
            }
            return Result.success(Unit)
        }
    }


}
