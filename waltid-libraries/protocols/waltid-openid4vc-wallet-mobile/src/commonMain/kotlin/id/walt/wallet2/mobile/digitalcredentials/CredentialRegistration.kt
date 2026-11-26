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
internal class CredentialRegistration(
    private val wallet: Wallet,
    private val credentialRegistry: CredentialRegistry,
    private val preferredLocales: List<String>,
    private val registrationProjection: RegistryProjection,
    private val onRegistrationChanged: suspend () -> Unit,
) {
    val state: StateFlow<CredentialRegistrationResult?> get() = observations

    suspend fun refresh(): CredentialRegistrationResult = registrationMutex.withLock {
        currentCoroutineContext().ensureActive()
        val records = try {
            if (credentialRegistry === UnavailableCredentialRegistry) emptyList()
            else wallet.registryRecords(preferredLocales, registrationProjection)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            lastRegistrationResult.value = CredentialRegistrationResult.Unavailable(failure.message ?: failure::class.simpleName ?: "Credential registration failed")
            throw failure
        }
        currentCoroutineContext().ensureActive()
        val presentationResult = runCatching {
            credentialRegistry.replace(registryId = digitalCredentialRegistryId(), records = records)
        }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            CredentialRegistrationResult.Unavailable(failure.message ?: failure::class.simpleName ?: "Credential registration failed")
        }
        // A completed presentation replacement remains observable if creation is cancelled.
        lastRegistrationResult.value = presentationResult
        currentCoroutineContext().ensureActive()
        runCatching {
            credentialRegistry.registerCreationOptions()
        }.onFailure { failure ->
            if (failure is CancellationException) throw failure
        }
        currentCoroutineContext().ensureActive()
        presentationResult
    }

    /**
     * Synchronizes the platform registry after the wallet store has already changed.
     *
     * The store has committed. [state] describes presentation replacement. Creation-option registration
     * is attempted independently, and the host is notified even after a presentation failure.
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
    private val lastRegistrationResult = MutableStateFlow<CredentialRegistrationResult?>(null)
    private val observations = lastRegistrationResult.asStateFlow()

    private fun digitalCredentialRegistryId(): String =
        "waltid-${ShaUtils.calculateSha256Base64Url(wallet.id).take(24)}"
}
