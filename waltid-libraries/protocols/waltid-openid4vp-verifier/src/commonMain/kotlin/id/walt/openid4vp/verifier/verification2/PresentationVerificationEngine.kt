package id.walt.openid4vp.verifier.verification2

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.presentations.formats.*
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.openid4vp.verifier.data.SessionEvent
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.openid4vp.verifier.handlers.vpresponse.ParsedVpToken
import id.walt.openid4vp.verifier.handlers.vpresponse.Verifier2SessionCredentialPolicyValidation
import id.walt.openid4vp.verifier.verification.DcqlFulfillmentChecker
import id.walt.openid4vp.verifier.verification.Verifier2PresentationValidator
import id.walt.policies2.vp.policies.VPPolicy2
import id.walt.policies2.vp.policies.VPPolicyRunner
import id.walt.policies2.vp.policies.VerificationSessionContext
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

object PresentationVerificationEngine {

    private val log = KotlinLogging.logger {}

    suspend fun parsePresentation(presentationString: String, format: CredentialFormat): VerifiablePresentation = when (format) {
        CredentialFormat.JWT_VC_JSON -> JwtVcJsonPresentation.parse(presentationString).getOrThrow()
        CredentialFormat.DC_SD_JWT -> DcSdJwtPresentation.parse(presentationString).getOrThrow()
        CredentialFormat.MSO_MDOC -> MsoMdocPresentation.parse(presentationString).getOrThrow()

        CredentialFormat.LDP_VC, CredentialFormat.AC_VP -> throw UnsupportedOperationException("Format $format not supported for validation yet.")
    }

    /**
     * Uses new VP Policy Interface
     */
    suspend fun verifySinglePresentation(
        presentation: VerifiablePresentation,
        presentationString: String,
        session: Verification2Session,
    ): Map<String, VPPolicy2.PolicyRunResult> {
        requireNotNull(session.policies.vp_policies) { "TODO: vpPolicies cannot be null right now" }

        val authorizationRequest = session.authorizationRequest
        val responseMode = session.authorizationRequest.responseMode
        val isDcApi = responseMode in OpenID4VPResponseMode.DC_API_RESPONSES
        val expectedOrigin = session.authorizationRequest.expectedOrigins?.first()
        val expectedAudience = if (isDcApi) "origin:$expectedOrigin" else authorizationRequest.clientId
        val isEncrypted = authorizationRequest.responseMode in OpenID4VPResponseMode.ENCRYPTED_RESPONSES
        val jwkThumbprint = session.jwkThumbprint

        val verificationContext = VerificationSessionContext(
            vpToken = presentationString,
            expectedNonce = session.authorizationRequest.nonce!!,
            expectedAudience = expectedAudience,
            expectedOrigins = session.authorizationRequest.expectedOrigins,
            responseUri = authorizationRequest.responseUri,
            responseMode = responseMode!!,
            isSigned = session.signedAuthorizationRequestJwt != null,
            isEncrypted = isEncrypted,
            jwkThumbprint = jwkThumbprint
        )

        return VPPolicyRunner.verifyPresentation(
            presentation = presentation,
            policies = session.policies.vp_policies,
            verificationContext = verificationContext
        )
    }


    suspend fun verifyAllPresentations(
        parsedPresentations: Map<Pair<String, CredentialQuery>, VerifiablePresentation>,
        session: Verification2Session
    ): Map<String, Map<String, VPPolicy2.PolicyRunResult>> {
        val verifiedPresentations = parsedPresentations.map { (entry, presentation) ->
            val (presentationString, query) = entry
            val policyResults = verifySinglePresentation(presentation, presentationString, session)

            query.id to policyResults
        }.toMap()

        return verifiedPresentations
    }

    suspend fun parseAllPresentations(
        vpTokenContents: ParsedVpToken,
        session: Verification2Session
    ): Map<Pair<String, CredentialQuery>, VerifiablePresentation> {
        val authorizationRequest = session.authorizationRequest


        val presentationsToUse = ArrayList<Pair<String, CredentialQuery>>()

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
                presentationsToUse.add(presentationJsonElement to originalCredentialQuery)
            }
        }

        val parsedPresentations = presentationsToUse.associateWith { (presentationString, originalCredentialQuery) ->
            val presentation = parsePresentation(presentationString, originalCredentialQuery.format)
            presentation
        }

        return parsedPresentations


    }

    /**
     * Uses old Verifier2PresentationValidator interface
     */
    suspend fun verifySinglePresentationOldPresentationValidator(
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

    suspend fun verifyAllPresentationOld(
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
                    val result = verifySinglePresentationOldPresentationValidator(
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


        val parsedPresentations = parseAllPresentations(vpTokenContents, session)

        session.updateSession(SessionEvent.parsed_presentation_available) {
            presentedPresentations = parsedPresentations.map { it.key.second.id to it.value }.toMap()
        }

        val presentationValidationResult = verifyAllPresentations(parsedPresentations, session)

        val anyError = presentationValidationResult.any { it.value.any { it.value.errors.isNotEmpty() } }

        if (anyError) {
            // Handle validation failure
            log.warn { "One or more presentations in vp_token failed validation for session ${session.id}" }
            log.info { "Validation results for session ${session.id}:" }
            presentationValidationResult.forEach { (query, policyResults) ->
                log.info { "$query -> " }
                policyResults.forEach { (s, result) ->
                    log.info { "  --- $s: $result" }
                }
            }

            val firstError = presentationValidationResult.firstNotNullOfOrNull { it.value.firstNotNullOfOrNull { it.value.errors.firstOrNull() } }
            log.warn { "First error: $firstError" }

            session.failSession(SessionEvent.presentation_validation_failed)

            throw IllegalArgumentException( // TODO: custom Exception class
                "One or more presentations in vp_token failed validation. See presentation validation results: ${
                    Json.encodeToString(
                        presentationValidationResult
                    )
                }"
            )

            //Verifier2Response.Verifier2Error.PRESENTATION_VALIDATION_FAILED.throwAsError()
        }


        val allSuccessfullyValidatedAndProcessedData = parsedPresentations.map {
            it.key.second.id to when (val presentation = it.value) {
                is JwtVcJsonPresentation -> presentation.credentials ?: emptyList()
                is DcSdJwtPresentation -> listOf(presentation.credential)
                is MsoMdocPresentation -> listOf(presentation.mdoc)
                is LdpVcPresentation -> throw NotImplementedError()
            }
        }.toMap()

        session.updateSession(SessionEvent.validated_credentials_available) {
            presentedCredentials = allSuccessfullyValidatedAndProcessedData
        }

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

            throw IllegalArgumentException(
                "The set of validated presentations does not fulfill all DCQL requirements. DCQL errors are: ${dcqlFulfilled.exceptionOrNull()?.message}",
                dcqlFulfilled.exceptionOrNull()!!
            )
            //Verifier2Response.Verifier2Error.REQUIRED_CREDENTIALS_NOT_PROVIDED.throwAsError()
        }

        session.updateSession(SessionEvent.presentation_fulfils_dcql_query) {

        }

        // --- Credential verification ---

        val credentialPolicyResults = Verifier2SessionCredentialPolicyValidation.validateCredentialPolicies(
            session.policies,
            allSuccessfullyValidatedAndProcessedData
        )

        val verificationSessionPolicyResults = Verifier2PolicyResults(
            vpPolicies = presentationValidationResult,
            vcPolicies = credentialPolicyResults.vcPolicies,
            specificVcPolicies = credentialPolicyResults.specificVcPolicies,
        )

        session.updateSession(SessionEvent.policy_results_available) {
            this.policyResults = verificationSessionPolicyResults
            this.status = when {
                verificationSessionPolicyResults.overallSuccess -> Verification2Session.VerificationSessionStatus.SUCCESSFUL
                else -> Verification2Session.VerificationSessionStatus.FAILED
            }
        }

    }
}
