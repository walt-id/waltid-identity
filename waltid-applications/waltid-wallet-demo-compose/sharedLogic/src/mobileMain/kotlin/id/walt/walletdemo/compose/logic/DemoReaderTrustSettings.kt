package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.ProximityReaderPolicy
import id.walt.wallet2.mobile.ProximityReaderTrustImportPreview
import id.walt.wallet2.mobile.ProximityReaderTrustSettings
import id.walt.wallet2.mobile.ProximityReaderTrustSettingsCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** App-private persistence boundary for the canonical Reader Authentication settings JSON. */
interface DemoReaderTrustSettingsStore {
    fun load(): ProximityReaderTrustSettings
    fun save(settings: ProximityReaderTrustSettings)
}

class InMemoryDemoReaderTrustSettingsStore(
    initial: ProximityReaderTrustSettings = ProximityReaderTrustSettings(),
) : DemoReaderTrustSettingsStore {
    private var stored = initial

    override fun load(): ProximityReaderTrustSettings = stored

    override fun save(settings: ProximityReaderTrustSettings) {
        stored = settings
    }
}

internal class PersistentDemoReaderTrustSettingsStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) : DemoReaderTrustSettingsStore {
    override fun load(): ProximityReaderTrustSettings = read()?.let(
        ProximityReaderTrustSettingsCodec::decode
    ) ?: ProximityReaderTrustSettings()

    override fun save(settings: ProximityReaderTrustSettings) {
        write(ProximityReaderTrustSettingsCodec.encode(settings))
    }
}

data class DemoReaderTrustSettingsUiState(
    val settings: ProximityReaderTrustSettings,
    val importInProgress: Boolean = false,
    val loading: Boolean = false,
    val pendingImport: ProximityReaderTrustImportPreview? = null,
    val error: String? = null,
)

/** Coordinates review-before-save Reader Authentication settings without owning protocol logic. */
class DemoReaderTrustSettingsController(
    private val store: DemoReaderTrustSettingsStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutableState = MutableStateFlow(DemoReaderTrustSettingsUiState(ProximityReaderTrustSettings(), loading = true))
    private val writes = Mutex()
    private var importJob: Job? = null
    private var pendingWrites = 0
    private val loadJob = scope.launch(dispatcher) {
        try {
            val settings = withContext(workerDispatcher) { store.load() }
            mutableState.value = DemoReaderTrustSettingsUiState(settings)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutableState.update { it.copy(loading = false, error = "Stored Reader Authentication settings were invalid and were not loaded: ${error.message}") }
        }
    }
    val state: StateFlow<DemoReaderTrustSettingsUiState> = mutableState.asStateFlow()

    /** Read once by a new proximity session; later settings changes cannot mutate that snapshot. */
    fun sessionSnapshot(): ProximityReaderTrustSettings {
        check(!mutableState.value.loading) { "Reader Authentication settings are still loading" }
        return mutableState.value.settings
    }

    fun setReaderPolicy(policy: ProximityReaderPolicy) {
        persist { it.copy(readerPolicy = policy) }
    }

    fun prepareImport(sourceName: String, bytes: ByteArray) {
        if (mutableState.value.importInProgress || mutableState.value.loading) return
        mutableState.update { it.copy(importInProgress = true, pendingImport = null, error = null) }
        val ownedBytes = bytes.copyOf()
        val existing = mutableState.value.settings
        importJob = scope.launch(dispatcher) {
            try {
                val preview = withContext(workerDispatcher) { ProximityReaderTrustSettingsCodec.prepareImport(
                    sourceName = sourceName,
                    bytes = ownedBytes,
                    existing = existing,
                ) }
                currentCoroutineContext().ensureActive()
                mutableState.update {
                    it.copy(importInProgress = false, pendingImport = preview, error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        importInProgress = false,
                        pendingImport = null,
                        error = error.message ?: "Reader trust material could not be imported",
                    )
                }
            }
        }
    }

    fun confirmImport() {
        val preview = mutableState.value.pendingImport ?: return
        persist { preview.resultingSettings }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        mutableState.update { it.copy(importInProgress = pendingWrites > 0, pendingImport = null, error = null) }
    }

    fun removeReaderAuthority(certificateDerBase64Url: String) {
        persist { settings -> settings.copy(trustAnchors = settings.trustAnchors.filterNot {
            it.certificateDerBase64Url == certificateDerBase64Url
        }) }
    }

    fun removeRicalProvider(providerId: String) {
        persist { settings -> settings.copy(ricalProviders = settings.ricalProviders.filterNot { it.providerId == providerId }) }
    }

    fun reset() {
        cancelImport()
        persist { ProximityReaderTrustSettings() }
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    fun reportImportError(message: String) {
        mutableState.update {
            it.copy(importInProgress = false, pendingImport = null, error = message)
        }
    }

    private fun persist(update: (ProximityReaderTrustSettings) -> ProximityReaderTrustSettings) {
        cancelImport()
        pendingWrites += 1
        mutableState.update { it.copy(importInProgress = true) }
        scope.launch(dispatcher) {
            try {
                loadJob.join()
                writes.withLock {
                    // Once a save begins, publish its outcome even if the owning route closes.
                    withContext(NonCancellable) {
                        val settings = update(mutableState.value.settings)
                        withContext(workerDispatcher) { store.save(settings) }
                        mutableState.update { it.copy(settings = settings, pendingImport = null, error = null) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Reader Authentication settings could not be saved") }
            } finally {
                pendingWrites -= 1
                mutableState.update { it.copy(importInProgress = pendingWrites > 0) }
            }
        }
    }

}

internal const val READER_TRUST_SETTINGS_KEY =
    "id.walt.walletdemo.sharing.readerTrustSettings"
