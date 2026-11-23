package id.walt.walletdemo.compose.logic

/**
 * Demo UX preferences for credential sharing.
 *
 * This is not wallet identity: reset must leave these values alone so a reviewer can keep a
 * platform-picker-only Digital Credentials path across wallet recreations.
 */
interface DemoSharingSettingsStore {
    fun automaticCredentialStatusChecks(): Boolean = true
    fun setAutomaticCredentialStatusChecks(enabled: Boolean) {}

    fun showDcApiPresentationPreview(): Boolean
    fun setShowDcApiPresentationPreview(enabled: Boolean)
    fun proximityTransportProfile(): WalletDemoProximityTransportProfile
    fun setProximityTransportProfile(profile: WalletDemoProximityTransportProfile)
    fun proximityApprovalMode(): WalletDemoProximityApprovalMode
    fun setProximityApprovalMode(mode: WalletDemoProximityApprovalMode)
}

class InMemoryDemoSharingSettingsStore(
    initialShowDcApiPresentationPreview: Boolean = true,
    initialProximityTransportProfile: WalletDemoProximityTransportProfile =
        WalletDemoProximityTransportProfile.Default,
    initialProximityApprovalMode: WalletDemoProximityApprovalMode = WalletDemoProximityApprovalMode.AskEachTime,
) : DemoSharingSettingsStore {
    private var statusChecks = true
    override fun automaticCredentialStatusChecks(): Boolean = statusChecks
    override fun setAutomaticCredentialStatusChecks(enabled: Boolean) { statusChecks = enabled }
    private var showDcApiPresentationPreview: Boolean = initialShowDcApiPresentationPreview
    private var proximityTransportProfile: WalletDemoProximityTransportProfile =
        initialProximityTransportProfile
    private var approvalMode = initialProximityApprovalMode

    override fun proximityApprovalMode(): WalletDemoProximityApprovalMode = approvalMode
    override fun setProximityApprovalMode(mode: WalletDemoProximityApprovalMode) { approvalMode = mode }

    override fun showDcApiPresentationPreview(): Boolean = showDcApiPresentationPreview

    override fun setShowDcApiPresentationPreview(enabled: Boolean) {
        showDcApiPresentationPreview = enabled
    }

    override fun proximityTransportProfile(): WalletDemoProximityTransportProfile =
        proximityTransportProfile

    override fun setProximityTransportProfile(profile: WalletDemoProximityTransportProfile) {
        proximityTransportProfile = profile
    }
}

internal class PersistentDemoSharingSettingsStore(
    private val readStatusChecks: () -> Boolean? = { null },
    private val writeStatusChecks: (Boolean) -> Unit = {},
    private val readEnabled: () -> Boolean?,
    private val writeEnabled: (Boolean) -> Unit,
    private val readProximityTransportProfile: () -> String?,
    private val writeProximityTransportProfile: (String) -> Unit,
    private val readProximityApprovalMode: () -> String?,
    private val writeProximityApprovalMode: (String) -> Unit,
) : DemoSharingSettingsStore {
    override fun automaticCredentialStatusChecks(): Boolean = readStatusChecks() ?: true
    override fun setAutomaticCredentialStatusChecks(enabled: Boolean) = writeStatusChecks(enabled)

    override fun proximityApprovalMode(): WalletDemoProximityApprovalMode =
        WalletDemoProximityApprovalMode.fromPersistedValue(readProximityApprovalMode())

    override fun setProximityApprovalMode(mode: WalletDemoProximityApprovalMode) {
        writeProximityApprovalMode(mode.persistedValue)
    }

    override fun showDcApiPresentationPreview(): Boolean = readEnabled() ?: true

    override fun setShowDcApiPresentationPreview(enabled: Boolean) {
        writeEnabled(enabled)
    }

    override fun proximityTransportProfile(): WalletDemoProximityTransportProfile =
        WalletDemoProximityTransportProfile.fromPersistedValue(readProximityTransportProfile())

    override fun setProximityTransportProfile(profile: WalletDemoProximityTransportProfile) {
        writeProximityTransportProfile(profile.persistedValue)
    }
}

internal const val SHOW_DC_API_PRESENTATION_PREVIEW_KEY =
    "id.walt.walletdemo.sharing.showDcApiPresentationPreview"
internal const val PROXIMITY_TRANSPORT_PROFILE_KEY =
    "id.walt.walletdemo.sharing.proximityTransportProfile"
internal const val PROXIMITY_APPROVAL_MODE_KEY =
    "id.walt.walletdemo.sharing.proximityApprovalMode"

internal const val AUTOMATIC_CREDENTIAL_STATUS_CHECKS_KEY = "id.walt.walletdemo.status.automatic"
