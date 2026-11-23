package id.walt.itb

import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.wallet2.handlers.CredentialEndpointException
import id.waltid.openid4vci.wallet.token.TokenRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Instant

// Issuer response text is untrusted; reports retain only known protocol error identifiers.
private val credentialEndpointErrorCodes = setOf(
    CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST,
    CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION,
    CredentialErrorCodes.UNKNOWN_CREDENTIAL_IDENTIFIER,
    CredentialErrorCodes.INVALID_PROOF,
    CredentialErrorCodes.INVALID_NONCE,
    CredentialErrorCodes.INVALID_ENCRYPTION_PARAMETERS,
    CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED,
)

/** An owned session is recorded before its first execution attempt, so failed starts can be cleaned up. */
data class ItbSession(val testSuite: String, val testCase: String, val session: String) {
    init {
        require(testSuite.isNotBlank() && testCase.isNotBlank()) { "Missing ITB case identity" }
        require(Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(session)) { "Invalid ITB session ID" }
    }
}

interface ItbInteractionBridge {
    /** Prepare one interactive session without starting its test steps. Never retry an uncertain creation. */
    suspend fun prepare(suite: ItbCatalogue.Suite, case: ItbCatalogue.Case): ItbSession
    /** Start the prepared session and read its pending wallet interaction. */
    suspend fun read(session: ItbSession): ItbWalletInteraction
    /** Read a later interaction in the same session, without starting it again. */
    suspend fun readNext(session: ItbSession): ItbWalletInteraction
    suspend fun complete()
    /** Acknowledge the case-specific prompt only after the wallet has verified the expected status. */
    suspend fun confirmRevocation(session: ItbSession)
}

@Serializable
data class ItbCaseResult(
    val suite: String,
    val case: String,
    val session: String?,
    val outcome: Outcome,
    val phase: Phase,
    val adapterInvoked: Boolean,
    val walletSucceeded: Boolean,
    val testBedVerdict: ItbSessionReport.Verdict?,
    val testBedCompleted: Boolean,
    val version: String?,
    val startedAt: String,
    val endedAt: String,
    val errorType: String? = null,
    val cleanupFailed: Boolean = false,
    val errorCode: String? = null,
    val statusObservations: List<ItbStatusObservation> = emptyList(),
    @Transient val issuedCredentialIds: List<String> = emptyList(),
) {
    enum class Outcome { PASSED, WALLET_FAILED, AUTH_UNAVAILABLE, ITB_FAILED, ERROR, TIMED_OUT, INCOMPLETE, PREREQUISITE_FAILED, NOT_RUN }
    enum class Phase { START, INTERACTION, WALLET, VERDICT }
}

/** Runs one case at a time so short-lived issuer offers are never queued behind other wallet work. */
class ItbCaseRunner(
    private val api: ItbRestClient,
    private val bridge: ItbInteractionBridge,
    private val executeWallet: suspend (ItbWalletInteraction) -> ItbWalletResult,
    private val caseTimeoutMillis: Long = 120_000,
    private val pollMillis: Long = 1_000,
) {
    init {
        require(caseTimeoutMillis > 0 && pollMillis > 0) { "ITB timeouts must be positive" }
    }

    suspend fun run(
        suite: ItbCatalogue.Suite,
        case: ItbCatalogue.Case,
        prerequisiteCredentialId: String? = null,
    ): ItbCaseResult {
        val start = Instant.now().toString()
        if (case.prerequisite != null && prerequisiteCredentialId == null) {
            return ItbCaseResult(
                suite.id, case.id, null, ItbCaseResult.Outcome.PREREQUISITE_FAILED, ItbCaseResult.Phase.START,
                false, false, null, false, null, start, start, errorCode = case.prerequisite,
            )
        }
        var session: ItbSession? = null
        var report: ItbSessionReport? = null
        var phase = ItbCaseResult.Phase.START
        var adapterInvoked = false
        var walletSucceeded = false
        var failure: String? = null
        var cleanupFailed = false
        var errorCode: String? = null
        var observation = ItbWalletResult()
        var outcome = ItbCaseResult.Outcome.ERROR
        try {
            withTimeout(caseTimeoutMillis) {
                val createdSession = bridge.prepare(suite, case)
                session = createdSession
                check(createdSession.testSuite == suite.id && createdSession.testCase == case.id) {
                    "ITB prepared an unexpected case"
                }
                phase = ItbCaseResult.Phase.INTERACTION
                val interaction = bridge.read(createdSession)
                case.invocationScheme?.let { expected ->
                    require((interaction as? ItbWalletInteraction.Presentation)?.url?.scheme.equals(expected, ignoreCase = true)) {
                        "The ITB interaction does not use the case's invocation scheme"
                    }
                }
                suspend fun execute(input: ItbWalletInteraction): ItbWalletResult {
                    phase = ItbCaseResult.Phase.WALLET
                    adapterInvoked = true
                    return executeWallet(input)
                }
                val caseKey = "${suite.id}/${case.id}"
                val expectedStatus = when (caseKey) {
                    "cs10-test-suite/rv_iss_01" -> ItbExpectedStatus.CLEAR
                    "cs10-test-suite/rv_iss_02" -> ItbExpectedStatus.INVALID
                    else -> null
                }
                if (expectedStatus != null) interaction.requireOffer()
                val input = when (interaction) {
                    is ItbWalletInteraction.Presentation -> interaction.copy(
                        credentialId = prerequisiteCredentialId,
                        attended = case.attended,
                    )
                    is ItbWalletInteraction.DigitalCredentials -> interaction.copy(credentialId = prerequisiteCredentialId)
                    else -> interaction
                }
                observation = execute(input)
                if (expectedStatus != null) {
                    val firstId = observation.singleIssuedCredential()
                    val ids = if (expectedStatus == ItbExpectedStatus.CLEAR) {
                        val firstOffer = interaction.requireOffer()
                        phase = ItbCaseResult.Phase.INTERACTION
                        bridge.complete()
                        val secondOffer = bridge.readNext(createdSession).requireOffer()
                        check(firstOffer.url != secondOffer.url) { "ITB repeated the first revocation offer" }
                        val secondId = execute(secondOffer).singleIssuedCredential()
                        if (firstId == secondId) throw ItbWalletRejection("status_expected_distinct_credentials")
                        listOf(firstId, secondId)
                    } else listOf(firstId)
                    phase = ItbCaseResult.Phase.INTERACTION
                    bridge.complete()
                    observation = execute(ItbWalletInteraction.CheckStatus(ids, expectedStatus))
                    if (observation.statusObservations.size != ids.size ||
                        observation.statusObservations.any { it.refreshed != expectedStatus.label }
                    ) throw ItbWalletRejection("status_missing_expected_app_observation")
                    walletSucceeded = true
                } else {
                    walletSucceeded = true
                    phase = ItbCaseResult.Phase.INTERACTION
                    bridge.complete()
                }
                phase = ItbCaseResult.Phase.VERDICT
                if (expectedStatus != null) bridge.confirmRevocation(createdSession)
                while (true) {
                    val status = readVerdict { api.status(listOf(createdSession.session)).single() }
                    if (status.isComplete) break
                    delay(pollMillis)
                }
                val finalReport = readVerdict { api.report(case.id, createdSession.session) }
                report = finalReport
                outcome = when {
                    finalReport.passed -> ItbCaseResult.Outcome.PASSED
                    finalReport.isComplete && finalReport.verdict == ItbSessionReport.Verdict.FAILURE -> ItbCaseResult.Outcome.ITB_FAILED
                    else -> ItbCaseResult.Outcome.INCOMPLETE
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            failure = "TimeoutCancellationException"
            outcome = ItbCaseResult.Outcome.TIMED_OUT
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Messages and stack traces may contain offers, protocol payloads or browser input values.
            failure = error::class.simpleName ?: "Exception"
            errorCode = when (error) {
                is ItbWalletRejection -> error.code
                is ItbVerifierRejection -> error.code
                is ItbPortalStepTimeout -> error.step.name.lowercase()
                is ItbPortalInputError -> error.reason.name.lowercase()
                is ItbRestClient.HttpFailure -> "itb_${error.operation}_${error.statusCode}"
                is ItbRestClient.TransportFailure -> "itb_${error.operation}_transport"
                is ItbRestClient.InvalidStatusResponse -> "itb_status_invalid_response"
                is CredentialEndpointException -> "credential_endpoint_http_${error.statusCode}" +
                    (error.credentialError?.error?.takeIf(credentialEndpointErrorCodes::contains)?.let { "_$it" } ?: "")
                is TokenRequestException -> ItbTokenFailure.reportCode(error)
                else -> null
            }
            outcome = when {
                error is ItbPortalStepTimeout -> ItbCaseResult.Outcome.TIMED_OUT
                error is ItbAuthenticationUnavailable && adapterInvoked -> ItbCaseResult.Outcome.AUTH_UNAVAILABLE
                    phase == ItbCaseResult.Phase.WALLET && !walletSucceeded -> ItbCaseResult.Outcome.WALLET_FAILED
                else -> ItbCaseResult.Outcome.ERROR
            }
        } finally {
            withContext(NonCancellable) {
                // Only the session created by this invocation is eligible for cleanup.
                session?.takeIf { report?.isComplete != true }?.let { owned ->
                    try {
                        if (!api.status(listOf(owned.session)).single().isComplete) api.stop(listOf(owned.session))
                        report = api.report(case.id, owned.session)
                    } catch (_: Exception) {
                        cleanupFailed = true
                    }
                }
            }
        }
        return ItbCaseResult(
            suite.id, case.id, session?.session, outcome, phase, adapterInvoked, walletSucceeded,
            report?.verdict, report?.isComplete == true, report?.caseVersion,
            start, Instant.now().toString(), failure, cleanupFailed, errorCode,
            observation.statusObservations, observation.issuedCredentialIds,
        )
    }

    private suspend fun <T> readVerdict(read: suspend () -> T): T {
        // Status and report reads are safe to repeat within the case's existing timeout.
        repeat(2) {
            try {
                return read()
            } catch (failure: IllegalStateException) {
                val retryable = when (failure) {
                    is ItbRestClient.TransportFailure, is ItbRestClient.InvalidStatusResponse -> true
                    is ItbRestClient.HttpFailure -> failure.statusCode in 500..599 || failure.statusCode in setOf(408, 429)
                    else -> false
                }
                if (!retryable) throw failure
                delay(pollMillis)
            }
        }
        return read()
    }

    private fun ItbWalletResult.singleIssuedCredential(): String =
        issuedCredentialIds.singleOrNull() ?: throw ItbWalletRejection("status_expected_one_credential_per_offer")

    private fun ItbWalletInteraction.requireOffer(): ItbWalletInteraction.Offer =
        checkNotNull(this as? ItbWalletInteraction.Offer) { "ITB did not supply a revocation offer" }
}
