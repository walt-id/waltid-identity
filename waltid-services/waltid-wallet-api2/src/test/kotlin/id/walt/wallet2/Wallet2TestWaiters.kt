package id.walt.wallet2

import id.walt.verifier2.data.Verification2Session
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Waits for the asynchronous verifier pipeline instead of racing its first IN_USE state. */
internal suspend fun HttpClient.awaitVerificationSession(
    sessionId: String,
    timeoutMillis: Long = 10_000,
): Verification2Session = withTimeout(timeoutMillis) {
    while (true) {
        val session = get("/verification-session/$sessionId/info").body<Verification2Session>()
        if (session.status.successful != null) return@withTimeout session
        delay(50)
    }
    @Suppress("UNREACHABLE_CODE")
    error("Verifier session polling loop terminated unexpectedly")
}
