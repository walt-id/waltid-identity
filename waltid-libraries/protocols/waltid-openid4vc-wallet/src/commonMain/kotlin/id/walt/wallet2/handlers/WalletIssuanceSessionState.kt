package id.walt.wallet2.handlers

import kotlinx.coroutines.sync.Mutex

/**
 * Private runtime state for one wallet's issuance transitions, shared across request-scoped engines.
 * Keep one instance per wallet in a service process. Each engine still resolves keys and writes
 * credentials through its current Wallet, so sharing state does not share caller permissions.
 * [store] provides restart recovery; this state coordinates callers within the current process.
 */
class WalletIssuanceSessionState(
    val walletId: String,
    internal val store: WalletIssuanceSessionStore? = null,
) {
    internal val mutex = Mutex()
    internal var closed = false
    internal val sessions = LinkedHashMap<String, WalletIssuanceSessionService.ActiveSession>()
    internal val deferred = LinkedHashMap<String, WalletIssuanceSessionService.DeferredRecord>()
    internal val pollingDeferred = mutableSetOf<String>()
}
