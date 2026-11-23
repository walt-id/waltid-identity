package id.waltid.openid4vp.wallet.presentation

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Trusted host review check scoped to one presentation attempt, never a serialized request policy.
 * Used before proof creation and again after native authorization/signing, before delivery.
 * Existing callers without a guard retain their existing behavior.
 */
class PresentationReviewGuard(private val requireCurrent: suspend () -> Unit) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PresentationReviewGuard>
    suspend fun check() { requireCurrent() }
}

internal suspend fun requireCurrentPresentationReview() {
    currentCoroutineContext()[PresentationReviewGuard]?.check()
}
