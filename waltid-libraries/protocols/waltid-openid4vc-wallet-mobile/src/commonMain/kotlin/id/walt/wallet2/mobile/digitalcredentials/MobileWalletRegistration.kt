package id.walt.wallet2.mobile.digitalcredentials

import id.walt.crypto.utils.ShaUtils
import id.walt.wallet2.data.Wallet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes publication of the current wallet contents to the platform registry. */
internal class MobileWalletRegistration(
    private val wallet: Wallet,
    private val credentialRegistry: MobileWalletCredentialRegistry,
    private val preferredLocales: List<String>,
    private val registrationProjection: MobileWalletRegistryProjection,
    private val onRegistrationChanged: suspend () -> Unit,
) {
    val state: StateFlow<MobileWalletCredentialRegistrationResult?> get() = observations

    suspend fun refresh(): MobileWalletCredentialRegistrationResult = registrationMutex.withLock {
        currentCoroutineContext().ensureActive()
        val records = try {
            if (credentialRegistry === UnavailableMobileWalletCredentialRegistry) emptyList()
            else wallet.registryRecords(preferredLocales, registrationProjection)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            lastRegistrationResult.value = MobileWalletCredentialRegistrationResult(
                available = false,
                registeredEntryCount = 0,
                reason = failure.message ?: failure::class.simpleName ?: "Credential registration failed",
            )
            throw failure
        }
        currentCoroutineContext().ensureActive()
        val presentationResult = runCatching {
            credentialRegistry.replace(registryId = digitalCredentialRegistryId(), records = records)
        }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            MobileWalletCredentialRegistrationResult(
                available = false,
                registeredEntryCount = 0,
                reason = failure.message ?: failure::class.simpleName ?: "Credential registration failed",
            )
        }
        currentCoroutineContext().ensureActive()
        // Creation options advertise issuance capability and must not share the presentation
        // replace lifecycle; failures here do not roll back a successful presentation projection.
        runCatching {
            credentialRegistry.registerCreationOptions()
        }.onFailure { failure ->
            if (failure is CancellationException) throw failure
        }
        currentCoroutineContext().ensureActive()
        lastRegistrationResult.value = presentationResult
        presentationResult
    }

    /**
     * Synchronizes the platform registry after the wallet store has already changed.
     *
     * The credential change is committed by the time this runs, so neither a registry nor a host
     * failure may turn a completed operation into a failed one; the outcome is reported through
     * [state] instead. The host is notified even when publishing failed,
     * because an earlier projection may still be pending.
     */
    suspend fun afterMutation() {
        runCatching { refresh() }.onFailure { failure ->
            if (failure is CancellationException) throw failure
            // The failed snapshot was reported while holding registrationMutex.
        }
        runCatching { onRegistrationChanged() }.onFailure { failure ->
            if (failure is CancellationException) throw failure
        }
    }

    private val registrationMutex = Mutex()
    private val lastRegistrationResult = MutableStateFlow<MobileWalletCredentialRegistrationResult?>(null)
    private val observations = lastRegistrationResult.asStateFlow()

    private fun digitalCredentialRegistryId(): String =
        "waltid-${ShaUtils.calculateSha256Base64Url(wallet.id).take(24)}"
}
