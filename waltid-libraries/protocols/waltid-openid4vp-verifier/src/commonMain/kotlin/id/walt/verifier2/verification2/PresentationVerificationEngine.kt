package id.walt.verifier2.verification2

import id.walt.credentials.presentations.formats.*
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.policies2.vp.policies.VPPolicy2
import id.walt.policies2.vp.policies.VPPolicyRunner
import id.walt.policies2.vp.policies.VerificationSessionContext
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.transactiondata.filterTransactionDataForCredentialId
import id.walt.verifier2.data.DcApiAnnexCFlowSetup
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.handlers.vpresponse.ParsedVpToken
import id.walt.verifier2.handlers.vpresponse.Verifier2SessionCredentialPolicyValidation
import id.walt.verifier2.verification.DcqlFulfillmentChecker
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
        expectedTransactionData: List<String>?,
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
            expectedTransactionData = expectedTransactionData,
            responseUri = authorizationRequest.responseUri,
            responseMode = responseMode!!,
            isSigned = session.signedAuthorizationRequestJwt != null,
            isEncrypted = isEncrypted,
            jwkThumbprint = jwkThumbprint,
            isAnnexC = session.setup is DcApiAnnexCFlowSetup,
            customData = session.data as? JsonObject
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
            val expectedTransactionData = filterTransactionDataForCredentialId(
                transactionData = session.authorizationRequest.transactionData,
                credentialId = query.id,
            ).takeIf(List<String>::isNotEmpty)
            val policyResults = verifySinglePresentation(
                presentation = presentation,
                presentationString = presentationString,
                session = session,
                expectedTransactionData = expectedTransactionData,
            )

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

        session.updateSession(SessionEvent.presentation_validation_available) {
            presentationValidationResults = presentationValidationResult
        }

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

            val firstError =
                presentationValidationResult.firstNotNullOfOrNull { it.value.firstNotNullOfOrNull { it.value.errors.firstOrNull() } }
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

        session.updateSession(SessionEvent.credential_policy_results_available) {
            this.policyResults = verificationSessionPolicyResults
            this.status = when {
                verificationSessionPolicyResults.overallSuccess -> Verification2Session.VerificationSessionStatus.SUCCESSFUL
                else -> Verification2Session.VerificationSessionStatus.FAILED
            }
        }

    }
}
