package id.walt.walletdemo.compose.logic

/**
 * Demo UX preferences for credential sharing.
 *
 * This is not wallet identity: reset must leave these values alone so a reviewer can keep a
 * platform-picker-only Digital Credentials path across wallet recreations.
 */
interface DemoSharingSettingsStore {
    fun showDcApiPresentationPreview(): Boolean
    fun setShowDcApiPresentationPreview(enabled: Boolean)
    fun proximityTransportProfile(): WalletDemoProximityTransportProfile
    fun setProximityTransportProfile(profile: WalletDemoProximityTransportProfile)
}

class InMemoryDemoSharingSettingsStore(
    initialShowDcApiPresentationPreview: Boolean = true,
    initialProximityTransportProfile: WalletDemoProximityTransportProfile =
        WalletDemoProximityTransportProfile.Default,
) : DemoSharingSettingsStore {
    private var showDcApiPresentationPreview: Boolean = initialShowDcApiPresentationPreview
    private var proximityTransportProfile: WalletDemoProximityTransportProfile =
        initialProximityTransportProfile

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
    private val readEnabled: () -> Boolean?,
    private val writeEnabled: (Boolean) -> Unit,
    private val readProximityTransportProfile: () -> String?,
    private val writeProximityTransportProfile: (String) -> Unit,
) : DemoSharingSettingsStore {
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
