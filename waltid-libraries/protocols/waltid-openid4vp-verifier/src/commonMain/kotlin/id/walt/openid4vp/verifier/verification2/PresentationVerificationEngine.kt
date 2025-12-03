package id.walt.openid4vp.verifier.verification2

import id.walt.credentials.formats.DigitalCredential
import id.walt.dcql.models.CredentialQuery
import id.walt.openid4vp.verifier.data.SessionEvent
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.openid4vp.verifier.handlers.vpresponse.ParsedVpToken
import id.walt.openid4vp.verifier.handlers.vpresponse.Verifier2SessionCredentialPolicyValidation
import id.walt.openid4vp.verifier.verification.DcqlFulfillmentChecker
import id.walt.openid4vp.verifier.verification.Verifier2PresentationValidator
import id.walt.policies2.vp.policies.VPPolicy2.PolicyRunError
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

object PresentationVerificationEngine {

    private val log = KotlinLogging.logger {}

    suspend fun verifySinglePresentation(
        presentationString: String,
        originalCredentialQuery: CredentialQuery,
        session: Verification2Session,
        queryId: String
    ): Result<Verifier2PresentationValidator.PresentationValidationResult> {
        val authorizationRequest = session.authorizationRequest
        val responseMode = session.authorizationRequest.responseMode
        val isDcApi = responseMode in OpenID4VPResponseMode.DC_API_RESPONSES
        val expectedOrigin = session.authorizationRequest.expectedOrigins?.first()
        val expectedAudience = if (isDcApi) "origin:$expectedOrigin" else authorizationRequest.clientId
        val isEncrypted = authorizationRequest.responseMode in OpenID4VPResponseMode.ENCRYPTED_RESPONSES
        val jwkThumbprint = session.jwkThumbprint

        val validationOutcome = Verifier2PresentationValidator.validatePresentation(
            presentationString = presentationString,
            expectedFormat = originalCredentialQuery.format,
            expectedAudience = expectedAudience,
            expectedNonce = authorizationRequest.nonce!!,
            responseUri = authorizationRequest.responseUri,
            originalClaimsQuery = originalCredentialQuery.claims,
            isDcApi = isDcApi,
            isEncrypted = isEncrypted,
            verifierOrigin = expectedOrigin, // Raw origin needed for mdoc,
            jwkThumbprint = jwkThumbprint
        )

        if (validationOutcome.isSuccess) {
            val validatedCredential = validationOutcome.getOrThrow().credentials
            log.info { "Successfully validated presentation for queryId '$queryId', credential ID (if available): $validatedCredential" }
        } else {
            log.warn { "Validation failed for a presentation under queryId '$queryId': ${validationOutcome.exceptionOrNull()?.message}" }
            // break // Decide if one failed presentation invalidates the whole vp_token
        }

        return validationOutcome
    }

    data class PresentationValidationResponse(
        val validated: Map<String, List<DigitalCredential>>,
        val errors: Map<String, List<Throwable>>
    )

    suspend fun verifyAllPresentation(
        vpTokenContents: ParsedVpToken,
        session: Verification2Session
    ): PresentationValidationResponse = coroutineScope {
        val authorizationRequest = session.authorizationRequest

        // --- Plan and Launch Tasks ---
        val validationJobs = vpTokenContents.flatMap { (queryId, presentedItemsJsonElements) ->
            val originalCredentialQuery =
                authorizationRequest.dcqlQuery?.credentials?.find { credentialQuery -> credentialQuery.id == queryId }

            if (originalCredentialQuery == null) {
                // This is a protocol error or a mismatch. Decide how to handle...
                // For strictness, one could maybe consider the whole vp_token invalid.
                // Or ignore the entry.
                log.warn { "Received presentation for unknown queryId '$queryId' in vp_token." }
                return@flatMap emptyList()
            }

            if (presentedItemsJsonElements.isEmpty()) {
                // This shouldn't happen if the Wallet adheres to the spec (no key for empty results).
                // If it does, it might mean an optional query had no matches, but the Wallet still included the key.
                log.warn { "Empty presentation list received for queryId '$queryId'." }
                return@flatMap emptyList()
            }

            if (!originalCredentialQuery.multiple && presentedItemsJsonElements.size > 1) {
                // Handle as an error or process only the first one.
                // For now, let's process only the first if multiple=false
                log.warn { "Multiple presentations received for queryId '$queryId' where multiple=false was expected." }
            }

            val itemsToProcess = if (!originalCredentialQuery.multiple) {
                presentedItemsJsonElements.take(1)
            } else {
                presentedItemsJsonElements
            }

            // Map every presentation to an async task
            itemsToProcess.map { presentationJsonElement ->
                async(Dispatchers.Default) {
                    val result = verifySinglePresentation(
                        presentationString = presentationJsonElement,
                        originalCredentialQuery = originalCredentialQuery,
                        queryId = queryId,
                        session = session
                    )
                    // Return context (QueryID) with result
                    queryId to result
                }
            }
        }

        // --- Parallel Execution ---
        val results = validationJobs.awaitAll()

        // --- Aggregate Results ---
        val validated = LinkedHashMap<String, MutableList<DigitalCredential>>()
        val errors = LinkedHashMap<String, MutableList<Throwable>>()

        results.forEach { (queryId, presentationResult) ->
            if (presentationResult.isSuccess) {
                val validatedCredentials = presentationResult.getOrThrow().credentials

                validated.getOrPut(queryId) { mutableListOf() }.addAll(validatedCredentials)
            } else {
                val error = presentationResult.exceptionOrNull()!!

                errors.getOrPut(queryId) { mutableListOf() }
                    .add(error)
            }
        }

        return@coroutineScope PresentationValidationResponse(
            validated = validated, errors = errors
        )
    }

    /**
     1. VP
     2. DCQL
     3. Credentials
     */
    suspend fun executeAllVerification(
        vpTokenContents: ParsedVpToken, session: Verification2Session,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit,
        failSessionCallback: suspend (session: Verification2Session, event: SessionEvent, updateSession: suspend (Verification2Session, SessionEvent, block: Verification2Session.() -> Unit) -> Unit) -> Unit
    ) {
        // syntax sugar:
        suspend fun Verification2Session.updateSession(event: SessionEvent, block: Verification2Session.() -> Unit) =
            updateSessionCallback.invoke(this, event, block)

        suspend fun Verification2Session.failSession(event: SessionEvent) =
            failSessionCallback.invoke(this, event, updateSessionCallback)

        // --- Presentation validation ---

        val presentationValidationResult = verifyAllPresentation(vpTokenContents, session)

        if (!presentationValidationResult.errors.isNotEmpty()) {
            // Handle validation failure
            log.warn { "One or more presentations in vp_token failed validation for session ${session.id}" }
            log.warn { "Validation failures for session ${session.id}: ${presentationValidationResult.errors}" }

            session.failSession(SessionEvent.presentation_validation_failed)

            val errors = presentationValidationResult.errors.mapValues { it.value.map { PolicyRunError(it) } }

            throw IllegalArgumentException( // TODO: custom Exception class
                "One or more presentations in vp_token failed validation. See attached errors: ${Json.encodeToString(errors)}"
            )

            //Verifier2Response.Verifier2Error.PRESENTATION_VALIDATION_FAILED.throwAsError()
        }
        val allSuccessfullyValidatedAndProcessedData = presentationValidationResult.validated

        // --- DCQL validation ---


        // Check if the set of validated presentations satisfies the overall DCQL Query
        // (e.g., credential_sets, all *required* CredentialQuery IDs are present in allSuccessfullyValidatedAndProcessedData)
        val dcqlFulfilled = session.authorizationRequest.dcqlQuery?.let { dcqlQuery ->
            DcqlFulfillmentChecker.checkOverallDcqlFulfillment(
                dcqlQuery = dcqlQuery,
                successfullyValidatedQueryIds = allSuccessfullyValidatedAndProcessedData.keys // set of query IDs for which we have valid presentations
            )
        }
        if (dcqlFulfilled?.isSuccess == false) {
            log.error { "The set of validated presentations does not fulfill all DCQL requirements for session ${session.id}, reported error is: ${dcqlFulfilled.exceptionOrNull()}" }

            session.failSession(SessionEvent.dcql_fulfillment_check_failed)

            throw IllegalArgumentException("The set of validated presentations does not fulfill all DCQL requirements. DCQL errors are: ${dcqlFulfilled.exceptionOrNull()?.message}", dcqlFulfilled.exceptionOrNull()!!)
            //Verifier2Response.Verifier2Error.REQUIRED_CREDENTIALS_NOT_PROVIDED.throwAsError()
        }


        // --- Credential verification ---

        val policyResults = Verifier2SessionCredentialPolicyValidation.validateCredentialPolicies(session.policies, allSuccessfullyValidatedAndProcessedData)

        session.updateSession(SessionEvent.policy_results_available) {
            this.policyResults = policyResults
            this.status =
                if (policyResults.overallSuccess) Verification2Session.VerificationSessionStatus.SUCCESSFUL else Verification2Session.VerificationSessionStatus.FAILED
        }

    }
}
