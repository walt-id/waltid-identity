package id.walt.wallet2.client

sealed interface WalletClientAttestationStatus {
    data class Present(
        val expiresAt: Long? = null,
    ) : WalletClientAttestationStatus

    data object NotRequired : WalletClientAttestationStatus
}

fun interface WalletClientAttestationProvider {
    suspend fun ensureReady(): WalletClientAttestationStatus

    object Noop : WalletClientAttestationProvider {
        override suspend fun ensureReady(): WalletClientAttestationStatus =
            WalletClientAttestationStatus.NotRequired
    }
}
