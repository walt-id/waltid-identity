package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletProximityReaderPolicy
import id.walt.wallet2.mobile.MobileWalletProximityReaderTrustImportPreview
import id.walt.wallet2.mobile.MobileWalletProximityReaderTrustSettings
import id.walt.wallet2.mobile.MobileWalletProximityReaderTrustSettingsCodec
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

/** App-private persistence boundary for the canonical Reader Authentication settings JSON. */
interface DemoReaderTrustSettingsStore {
    fun load(): MobileWalletProximityReaderTrustSettings
    fun save(settings: MobileWalletProximityReaderTrustSettings)
}

class InMemoryDemoReaderTrustSettingsStore(
    initial: MobileWalletProximityReaderTrustSettings = MobileWalletProximityReaderTrustSettings(),
) : DemoReaderTrustSettingsStore {
    private var stored = initial

    override fun load(): MobileWalletProximityReaderTrustSettings = stored

    override fun save(settings: MobileWalletProximityReaderTrustSettings) {
        stored = settings
    }
}

internal class PersistentDemoReaderTrustSettingsStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) : DemoReaderTrustSettingsStore {
    override fun load(): MobileWalletProximityReaderTrustSettings = read()?.let(
        MobileWalletProximityReaderTrustSettingsCodec::decode
    ) ?: MobileWalletProximityReaderTrustSettings()

    override fun save(settings: MobileWalletProximityReaderTrustSettings) {
        write(MobileWalletProximityReaderTrustSettingsCodec.encode(settings))
    }
}

data class DemoReaderTrustSettingsUiState(
    val settings: MobileWalletProximityReaderTrustSettings,
    val importInProgress: Boolean = false,
    val pendingImport: MobileWalletProximityReaderTrustImportPreview? = null,
    val error: String? = null,
)

/** Coordinates review-before-save Reader Authentication settings without owning protocol logic. */
class DemoReaderTrustSettingsController(
    private val store: DemoReaderTrustSettingsStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val mutableState = MutableStateFlow(
        runCatching { DemoReaderTrustSettingsUiState(store.load()) }.getOrElse { error ->
            DemoReaderTrustSettingsUiState(
                settings = MobileWalletProximityReaderTrustSettings(),
                error = "Stored Reader Authentication settings were invalid and were not loaded: " +
                    (error.message ?: "unknown error"),
            )
        }
    )
    val state: StateFlow<DemoReaderTrustSettingsUiState> = mutableState.asStateFlow()

    /** Read once by a new proximity session; later settings changes cannot mutate that snapshot. */
    fun sessionSnapshot(): MobileWalletProximityReaderTrustSettings = mutableState.value.settings

    fun setReaderPolicy(policy: MobileWalletProximityReaderPolicy) {
        persist(mutableState.value.settings.copy(readerPolicy = policy))
    }

    fun prepareImport(sourceName: String, bytes: ByteArray) {
        if (mutableState.value.importInProgress) return
        mutableState.update { it.copy(importInProgress = true, pendingImport = null, error = null) }
        scope.launch(dispatcher) {
            try {
                val preview = MobileWalletProximityReaderTrustSettingsCodec.prepareImport(
                    sourceName = sourceName,
                    bytes = bytes,
                    existing = mutableState.value.settings,
                )
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
        persist(preview.resultingSettings)
    }

    fun cancelImport() {
        mutableState.update { it.copy(importInProgress = false, pendingImport = null, error = null) }
    }

    fun removeReaderAuthority(certificateDerBase64Url: String) {
        persist(
            mutableState.value.settings.copy(
                trustAnchors = mutableState.value.settings.trustAnchors.filterNot {
                    it.certificateDerBase64Url == certificateDerBase64Url
                }
            )
        )
    }

    fun removeRicalProvider(providerId: String) {
        persist(
            mutableState.value.settings.copy(
                ricalProviders = mutableState.value.settings.ricalProviders.filterNot {
                    it.providerId == providerId
                }
            )
        )
    }

    fun reset() {
        persist(MobileWalletProximityReaderTrustSettings())
    }

    fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    fun reportImportError(message: String) {
        mutableState.update {
            it.copy(importInProgress = false, pendingImport = null, error = message)
        }
    }

    private fun persist(settings: MobileWalletProximityReaderTrustSettings) {
        try {
            store.save(settings)
            mutableState.value = DemoReaderTrustSettingsUiState(settings)
        } catch (error: Throwable) {
            mutableState.update {
                it.copy(error = error.message ?: "Reader Authentication settings could not be saved")
            }
        }
    }
}

internal const val READER_TRUST_SETTINGS_KEY =
    "id.walt.walletdemo.sharing.readerTrustSettings"
