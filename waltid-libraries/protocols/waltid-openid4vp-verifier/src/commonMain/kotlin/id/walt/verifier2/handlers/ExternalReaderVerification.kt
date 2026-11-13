package id.walt.verifier2.handlers

import id.walt.cose.coseCompliantCbor
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.trustedauthorities.DcqlTrustedAuthoritiesChecker
import id.walt.credentials.presentations.formats.MsoMdocPresentation
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.mdoc.proximity.MdocProximityVerifier
import id.walt.mdoc.verification.MdocVerifier
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.PolicyExecutionContext
import id.walt.policies2.vp.policies.VPPolicyRunner
import id.walt.policies2.vp.policies.VerificationSessionContext
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier2.data.publicView
import id.walt.verifier2.data.ExternalReaderDeviceResponse
import id.walt.verifier2.data.ExternalReaderFlowSetup
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus
import id.walt.verifier2.data.buildRequestedElementsDcqlQuery
import id.walt.verifier2.data.effectiveRetentionDate
import id.walt.verifier2.handlers.vpresponse.Verifier2SessionCredentialPolicyValidation
import id.walt.verifier2.verification.DcqlFulfillmentChecker
import id.walt.verifier2.verification2.PresentationVerificationEngine
import id.walt.verifier2.verification2.Verifier2PolicyResults
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration

/** Produces one terminal outcome. Services own persistence and deliver exactly that one update. */
@OptIn(ExperimentalSerializationApi::class)
object ExternalReaderVerification {
    data class Outcome(val session: Verification2Session, val event: SessionEvent)

    suspend fun verify(
        request: ExternalReaderDeviceResponse,
        submission: MdocProximityVerifier.Submission,
        retention: Duration? = null,
        policyContext: PolicyExecutionContext = PolicyExecutionContext.Empty,
    ): Outcome {
        val setup = ExternalReaderFlowSetup(request.core)
        val query = setup.core.dcqlQuery ?: setup.core.requestedElements?.let(::buildRequestedElementsDcqlQuery)
        val policies = setup.core.policies.copy(
            vc_policies = setup.core.policies.vc_policies ?: VCPolicyList(listOf(CredentialSignaturePolicy())),
        )
        val now = Clock.System.now()
        val session = Verification2Session(
            setup = setup,
            status = VerificationSessionStatus.FAILED,
            creationDate = now,
            attempted = true,
            // This call creates a completed result, not a reusable wallet offer.
            reattemptable = false,
            notifications = setup.core.notifications,
            retentionDate = setup.core.effectiveRetentionDate(retention, now),
            // The legacy session model requires this container even for non-OpenID4VP flows.
            // It carries only the configured query, never a nonce, response URI or wallet request.
            authorizationRequest = AuthorizationRequest(responseType = null, dcqlQuery = query),
            authorizationRequestUrl = null,
            requestMode = Verification2Session.RequestMode.EXTERNAL_READER,
            policies = policies,
            presentedRawData = Verification2Session.PresentedRawData(mapOf("device_response" to listOf(request.deviceResponse)), null),
        )

        val credentials: List<MdocsCredential>
        try {
            // Inner signed byte strings retain their original encoding when Document is serialized.
            credentials = submission.response.documents.orEmpty().map { document ->
                val signed = Base64.UrlSafe.encode(coseCompliantCbor.encodeToByteArray(document))
                val credential = CredentialParser.handleMdocs(signed, base64 = true).second
                credential.copy(credentialData = JsonObject(credential.credentialData + MdocVerifier.mergeMdocDataToJson(document)))
            }
            session.presentedCredentials = credentials.groupBy { it.docType }
            session.presentedPresentationsList = credentials.groupBy { it.docType }.mapValues { (_, docs) -> docs.map(::MsoMdocPresentation) }
            MdocProximityVerifier.verify(submission)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            session.failure = SessionFailure.PresentationValidation(reason = cause.message ?: "Mdoc verification failed", failedPolicies = emptyMap())
            session.statusReason = cause.message
            return Outcome(session, SessionEvent.presentation_validation_failed)
        }

        // Match returned claims, without claiming to know which DeviceRequest the kiosk sent.
        // Explicit DCQL alternatives must match in full before assignment. requestedElements remains
        // a doctype adapter, as in Annex C: holders may withhold requested elements.
        val credentialsByQuery: Map<String, List<DigitalCredential>> = query?.let { dcql ->
            dcql.credentials.associate { credentialQuery ->
                val matching = credentials.filter { credential ->
                    DcqlMatcher.match(
                        DcqlQuery(listOf(if (setup.core.dcqlQuery != null) credentialQuery else credentialQuery.copy(claims = null, claimSets = null))),
                        listOf(RawDcqlCredential(credentialQuery.id, credential.format, credential.credentialData, originalCredential = credential)),
                        trustedAuthoritiesChecker = DcqlTrustedAuthoritiesChecker.checker,
                    ).getOrNull()?.get(credentialQuery.id)?.isNotEmpty() == true
                }
                credentialQuery.id to if (credentialQuery.multiple) matching else matching.take(1)
            }.filterValues { it.isNotEmpty() }
        } ?: credentials.groupBy { it.docType }
        // Preserve all documents, including those not covered by a query, on the public session.
        val reservedIds = query?.credentials.orEmpty().mapTo(mutableSetOf()) { it.id }
            .apply { addAll(policies.specific_vc_policies.orEmpty().keys) }
        session.presentedCredentials = credentialsByQuery.toMutableMap().apply {
            var extraIndex = 0
            credentials.filter { credential -> credentialsByQuery.values.none { credential in it } }.forEach { credential ->
                while ("unrequested_$extraIndex" in reservedIds || "unrequested_$extraIndex" in this) extraIndex++
                put("unrequested_${extraIndex++}", listOf(credential))
            }
        }
        session.presentedPresentationsList = session.presentedCredentials!!.mapValues { (_, docs) ->
            docs.map { MsoMdocPresentation(it as MdocsCredential) }
        }

        session.presentedPresentations = session.presentedPresentationsList?.mapValues { (_, values) -> values.last() }

        val context = VerificationSessionContext(
            vpToken = request.deviceResponse,
            expectedNonce = "",
            expectedAudience = null,
            expectedOrigins = null,
            responseUri = null,
            responseMode = OpenID4VPResponseMode.DIRECT_POST,
            isSigned = false,
            isEncrypted = false,
            jwkThumbprint = null,
            isAnnexC = false,
            mdocSessionTranscript = submission.transcript,
        )
        val vpResults = PresentationVerificationEngine.combinePolicyResultsByQuery(
            session.presentedPresentationsList!!.flatMap { (queryId, presentations) ->
                presentations.map { presentation ->
                    queryId to VPPolicyRunner.verifySpecificPresentation(
                        presentation as MsoMdocPresentation, policies.vp_policies?.msoMdoc.orEmpty(), context,
                    )
                }
            },
        )
        val credentialResults = Verifier2SessionCredentialPolicyValidation.validateCredentialPolicies(
            policies, session.presentedCredentials!!, policyContext,
        )
        val results = Verifier2PolicyResults(
            vpPolicies = vpResults,
            vcPolicies = credentialResults.vcPolicies,
            specificVcPolicies = credentialResults.specificVcPolicies,
        )
        val queryFailure = query?.let {
            DcqlFulfillmentChecker.checkOverallDcqlFulfillment(it, credentialsByQuery.keys).exceptionOrNull()
        }
        session.policyResults = results
        session.presentationValidationResults = vpResults
        session.status = if (results.overallSuccess && queryFailure == null) VerificationSessionStatus.SUCCESSFUL else VerificationSessionStatus.FAILED
        session.statusReason = queryFailure?.message
        if (queryFailure != null) {
            session.failure = SessionFailure.DcqlFulfillment(
                reason = queryFailure.message ?: "Query requirements were not met",
                failure = (queryFailure as DcqlFulfillmentChecker.DcqlFulfillmentException).details,
            )
        } else if (!results.overallSuccess) {
            val violations = results.vcPolicies.filterNot { it.success } + results.specificVcPolicies.values.flatten().filterNot { it.success }
            session.failure = if (violations.isNotEmpty()) {
                SessionFailure.VcPolicyViolations(reason = "Configured credential policies failed", violations = violations)
            } else SessionFailure.PresentationValidation(
                reason = "Configured presentation policies failed",
                failedPolicies = vpResults.mapValues { (_, values) -> values.filterValues { !it.success } },
            )
        }
        return Outcome(session, SessionEvent.credential_policy_results_available)
    }
}
