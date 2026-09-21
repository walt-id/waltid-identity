package id.walt.itb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.time.Instant

/** The UI transport owns only the interaction belonging to the supplied REST session. */
interface ItbInteractionBridge {
    suspend fun read(session: ItbRestClient.CreatedSession): ItbWalletInteraction
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
    enum class Outcome { PASSED, WALLET_FAILED, ITB_FAILED, ERROR, TIMED_OUT, INCOMPLETE, NOT_RUN }
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

    suspend fun run(system: String, actor: String, suite: String, case: String): ItbCaseResult {
        val start = Instant.now().toString()
        var sessions = emptyList<ItbRestClient.CreatedSession>()
        var session: ItbRestClient.CreatedSession? = null
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
                sessions = api.start(system, actor, suite, listOf(case))
                check(sessions.size == 1 && sessions.single().testSuite == suite && sessions.single().testCase == case) {
                    "ITB started an unexpected case inventory"
                }
                val createdSession = sessions.single()
                session = createdSession
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
                val finalReport = api.report(case, createdSession.session)
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
            outcome = if (adapterInvoked && !walletSucceeded) ItbCaseResult.Outcome.WALLET_FAILED else ItbCaseResult.Outcome.ERROR
        } finally {
            withContext(NonCancellable) {
                // Never stop other tenant sessions; only IDs returned by this invocation's start call.
                if (sessions.isNotEmpty() && report?.isComplete != true) {
                    try {
                        val active = api.status(sessions.map { it.session }).filterNot { it.isComplete }
                        if (active.isNotEmpty()) api.stop(active.map { it.session })
                        session?.let { report = api.report(case, it.session) }
                    } catch (_: Exception) {
                        cleanupFailed = true
                    }
                }
            }
        }
        return ItbCaseResult(
            suite, case, session?.session, outcome, phase, adapterInvoked, walletSucceeded,
            report?.verdict, report?.isComplete == true, report?.caseVersion,
            start, Instant.now().toString(), failure, cleanupFailed, errorCode,
        )
    }
}
