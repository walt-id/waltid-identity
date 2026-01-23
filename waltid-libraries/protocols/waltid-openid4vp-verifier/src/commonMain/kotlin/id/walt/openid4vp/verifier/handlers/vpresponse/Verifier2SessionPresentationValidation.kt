package id.walt.openid4vp.verifier.handlers.vpresponse

import id.walt.credentials.formats.DigitalCredential
import id.walt.openid4vp.verifier.data.PresentationValidationResult
import id.walt.openid4vp.verifier.verification.Verifier2PresentationValidator
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import io.github.oshai.kotlinlogging.KotlinLogging

object Verifier2SessionPresentationValidation {

    private val log = KotlinLogging.logger {}

    internal suspend fun validateVPToken(
        authorizationRequest: AuthorizationRequest,
        vpTokenContents: Map<String, List<String>>,

        // DC API:
        isDcApi: Boolean?,
        expectedOrigins: List<String>?,
        /** JWK Thumbprint of ephemeralDecryptionKey if encrypted (this is required for HAIP) */
        jwkThumbprint: String?
    ): PresentationValidationResult {
        var allPresentationsValid = true

        val allSuccessfullyValidatedAndProcessedData =
            // queryId -> [validated credentials]
            mutableMapOf<String, MutableList<DigitalCredential>>() // Or a more structured object

        val expectedOrigin = expectedOrigins?.first()
        val expectedAudience = if (isDcApi == true) "origin:$expectedOrigin" else authorizationRequest.clientId
        val isEncrypted = authorizationRequest.responseMode in OpenID4VPResponseMode.ENCRYPTED_RESPONSES

        for ((queryId, presentedItemsJsonElements) in vpTokenContents) {
            val originalCredentialQuery = authorizationRequest.dcqlQuery?.credentials
                ?.find { credentialQuery -> credentialQuery.id == queryId }

            if (originalCredentialQuery == null) {
                log.warn { "Received presentation for unknown queryId '$queryId' in vp_token." }
                // This is a protocol error or a mismatch. Decide how to handle.
                // For strictness, maybe consider the whole vp_token invalid.
                // allPresentationsValid = false; break;
                continue // Or ignore this entry
            }

            if (presentedItemsJsonElements.isEmpty()) {
                // This shouldn't happen if the Wallet adheres to the spec (no key for empty results).
                // If it does, it might mean an optional query had no matches, but the Wallet still included the key.
                log.warn { "Empty presentation list received for queryId '$queryId'." }
                continue
            }

            // If multiple=false in originalCredentialQuery, presentedItemsJsonElements should have size 1.
            if (!originalCredentialQuery.multiple && presentedItemsJsonElements.size > 1) {
                log.warn { "Multiple presentations received for queryId '$queryId' where multiple=false was expected." }
                // Handle as an error or process only the first one.
                // allPresentationsValid = false; break;
                // For now, let's process only the first if multiple=false
            }
            val itemsToProcess =
                if (!originalCredentialQuery.multiple) presentedItemsJsonElements.take(1) else presentedItemsJsonElements

            for (presentationJsonElement in itemsToProcess) {
                val presentationString = presentationJsonElement
                /*if (presentationString == null && originalCredentialQuery.format != CredentialFormat.LDP_VC) { // LDP_VC can be an object
                    log.warn { "Presentation for queryId '$queryId' is not a string, but format is ${originalCredentialQuery.format}." }
                    // allPresentationsValid = false; break // out of inner loop
                    continue
                }*/

                val validationOutcome = Verifier2PresentationValidator.validatePresentation(
                    presentationString = presentationString,
                    expectedFormat = originalCredentialQuery.format,
                    expectedAudience = expectedAudience,
                    expectedNonce = authorizationRequest.nonce!!,
                    responseUri = authorizationRequest.responseUri,
                    originalClaimsQuery = originalCredentialQuery.claims,

                    isDcApi = isDcApi == true,
                    isEncrypted = isEncrypted,
                    verifierOrigin = expectedOrigin, // Raw origin needed for mdoc,
                    jwkThumbprint = jwkThumbprint
                )

                if (validationOutcome.isSuccess) {
                    val validatedCredential = validationOutcome.getOrThrow().credentials
                    log.info { "Successfully validated presentation for queryId '$queryId', credential ID (if available): $validatedCredential" }
                    allSuccessfullyValidatedAndProcessedData
                        .getOrPut(queryId) { mutableListOf() }
                        .addAll(validatedCredential)
                } else {
                    log.warn { "Validation failed for a presentation under queryId '$queryId': ${validationOutcome.exceptionOrNull()?.message}" }
                    allPresentationsValid = false
                    // break // Decide if one failed presentation invalidates the whole vp_token
                }
            } // End loop over presentationJsonElements for a queryId
            if (!allPresentationsValid) break // If one queryId fails, stop processing others
        } // End loop over vpTokenContents

        return PresentationValidationResult(allPresentationsValid, allSuccessfullyValidatedAndProcessedData)
    }

}
