package id.walt.crypto2.signum

import id.walt.crypto2.keys.Signer
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private enum class AuthorizationState { AVAILABLE, CONSUMED, SIGNED, CLOSED }

internal fun SignumAuthenticationPolicy.requireTimedBiometrics(): SignumAuthenticationPolicy.UserPresence =
    (this as? SignumAuthenticationPolicy.UserPresence)?.takeIf {
        it.biometric && !it.deviceCredential && it.timeoutSeconds in 1..30
    } ?: throw SignumAuthorizationException("Fresh signing requires a timed biometric-only key")

/** Called immediately after native authentication. No refresh, second signature or escaped use. */
internal suspend fun <T> withFreshBiometricSigner(
    timeoutSeconds: Int,
    signer: Signer,
    authenticatedAt: TimeMark = TimeSource.Monotonic.markNow(),
    operation: suspend (Signer) -> T,
): T {
    require(timeoutSeconds in 1..30)
    val lock = Mutex()
    var state = AuthorizationState.AVAILABLE
    fun requireCurrent() {
        if (state == AuthorizationState.CLOSED || authenticatedAt.elapsedNow() >= timeoutSeconds.seconds) {
            throw SignumAuthorizationException("Fresh biometric authorization has expired or ended")
        }
    }
    val authorized = Signer { data, algorithm ->
        lock.withLock {
            currentCoroutineContext().ensureActive()
            requireCurrent()
            if (state != AuthorizationState.AVAILABLE) {
                throw SignumAuthorizationException("Fresh biometric authorization was already consumed")
            }
            state = AuthorizationState.CONSUMED
            val signature = signer.sign(data.copyOf(), algorithm)
            currentCoroutineContext().ensureActive()
            requireCurrent()
            state = AuthorizationState.SIGNED
            signature
        }
    }
    try {
        currentCoroutineContext().ensureActive()
        val result = operation(authorized)
        currentCoroutineContext().ensureActive()
        lock.withLock {
            requireCurrent()
            if (state != AuthorizationState.SIGNED) {
                throw SignumAuthorizationException("No signature established this authorization")
            }
        }
        return result
    } finally {
        withContext(NonCancellable) { lock.withLock { state = AuthorizationState.CLOSED } }
    }
}
