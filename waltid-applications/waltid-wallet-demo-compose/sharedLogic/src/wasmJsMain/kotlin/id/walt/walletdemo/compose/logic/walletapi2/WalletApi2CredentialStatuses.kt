package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import id.walt.walletdemo.compose.logic.WalletDemoStatusRefreshSummary
import id.walt.walletdemo.compose.logic.WalletDemoStatusReviewChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One browser wallet lifetime. No evidence or context identifier is written to browser storage. */
internal class WalletApi2CredentialStatuses(private val client: WalletApi2Client, private val walletId: String) {
    private data class ActiveReview(val snapshot: CredentialStatusSnapshotDto, val awaitingDisplay: Boolean = false) {
        val context = requireNotNull(snapshot.statusContext) { "Server did not return the active status review" }
    }
    private val mutex = Mutex()
    private var review: ActiveReview? = null
    private var reviewInvalidated = false
    private var reviewEpoch = 0L

    suspend fun snapshot(): WalletDemoCredentialStatuses = mutex.withLock {
        val snapshot = readSnapshot()?.toDemoStatuses() ?: WalletDemoCredentialStatuses(
            client.listCredentialMetadata(walletId).associate { it.id to WalletDemoCredentialStatus.ServerUnsupported },
            legacySubmission = true,
        )
        snapshot.copy(reviewEpoch = reviewEpoch)
    }

    /** A local server read validates context lifetime; expiration always invalidates pending consent. */
    suspend fun submissionSnapshot(renderedEpoch: Long? = null): CredentialStatusSnapshotDto? = mutex.withLock {
        if (renderedEpoch != null && renderedEpoch != reviewEpoch) throw WalletDemoStatusReviewChanged()
        // Polling may already have recovered cold, with the same semantic revision. That recovery
        // cannot silently revive an attempt bound to the lost context. A matching rendered epoch
        // identifies fresh consent to that recovered review, without an extra failed attempt.
        if (reviewInvalidated) {
            reviewInvalidated = false
            if (renderedEpoch == null) throw WalletDemoStatusReviewChanged()
        }
        try { readSnapshot(consumeImmediate = false, failOnExpired = true) }
        catch (changed: WalletDemoStatusReviewChanged) {
            reviewInvalidated = false // This attempt has already been rejected; require a new action.
            throw changed
        }
    }

    suspend fun refresh(ids: Set<String>?, force: Boolean): WalletDemoStatusRefreshSummary = mutex.withLock {
        // Context creation and all snapshot reads are local; only this explicit action checks issuers.
        val before = if (review == null) openContext() else {
            val snapshot = readSnapshot(consumeImmediate = false)
            if (review == null) openContext() else requireNotNull(snapshot)
        }
        val context = requireNotNull(review).context
        val selection = ids?.let { selected -> before.credentials.filter { it.reference.credentialId in selected }.map { it.reference } }
        require(selection == null || selection.isNotEmpty())
        val results = client.refreshCredentialStatuses(walletId, RefreshCredentialStatusesDto(
            selection, if (force) "FORCE" else "IF_DUE", context.id,
        ))
        // Display the response itself before polling again, including no-store and failed attempts.
        review = ActiveReview(before.withRefreshResults(results), awaitingDisplay = true)
        if (force) reviewInvalidated = false // The explicit check starts a new review; its caller renders this result before enabling submission.
        WalletDemoStatusRefreshSummary(
            results.count { it.outcome == "COMPLETED" }, results.count { it.outcome == "UNCHANGED" },
            results.count { it.outcome == "DEFERRED" }, results.count { it.outcome != "REMOVED" && it.hasCheckErrors },
        )
    }

    private suspend fun openContext(): CredentialStatusSnapshotDto = client.createCredentialStatusContext(walletId).also {
        review = ActiveReview(it)
        reviewEpoch += 1
    }

    /** Called only under [mutex], so an earlier poll cannot overwrite an explicit refresh. */
    private suspend fun readSnapshot(consumeImmediate: Boolean = true, failOnExpired: Boolean = false): CredentialStatusSnapshotDto? {
        val active = review
        if (consumeImmediate && active?.awaitingDisplay == true) {
            review = active.copy(awaitingDisplay = false)
            return active.snapshot
        }
        val snapshot = try { client.credentialStatusSnapshot(walletId, active?.context?.id) }
        catch (changed: WalletDemoStatusReviewChanged) {
            if (active == null) throw changed
            review = null
            reviewInvalidated = true
            reviewEpoch += 1
            if (failOnExpired) throw changed
            // Recover once with a cold local read. No issuer work and no submission retry.
            client.credentialStatusSnapshot(walletId)
        }
        if (review != null) {
            requireNotNull(snapshot)
            check(snapshot.statusContext?.id == active?.context?.id) { "Server changed the active status review" }
            review = ActiveReview(snapshot)
        }
        return snapshot
    }
}
