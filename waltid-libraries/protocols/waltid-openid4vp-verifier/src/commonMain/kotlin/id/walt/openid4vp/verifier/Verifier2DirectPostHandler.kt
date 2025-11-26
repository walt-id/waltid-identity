package id.walt.openid4vp.verifier

import id.walt.credentials.formats.DigitalCredential
import id.walt.openid4vp.verifier.Verification2Session.VerificationSessionStatus
import id.walt.openid4vp.verifier.Verifier2Response.Verifier2Error
import id.walt.openid4vp.verifier.verification.DcqlFulfillmentChecker
import id.walt.openid4vp.verifier.verification.Verifier2PresentationValidator
import id.walt.policies2.PolicyResult
import id.walt.policies2.PolicyResults
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json

object Verifier2DirectPostHandler {

    private val log = KotlinLogging.logger {}

    fun parseVpToken(vpTokenString: String): Map<String, List<String>> = try {
        Json.decodeFromString(vpTokenString)
    } catch (e: Exception) {
        log.info { "Failed to parse vp_token string: $vpTokenString. Error: ${e.message}" }
        Verifier2Error.MALFORMED_VP_TOKEN.throwAsError()
    }

    data class PresentationValidationResult(
        /** overall result */
        val presentationValid: Boolean,
        val allSuccessfullyValidatedAndProcessedData: Map<String, List<DigitalCredential>>
    )

    suspend fun presentationValidation(
        authorizationRequest: AuthorizationRequest,
        vpTokenContents: Map<String, List<String>>
    ): PresentationValidationResult {
        var allPresentationsValid = true

        val allSuccessfullyValidatedAndProcessedData =
            // queryId -> [validated credentials]
            mutableMapOf<String, MutableList<DigitalCredential>>() // Or a more structured object


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
                    expectedAudience = authorizationRequest.clientId,
                    expectedNonce = authorizationRequest.nonce!!,
                    responseUri = authorizationRequest.responseUri,
                    originalClaimsQuery = originalCredentialQuery.claims
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

    suspend fun validatePolicies(
        policies: Verification2Session.DefinedVerificationPolicies,
        allSuccessfullyValidatedAndProcessedData: Map<String, List<DigitalCredential>>
    ): PolicyResults {
        val vcPolicyResults = ArrayList<PolicyResult>()
        val specificVcPolicyResults = emptyMap<String, List<PolicyResult>>()


        // VP Policies:
        /*  // TODO: vpPolicies
        val vpPolicyResults = session.policies.vpPolicies.policies.forEach {
            it.verify(vpTokenContents)
        }*/

        allSuccessfullyValidatedAndProcessedData.forEach { (queryId, credentials) ->
            credentials.forEach { credential ->

                policies.vcPolicies.policies.forEach { policy ->
                    log.trace { "Validating '$queryId' credential with policy '${policy.id}': $credential" }
                    val result = policy.verify(credential)
                    log.trace { "'$queryId' credential '${policy.id}' result: $result" }

                    vcPolicyResults.add(
                        PolicyResult(
                            policy = policy,
                            success = result.isSuccess,
                            result = result.getOrNull(),
                            error = result.exceptionOrNull()?.message
                        )
                    )
                }
                // Specific VC Policies:
                //session.policies.specificVcPolicies
            }
        }

        val policyResults = PolicyResults(
            // vpPolicies = vpPolicyResults, // TODO: vpPolicies
            vcPolicies = vcPolicyResults,
            specificVcPolicies = specificVcPolicyResults
        )

        return policyResults
    }

    /**
     * Here the receiving of credentials through the Verifiers endpoints
     * (e.g. direct_post endpoint) is handled
     */
    suspend fun handleDirectPost(
        verificationSession: Verification2Session?,
        vpTokenString: String,
        receivedState: String?,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit,
        failSessionCallback: suspend (session: Verification2Session, event: SessionEvent, updateSession: suspend (Verification2Session, SessionEvent, block: Verification2Session.() -> Unit) -> Unit) -> Unit,
    ): Map<String, String> {
        suspend fun Verification2Session.updateSession(event: SessionEvent, block: Verification2Session.() -> Unit) =
            updateSessionCallback.invoke(this, event, block)

        suspend fun Verification2Session.failSession(event: SessionEvent) =
            failSessionCallback.invoke(this, event, updateSessionCallback)

        if (receivedState == null) {
            log.info { "Direct POST response received without 'state' parameter." }
            Verifier2Error.MISSING_STATE_PARAMETER.throwAsError()
        }

        log.debug { "Received vp_token string for state $receivedState: $vpTokenString" }

        // 1. Retrieve session data based on state (or verificationSessionId if it maps to state)
        // This session data contains the original nonce, dcql_query, and client_id, ...
        val session = verificationSession
        if (session == null) {
            log.info { "Direct POST response received with invalid or expired state: $receivedState" }
            Verifier2Error.INVALID_STATE_PARAMETER.throwAsError()
        }


        require(receivedState == session.authorizationRequest.state) { "State does not match" }

        // 2. Parse vp_token
        val vpTokenContents = parseVpToken(vpTokenString)
        log.debug { "Parsed vp_token for state $receivedState: $vpTokenContents" }

        session.updateSession(SessionEvent.attempted_presentation) {
            attempted = true
            status = VerificationSessionStatus.IN_USE
            presentedRawData = Verification2Session.PresentedRawData(vpTokenContents, receivedState)
        }

        // Process

        // queryId: from original dcql_query
        // presented credential/presentation


        // ---------------------------
        val presentationValidationResult = presentationValidation(session.authorizationRequest, vpTokenContents)
        val allPresentationsValid = presentationValidationResult.presentationValid

        // queryId -> [validated credentials]
        // Or a more structured object
        val allSuccessfullyValidatedAndProcessedData = presentationValidationResult.allSuccessfullyValidatedAndProcessedData

        session.updateSession(SessionEvent.validated_credentials_available) {
            presentedCredentials = allSuccessfullyValidatedAndProcessedData
        }

        if (!allPresentationsValid) {
            // Handle overall validation failure
            // Handle individual presentation validation failure
            log.error { "One or more presentations in vp_token failed validation for session ${session.id}" }

            session.failSession(SessionEvent.presentation_validation_failed)

            Verifier2Error.PRESENTATION_VALIDATION_FAILED.throwAsError()
        }

        // 3. Check if the set of validated presentations satisfies the overall DCQL Query
        //    (e.g., credential_sets, all *required* CredentialQuery IDs are present in allSuccessfullyValidatedAndProcessedData)
        val dcqlFulfilled = session.authorizationRequest.dcqlQuery?.let { dcqlQuery ->
            DcqlFulfillmentChecker.checkOverallDcqlFulfillment(
                dcqlQuery = dcqlQuery,
                successfullyValidatedQueryIds = allSuccessfullyValidatedAndProcessedData.keys // set of query IDs for which we have valid presentations
            )
        }
        if (dcqlFulfilled == false) {
            log.error { "The set of validated presentations does not fulfill all DCQL requirements for session ${session.id}" }

            session.failSession(SessionEvent.dcql_fulfillment_check_failed)

            Verifier2Error.REQUIRED_CREDENTIALS_NOT_PROVIDED.throwAsError()
        }

        // If we reach here, all individual presentations are valid AND the overall DCQL structure is met.
        log.info { "All presentations in vp_token validated and DCQL fulfilled for session ${session.id}." }

        // 4. If all good, proceed with business logic using `allSuccessfullyValidatedAndProcessedData`

        // verifierService.processVerifiedPresentation(sessionData.sessionId, allSuccessfullyValidatedAndProcessedData)
        // ... then respond to the Wallet's POST (200 OK + JSON body)

        // ---------------------------

        val policyResults = validatePolicies(session.policies, allSuccessfullyValidatedAndProcessedData)

        session.updateSession(SessionEvent.policy_results_available) {
            this.policyResults = policyResults
            this.status = if (policyResults.overallSuccess) VerificationSessionStatus.SUCCESSFUL else VerificationSessionStatus.FAILED
        }

        val optionalSuccessRedirectUrl = session.redirects?.successRedirectUri
        val willRedirect = optionalSuccessRedirectUrl != null

        return if (willRedirect) {
            mapOf("redirect_uri" to optionalSuccessRedirectUrl)

            // TODO: error redirect url !!!

        } else {
            mapOf(
                "status" to "received",
                "message" to "Presentation received and is being processed."
            )
        }


    }

}
