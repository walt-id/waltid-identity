package id.walt.openid4vp.verifier

import id.walt.credentials.formats.DigitalCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.openid4vp.verifier.Verification2Session.VerificationSessionStatus
import id.walt.openid4vp.verifier.Verifier2Response.Verifier2Error
import id.walt.openid4vp.verifier.verification.DcqlFulfillmentChecker
import id.walt.openid4vp.verifier.verification.Verifier2PresentationValidator
import id.walt.policies2.PolicyResult
import id.walt.policies2.PolicyResults
import io.klogging.logger
import kotlinx.serialization.json.Json

/**
 * Here the receiving of credentials through the Verifiers endpoints
 * (e.g. direct_post endpoint) is handled
 */
object Verifier2ReceivedCredentialHandler {

    private val log = logger("Verifier2ReceivedCredentialHandler")

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
            log.info("Direct POST response received without 'state' parameter.")
            Verifier2Error.MISSING_STATE_PARAMETER.throwAsError()
        }

        // 1. Retrieve session data based on state (or verificationSessionId if it maps to state)
        // This session data contains the original nonce, dcql_query, and client_id, ...
        val session = verificationSession
        if (session == null) {
            log.info("Direct POST response received with invalid or expired state: $receivedState")
            Verifier2Error.INVALID_STATE_PARAMETER.throwAsError()
        }


        require(receivedState == session.authorizationRequest.state) { "State does not match" }

        // 2. Parse vp_token
        val vpTokenContents: Map<String, List<String>> = try {
            Json.decodeFromString(vpTokenString)
        } catch (e: Exception) {
            log.info("Failed to parse vp_token string: $vpTokenString. Error: ${e.message}")
            Verifier2Error.MALFORMED_VP_TOKEN.throwAsError()
        }

        log.debug("Received vp_token for state $receivedState: $vpTokenContents")

        session.updateSession(SessionEvent.attempted_presentation) {
            attempted = true
            status = VerificationSessionStatus.IN_USE
            presentedRawData = Verification2Session.PresentedRawData(vpTokenContents, receivedState)
        }

        // Process

        // queryId: from original dcql_query
        // presented credential/presentation


        // ---------------------------
        var allPresentationsValid = true

        val allSuccessfullyValidatedAndProcessedData =
            // queryId -> [validated credentials]
            mutableMapOf<String, MutableList<DigitalCredential>>() // Or a more structured object


        for ((queryId, presentedItemsJsonElements) in vpTokenContents) {
            val originalCredentialQuery = session.authorizationRequest.dcqlQuery?.credentials?.find { it.id == queryId }
            if (originalCredentialQuery == null) {
                log.warn("Received presentation for unknown queryId '$queryId' in vp_token for session ${session.id}.")
                // This is a protocol error or a mismatch. Decide how to handle.
                // For strictness, maybe consider the whole vp_token invalid.
                // allPresentationsValid = false; break;
                continue // Or ignore this entry
            }

            if (presentedItemsJsonElements.isEmpty()) {
                // This shouldn't happen if the Wallet adheres to the spec (no key for empty results).
                // If it does, it might mean an optional query had no matches, but the Wallet still included the key.
                log.warn("Empty presentation list received for queryId '$queryId'.")
                continue
            }

            // If multiple=false in originalCredentialQuery, presentedItemsJsonElements should have size 1.
            if (!originalCredentialQuery.multiple && presentedItemsJsonElements.size > 1) {
                log.warn("Multiple presentations received for queryId '$queryId' where multiple=false was expected.")
                // Handle as an error or process only the first one.
                // allPresentationsValid = false; break;
                // For now, let's process only the first if multiple=false
            }
            val itemsToProcess =
                if (!originalCredentialQuery.multiple) presentedItemsJsonElements.take(1) else presentedItemsJsonElements

            for (presentationJsonElement in itemsToProcess) {
                val presentationString = presentationJsonElement
                if (presentationString == null && originalCredentialQuery.format != CredentialFormat.LDP_VC) { // LDP_VC can be an object
                    log.warn("Presentation for queryId '$queryId' is not a string, but format is ${originalCredentialQuery.format}.")
                    // allPresentationsValid = false; break // out of inner loop
                    continue
                }

                val validationOutcome = Verifier2PresentationValidator.validatePresentation(
                    presentationString = presentationString,
                    expectedFormat = originalCredentialQuery.format,
                    expectedAudience = session.authorizationRequest.clientId,
                    expectedNonce = session.authorizationRequest.nonce!!,
                    responseUri = session.authorizationRequest.responseUri,
                    originalClaimsQuery = originalCredentialQuery.claims
                )

                // --- Format-Specific Validation ---
//                val validationOutcome: Result<List<DigitalCredential>> = when (originalCredentialQuery.format) {
//                    CredentialFormat.JWT_VC_JSON -> {
//                        Verifier2PresentationValidator.validatePresentation(
//                            presentationString,
//                            expectedFormat = CredentialFormat.JWT_VC_JSON,
//                            expectedAudience = session.authorizationRequest.clientId,
//                            expectedNonce = session.authorizationRequest.nonce!!,
//                            originalClaimsQuery = originalCredentialQuery.claims
//                        )
//                        /*
//                        1. Parse the vpJwtString as a JWS.
//                        2. Verify its signature using the Holder's public key (obtained from vpJwt.payload.iss DID or other mechanism).
//                        3. Extract payload claims: aud, nonce, vp.
//                        4. Verify aud == expectedAudience.
//                        5. Verify nonce == expectedNonce.
//                        6. Extract the verifiableCredential array from vp.verifiableCredential.
//                        7. For each VC string/object in the array:
//                            - Use credentialParser to get DigitalCredential object.
//                            - Perform any necessary validation on the VC itself (e.g., its own signature if it's a JWT VC, issuer trust, expiration).
//                        8. Return the primary DigitalCredential (or list if multiple VCs were in one VP).
//                         */
//                    }
//
//                    CredentialFormat.DC_SD_JWT -> {
//                        TODO("DC_SD_JWT")
//                        /*validateSdJwtVcPresentation(
//                            sdJwtPresentationString = presentationString!!,
//                            expectedAudience = sessionData.verifierClientId,
//                            expectedNonce = sessionData.nonce,
//                            credentialParser = credentialParser,
//                            requestedClaimsPaths = originalCredentialQuery.claims?.map { it.path } // For checking disclosed claims
//                            // need the Holder's public key for the KB-JWT
//                        )*/
//                        /*
//                        1. Split the sdJwtPresentationString by ~ into SD-JWT core, disclosures, and Key-Binding JWT (KB-JWT).
//                        2. Validate KB-JWT:
//                            2.1. Parse as JWS.
//                            2.2. Verify its signature using the Holder's public key (derived from cnf claim in the SD-JWT core, which credentialParser should make available on the parsed DigitalCredential from the core).
//                            2.3. Extract payload claims: aud, nonce, sd_hash.
//                            2.4. Verify aud == expectedAudience.
//                            2.5. Verify nonce == expectedNonce.
//                        3. Reconstruct sd_hash: Based on the received disclosures (the ones between SD-JWT core and KB-JWT), calculate the sd_hash exactly as the Wallet did (e.g., SHA256(Base64URL(disclosure1_encoded + "~" + disclosure2_encoded + ...))).
//                        4. Verify recalculated_sd_hash == kbJwt.payload.sd_hash.
//                        5. Validate SD-JWT Core + Disclosures:
//                            5.1. Use credentialParser (parser needs to be able to take the core and the verified disclosures to reconstruct the full claim set).
//                            5.2. The parser should verify that the digests in the _sd array of the core match the hashes of the provided disclosures.
//                            5.3. Perform any necessary validation on the reconstructed VC (issuer trust, expiration).
//                        6. Return the reconstructed DigitalCredential.
//                         */
//                    }
//
//                    CredentialFormat.LDP_VC -> {
//                        TODO("Not yet implemented: LDP_VC verification")
//                        /*if (presentationJsonElement !is JsonObject) {
//                            log.warn("Presentation for queryId '$queryId' (LDP_VC) is not a JSON object.")
//                            Result.failure(IllegalArgumentException("LDP_VC presentation must be a JSON object"))
//                        } else {
//                            validateLdpVp(
//                                vpJsonObject = presentationJsonElement,
//                                expectedDomain = sessionData.verifierClientId,
//                                expectedChallenge = sessionData.nonce,
//                                credentialParser = credentialParser
//                                // need a way to get Holder's public key for DI proof verification
//                            )
//                        }*/
//                        /*
//                        1. Parse vpJsonObject.
//                        2. Extract the proof object.
//                        3. Verify proof.domain == expectedDomain.
//                        4. Verify proof.challenge == expectedChallenge.
//                        5. Verify the Data Integrity proof.proofValue using the proof.verificationMethod (Holder's public key) and other proof parameters. This requires a JSON-LD canonicalization and signing library.
//                        6. Extract verifiableCredential array.
//                        7. For each VC object:
//                            7.1. Use credentialParser.parse(vcJsonObject) (parser needs to handle JSON-LD objects for LDP VCs).
//                            7.2. Validate the VC (issuer, expiration, its own proof if nested).
//                        8. Return the primary DigitalCredential.
//                         */
//                    }
//
//                    CredentialFormat.MSO_MDOC -> {
//                        TODO("Not yet implemented: MSO_MDOC verification")
//                        /*validateMdocPresentation(
//                            mdocBase64UrlString = presentationString!!,
//                            expectedNonce = sessionData.nonce, // Or elements derived for SessionTranscript
//                            expectedClientId = sessionData.verifierClientId, // Or elements derived for SessionTranscript
//                            credentialParser = credentialParser
//                            // need device public keys or shared secrets for mdoc verification
//                        )*/
//                        /*
//                        1. Base64URL decode mdocBase64UrlString to get DeviceResponse CBOR bytes.
//                        2. Parse DeviceResponse CBOR.
//                        3. Extract documents and deviceAuth.
//                        4. Reconstruct/Verify SessionTranscript: This is key. The Verifier needs to reconstruct what it believes the SessionTranscript should have been, including elements derived from expectedNonce and expectedClientId.
//                        5. Verify deviceAuth.deviceSignature (or MAC) over the reconstructed SessionTranscript using the device's public key (which needs to be known or established).
//                        6. If valid, parse the data elements from documents using credentialParser (it needs to understand the mdoc data structure).
//                        7. Return the DigitalCredential.
//                         */
//                    }
//
//                    else -> {
//                        log.warn("Unsupported format ${originalCredentialQuery.format} for validation.")
//                        Result.failure(UnsupportedOperationException("Validation for format ${originalCredentialQuery.format} not implemented."))
//                    }
//                }

                if (validationOutcome.isSuccess) {
                    val validatedCredential = validationOutcome.getOrThrow().credentials
                    log.info("Successfully validated presentation for queryId '$queryId', credential ID (if available): $validatedCredential")
                    allSuccessfullyValidatedAndProcessedData
                        .getOrPut(queryId) { mutableListOf() }
                        .addAll(validatedCredential)
                } else {
                    log.warn("Validation failed for a presentation under queryId '$queryId': ${validationOutcome.exceptionOrNull()?.message}")
                    allPresentationsValid = false
                    // break // Decide if one failed presentation invalidates the whole vp_token
                }
            } // End loop over presentationJsonElements for a queryId
            if (!allPresentationsValid) break // If one queryId fails, stop processing others
        } // End loop over vpTokenContents

        session.updateSession(SessionEvent.attempted_presentation) {
            presentedCredentials = allSuccessfullyValidatedAndProcessedData
        }

        if (!allPresentationsValid) {
            // Handle overall validation failure
            // Handle individual presentation validation failure
            log.error("One or more presentations in vp_token failed validation for session ${session.id}")

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
            log.error("The set of validated presentations does not fulfill all DCQL requirements for session ${session.id}")

            session.failSession(SessionEvent.dcql_fulfillment_check_failed)

            Verifier2Error.REQUIRED_CREDENTIALS_NOT_PROVIDED.throwAsError()
        }

        // If we reach here, all individual presentations are valid AND the overall DCQL structure is met.
        log.info("All presentations in vp_token validated and DCQL fulfilled for session ${session.id}.")

        // 4. If all good, proceed with business logic using `allSuccessfullyValidatedAndProcessedData`

        // verifierService.processVerifiedPresentation(sessionData.sessionId, allSuccessfullyValidatedAndProcessedData)
        // ... then respond to the Wallet's POST (200 OK + JSON body)

        // ---------------------------


        // VP Policies:
        /*  // TODO: vpPolicies
        val vpPolicyResults = session.policies.vpPolicies.policies.forEach {
            it.verify(vpTokenContents)
        }*/

        val vcPolicyResults = ArrayList<PolicyResult>()
        val specificVcPolicyResults = emptyMap<String, List<PolicyResult>>()

        // VC Policies:
        allSuccessfullyValidatedAndProcessedData.forEach { (queryId, credentials) ->
            credentials.forEach { credential ->

                session.policies.vcPolicies.policies.forEach { policy ->
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
