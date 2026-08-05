@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.verification2

import id.walt.cose.protectedAlgorithm
import id.walt.cose.Cose
import id.walt.cose.acceptsCoseAlgorithm
import id.walt.credentials.presentations.formats.*
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.dcql.DcqlCredential
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.TrustedAuthoritiesQuery
import id.walt.policies2.vc.policies.PolicyExecutionContext
import id.walt.policies2.vp.policies.VPPolicy2
import id.walt.policies2.vp.policies.VPPolicyRunner
import id.walt.policies2.vp.policies.VerificationSessionContext
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.transactiondata.filterTransactionDataForCredentialId
import id.walt.verifier2.data.DcApiAnnexCFlowSetup
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.handlers.vpresponse.ParsedVpToken
import id.walt.verifier2.handlers.vpresponse.Verifier2SessionCredentialPolicyValidation
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.PresentationRejectionException
import id.walt.verifier2.verification.DcqlFulfillmentChecker
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

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
        verificationTime: Instant,
    ): Map<String, VPPolicy2.PolicyRunResult> {
        val vpPolicies = requireNotNull(session.policies.vp_policies) { "TODO: vpPolicies cannot be null right now" }
        verifyAdvertisedAlgorithms(presentation, session)

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
            verificationTime = verificationTime,
            customData = session.data as? JsonObject,
            transactionData = authorizationRequest.transactionData
        )

        return VPPolicyRunner.verifyPresentation(
            presentation = presentation,
            policies = vpPolicies,
            verificationContext = verificationContext
        )
    }

    internal suspend fun verifyAdvertisedAlgorithms(
        presentation: VerifiablePresentation,
        session: Verification2Session,
    ) {
        when (presentation) {
            is JwtVcJsonPresentation -> {
                val allowed = advertisedAlgorithms(session, CredentialFormat.JWT_VC_JSON, "alg_values", strings = true)
                requireJwsAlgorithm(presentation.jwt, allowed, "jwt_vc_json presentation")
                presentation.credentials.orEmpty().forEach { credential ->
                    requireJwsAlgorithm(
                        requireNotNull(credential.signed) { "jwt_vc_json credential is unsigned" }.substringBefore('~'),
                        allowed,
                        "jwt_vc_json credential",
                    )
                }
            }
            is DcSdJwtPresentation -> {
                requireJwsAlgorithm(
                    presentation.sdJwt,
                    advertisedAlgorithms(session, CredentialFormat.DC_SD_JWT, "sd-jwt_alg_values", strings = true),
                    "dc+sd-jwt credential",
                )
                requireJwsAlgorithm(
                    presentation.keyBindingJwt,
                    advertisedAlgorithms(session, CredentialFormat.DC_SD_JWT, "kb-jwt_alg_values", strings = true),
                    "dc+sd-jwt key binding",
                )
            }
            is MsoMdocPresentation -> {
                val document = presentation.mdoc.document
                requireCoseAlgorithm(
                    document.issuerSigned.issuerAuth.protectedAlgorithm(),
                    advertisedAlgorithms(session, CredentialFormat.MSO_MDOC, "issuerauth_alg_values", strings = false),
                    document.issuerSigned.getParsedIssuerAuthCrypto2().signerKey.spec == KeySpec.Ec(EcCurve.P256),
                    "mso_mdoc issuer authentication",
                )
                val deviceSignature = requireNotNull(document.deviceSigned?.deviceAuth?.deviceSignature) {
                    "mso_mdoc device signature is missing"
                }
                requireCoseAlgorithm(
                    deviceSignature.protectedAlgorithm(),
                    advertisedAlgorithms(session, CredentialFormat.MSO_MDOC, "deviceauth_alg_values", strings = false),
                    presentation.mdoc.documentMso.deviceKeyInfo.deviceKey.crv == Cose.EllipticCurves.P_256,
                    "mso_mdoc device authentication",
                )
            }
            is LdpVcPresentation -> throw UnsupportedOperationException("LDP presentations are not supported")
        }
    }

    private fun advertisedAlgorithms(
        session: Verification2Session,
        format: CredentialFormat,
        field: String,
        strings: Boolean,
    ): Set<String>? {
        val formats = requireNotNull(session.authorizationRequest.clientMetadata?.vpFormatsSupported) {
            "Verifier metadata is missing vp_formats_supported"
        }
        val formatMetadata = formats.entries.firstOrNull { it.key in format.id }?.value
            ?: throw IllegalArgumentException("Verifier metadata is missing ${format.id.first()} support")
        val value = formatMetadata[field] ?: return null
        val values = value as? JsonArray
            ?: throw IllegalArgumentException("Verifier metadata $field must be an array")
        require(values.isNotEmpty()) { "Verifier metadata $field must not be empty" }
        return values.mapTo(mutableSetOf()) { value ->
            val primitive = value as? JsonPrimitive
                ?: throw IllegalArgumentException("Verifier metadata $field values must be primitives")
            require(primitive.isString == strings) {
                "Verifier metadata $field contains a value with the wrong JSON type"
            }
            primitive.content
        }
    }

    private fun requireJwsAlgorithm(jwt: String, allowed: Set<String>?, label: String) {
        if (allowed == null) return
        val algorithm = CompactJws.decodeUnverified(jwt).algorithm.identifier
        require(algorithm in allowed) { "$label algorithm is not allowed: $algorithm" }
    }

    private fun requireCoseAlgorithm(
        algorithm: Int,
        allowed: Set<String>?,
        p256Key: Boolean,
        label: String,
    ) {
        if (allowed == null) return
        val accepted = allowed.mapTo(mutableSetOf(), String::toInt).acceptsCoseAlgorithm(algorithm, p256Key)
        require(accepted) { "$label algorithm is not allowed: $algorithm" }
    }


    suspend fun verifyAllPresentations(
        parsedPresentations: Map<Pair<String, CredentialQuery>, VerifiablePresentation>,
        session: Verification2Session,
        verificationTime: Instant,
    ): Map<String, Map<String, VPPolicy2.PolicyRunResult>> {
        val verifiedPresentations = parsedPresentations.map { (entry, presentation) ->
            val (presentationString, query) = entry
            val expectedTransactionData = filterTransactionDataForCredentialId(
                transactionData = session.authorizationRequest.transactionData,
                credentialId = query.id,
            )
            val policyResults = verifySinglePresentation(
                presentation = presentation,
                presentationString = presentationString,
                session = session,
                expectedTransactionData = expectedTransactionData,
                verificationTime = verificationTime,
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
        failSessionCallback: suspend (session: Verification2Session, event: SessionEvent, updateSession: suspend (Verification2Session, SessionEvent, block: Verification2Session.() -> Unit) -> Unit) -> Unit,
        policyContext: PolicyExecutionContext = PolicyExecutionContext.Empty,
        verificationTime: Instant = Clock.System.now(),
        /**
         * Optional callback for checking `trusted_authorities` constraints declared in a DCQL
         * `CredentialQuery`. Receives the credential (wrapped as [DcqlCredential]) and the list of
         * authority constraints; returns true if the credential satisfies at least one entry.
         *
         * When null (default), `trusted_authorities` constraints are not enforced.
         * Pass [id.walt.credentials.trustedauthorities.DcqlTrustedAuthoritiesChecker.checker]
         * from JVM callers to enable AKI-based authority verification.
         */
        trustedAuthoritiesChecker: ((DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean)? = null,
    ) {
        // syntax sugar:
        suspend fun Verification2Session.updateSession(event: SessionEvent, block: Verification2Session.() -> Unit) =
            updateSessionCallback.invoke(this, event, block)

        suspend fun Verification2Session.failSession(event: SessionEvent) =
            failSessionCallback.invoke(this, event, updateSessionCallback)

        // ── Top-level exception guard ─────────────────────────────────────────────
        //
        // Without this guard, any unexpected exception thrown inside this function
        // (e.g. a policy implementation that throws instead of returning Result.failure,
        // a network error during status list fetching, a deserialisation error in a
        // presentation parser) would propagate back to the HTTP handler uncaught.
        //
        // The HTTP handler only catches [PresentationRejectionException]; everything
        // else leaves the session permanently stuck in PROCESSING_FLOW — it can never
        // transition to SUCCESSFUL or FAILED, so callers polling the session (SSE,
        // heartbeat, frontend) will hang indefinitely.
        //
        // This try/catch ensures that *any* exception that is not already a
        // [PresentationRejectionException] (which carries its own session state update)
        // is:
        //   1. Logged with context
        //   2. Stored on the session as a [SessionFailure.PresentationValidation] with
        //      the exception message so the error is visible via the session's failure field
        //   3. Marked FAILED via failSessionCallback
        //   4. Rethrown as a [PresentationRejectionException] so the HTTP handler can
        //      return 400 to the wallet instead of 500 (the wallet misbehaving or sending
        //      an unprocessable credential should not be a 500)
        try {


            val parsedPresentations = parseAllPresentations(vpTokenContents, session)

            session.updateSession(SessionEvent.parsed_presentation_available) {
                presentedPresentations = parsedPresentations.map { it.key.second.id to it.value }.toMap()
            }

            val presentationValidationResult = verifyAllPresentations(parsedPresentations, session, verificationTime)

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

                val failedPoliciesMap = presentationValidationResult
                    .mapValues { (_, byPolicy) -> byPolicy.filterValues { it.errors.isNotEmpty() } }
                    .filterValues { it.isNotEmpty() }

                session.updateSession(SessionEvent.presentation_validation_available) {
                    failure = SessionFailure.PresentationValidation(
                        reason = firstError?.message?.let { "Presentation validation failed: $it" }
                            ?: "One or more presentations in vp_token failed validation",
                        failedPolicies = failedPoliciesMap,
                    )
                }

                session.failSession(SessionEvent.presentation_validation_failed)

                val failedPoliciesNames = presentationValidationResult.flatMap { (queryId, policyResults) ->
                    policyResults.filter { it.value.errors.isNotEmpty() }
                        .map { (policyId, _) -> "$queryId/$policyId" }
                }
                throw PresentationRejectionException(
                    "Presentation validation failed. Failed VP policies: ${failedPoliciesNames.joinToString()}"
                )
            }


            val allSuccessfullyValidatedAndProcessedData = parsedPresentations.map {
                it.key.second.id to when (val presentation = it.value) {
                    is JwtVcJsonPresentation -> {
                        if (presentation.vp == null) {
                            throw PresentationRejectionException(
                                "Presentation for query '${it.key.second.id}' is missing the required 'vp' claim."
                            )
                        }
                        presentation.credentials ?: emptyList()
                    }

                    is DcSdJwtPresentation -> listOf(presentation.credential)
                    is MsoMdocPresentation -> listOf(presentation.mdoc)
                    is LdpVcPresentation -> throw NotImplementedError()
                }
            }.toMap()

            session.updateSession(SessionEvent.validated_credentials_available) {
                presentedCredentials = allSuccessfullyValidatedAndProcessedData
            }

            // --- trusted_authorities check ---
            // Per OID4VP §6.1.1: if a CredentialQuery specifies trusted_authorities, every credential
            // presented for that query MUST satisfy at least one authority entry.
            if (trustedAuthoritiesChecker != null) {
                val dcqlQuery = session.authorizationRequest.dcqlQuery
                if (dcqlQuery != null) {
                    for ((queryId, credentials) in allSuccessfullyValidatedAndProcessedData) {
                        val credentialQuery = dcqlQuery.credentials.find { it.id == queryId }
                        val authorities = credentialQuery?.trustedAuthorities
                        if (!authorities.isNullOrEmpty()) {
                            for (credential in credentials) {
                                val dcqlCredential = RawDcqlCredential(
                                    id = queryId,
                                    format = credentialQuery.format.name,
                                    data = credential.credentialData,
                                    originalCredential = credential
                                )
                                if (!trustedAuthoritiesChecker(dcqlCredential, authorities)) {
                                    val msg = "Credential for query '$queryId' does not satisfy trusted_authorities constraint"
                                    log.warn { msg }
                                    session.updateSession(SessionEvent.presentation_validation_available) {
                                        failure = SessionFailure.PresentationValidation(
                                            reason = msg,
                                            failedPolicies = emptyMap()
                                        )
                                    }
                                    session.failSession(SessionEvent.presentation_validation_failed)
                                    throw PresentationRejectionException(msg)
                                }
                            }
                        }
                    }
                }
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
            val dcqlFailure = dcqlFulfilled?.exceptionOrNull() as? DcqlFulfillmentChecker.DcqlFulfillmentException
            if (dcqlFailure != null) {
                log.error { "The set of validated presentations does not fulfill all DCQL requirements for session ${session.id}, reported error is: ${dcqlFailure.message}" }

                session.updateSession(SessionEvent.validated_credentials_available) {
                    failure = SessionFailure.DcqlFulfillment(
                        reason = dcqlFailure.message,
                        failure = dcqlFailure.details,
                    )
                }

                session.failSession(SessionEvent.dcql_fulfillment_check_failed)

                throw PresentationRejectionException(
                    "The set of validated presentations does not fulfill all DCQL requirements. DCQL errors are: ${dcqlFulfilled.exceptionOrNull()?.message}",
                    dcqlFulfilled.exceptionOrNull()!!
                )
            }

            session.updateSession(SessionEvent.presentation_fulfils_dcql_query) {

            }

            // --- Credential verification ---

            val credentialPolicyResults = Verifier2SessionCredentialPolicyValidation.validateCredentialPolicies(
                session.policies,
                allSuccessfullyValidatedAndProcessedData,
                policyContext
            )

            val verificationSessionPolicyResults = Verifier2PolicyResults(
                vpPolicies = presentationValidationResult,
                vcPolicies = credentialPolicyResults.vcPolicies,
                specificVcPolicies = credentialPolicyResults.specificVcPolicies,
            )

            val vcPolicyViolations =
                credentialPolicyResults.vcPolicies.filter { !it.success } +
                        credentialPolicyResults.specificVcPolicies.values.flatten().filter { !it.success }

            session.updateSession(SessionEvent.credential_policy_results_available) {
                this.policyResults = verificationSessionPolicyResults
                this.status = when {
                    verificationSessionPolicyResults.overallSuccess -> Verification2Session.VerificationSessionStatus.SUCCESSFUL
                    else -> Verification2Session.VerificationSessionStatus.FAILED
                }
                if (!verificationSessionPolicyResults.overallSuccess) {
                    // Invariant: overallSuccess=false implies at least one credential policy failure
                    // in the same lists used to compute the overall result.
                    failure = SessionFailure.VcPolicyViolations(
                        reason = "${vcPolicyViolations.size} credential policy violation(s)",
                        violations = vcPolicyViolations,
                    )
                }
            }

            if (!verificationSessionPolicyResults.overallSuccess) {
                val failedVcPolicies = credentialPolicyResults.vcPolicies
                    .filter { !it.success }
                    .map { it.policy.id }
                val failedSpecificVcPolicies = credentialPolicyResults.specificVcPolicies
                    .flatMap { (queryId, results) -> results.filter { !it.success }.map { "$queryId/${it.policy.id}" } }
                val allFailed = (failedVcPolicies + failedSpecificVcPolicies).distinct()
                throw PresentationRejectionException(
                    "Credential policy verification failed. Failed VC policies: ${allFailed.joinToString()}"
                )
            }

        } catch (e: PresentationRejectionException) {
            // Already handled: session state was updated before this was thrown.
            // Re-throw so the HTTP handler can return 400 to the wallet.
            throw e
        } catch (e: Exception) {
            // Unexpected exception — policy threw instead of returning Result.failure,
            // network error during status check, parse error, etc.
            // Mark the session FAILED so it doesn't stay stuck in PROCESSING_FLOW forever.
            log.error(e) {
                "Unexpected exception during verification of session ${session.id} — " +
                        "marking session FAILED to prevent it from being stuck in PROCESSING_FLOW. " +
                        "Exception: ${e::class.simpleName}: ${e.message}"
            }
            session.updateSession(SessionEvent.presentation_validation_available) {
                failure = SessionFailure.PresentationValidation(
                    reason = "Internal verification error: ${e::class.simpleName}: ${e.message}",
                    failedPolicies = emptyMap(),
                )
            }
            session.failSession(SessionEvent.presentation_validation_failed)
            throw PresentationRejectionException(
                "Verification failed due to an internal error: ${e.message}",
                cause = e
            )
        }

    }
}
