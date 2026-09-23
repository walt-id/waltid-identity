package id.walt.itb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.time.Instant

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
    suspend fun complete()
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
) {
    enum class Outcome { PASSED, WALLET_FAILED, AUTH_UNAVAILABLE, ITB_FAILED, ERROR, TIMED_OUT, INCOMPLETE, NOT_RUN }
    enum class Phase { START, INTERACTION, WALLET, VERDICT }
}

/** Runs one case at a time so short-lived issuer offers are never queued behind other wallet work. */
class ItbCaseRunner(
    private val api: ItbRestClient,
    private val bridge: ItbInteractionBridge,
    private val executeWallet: suspend (ItbWalletInteraction) -> Unit,
    private val caseTimeoutMillis: Long = 120_000,
    private val pollMillis: Long = 1_000,
) {
    init {
        require(caseTimeoutMillis > 0 && pollMillis > 0) { "ITB timeouts must be positive" }
    }

    suspend fun run(suite: ItbCatalogue.Suite, case: ItbCatalogue.Case): ItbCaseResult {
        val start = Instant.now().toString()
        var session: ItbSession? = null
        var report: ItbSessionReport? = null
        var phase = ItbCaseResult.Phase.START
        var adapterInvoked = false
        var walletSucceeded = false
        var failure: String? = null
        var cleanupFailed = false
        var errorCode: String? = null
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
                phase = ItbCaseResult.Phase.WALLET
                adapterInvoked = true
                executeWallet(interaction)
                walletSucceeded = true
                bridge.complete()
                phase = ItbCaseResult.Phase.VERDICT
                while (true) {
                    val status = api.status(listOf(createdSession.session)).single()
                    if (status.isComplete) break
                    delay(pollMillis)
                }
                val finalReport = api.report(case.id, createdSession.session)
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
            errorCode = (error as? ItbWalletRejection)?.code
            outcome = when {
                error is ItbAuthenticationUnavailable && adapterInvoked -> ItbCaseResult.Outcome.AUTH_UNAVAILABLE
                adapterInvoked && !walletSucceeded -> ItbCaseResult.Outcome.WALLET_FAILED
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
        )
    }
}
