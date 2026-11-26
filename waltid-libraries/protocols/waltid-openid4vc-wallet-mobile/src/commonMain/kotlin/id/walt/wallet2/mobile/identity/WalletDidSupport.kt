package id.walt.wallet2.mobile.identity

import id.walt.did.dids.DidService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object WalletDidSupport {
    suspend fun ensureInitialized() = initializationMutex.withLock {
        if (!initialized) {
            DidService.minimalInit()
            initialized = true
        }
    }
    private val initializationMutex = Mutex()

    private var initialized = false
}
