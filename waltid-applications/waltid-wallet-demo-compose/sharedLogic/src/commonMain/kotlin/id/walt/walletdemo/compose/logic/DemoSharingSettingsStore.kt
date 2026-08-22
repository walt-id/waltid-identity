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
}

class InMemoryDemoSharingSettingsStore(
    initialShowDcApiPresentationPreview: Boolean = true,
) : DemoSharingSettingsStore {
    private var showDcApiPresentationPreview: Boolean = initialShowDcApiPresentationPreview

    override fun showDcApiPresentationPreview(): Boolean = showDcApiPresentationPreview

    override fun setShowDcApiPresentationPreview(enabled: Boolean) {
        showDcApiPresentationPreview = enabled
    }
}

internal class PersistentDemoSharingSettingsStore(
    private val readEnabled: () -> Boolean?,
    private val writeEnabled: (Boolean) -> Unit,
) : DemoSharingSettingsStore {
    override fun showDcApiPresentationPreview(): Boolean = readEnabled() ?: true

    override fun setShowDcApiPresentationPreview(enabled: Boolean) {
        writeEnabled(enabled)
    }
}

internal const val SHOW_DC_API_PRESENTATION_PREVIEW_KEY =
    "id.walt.walletdemo.sharing.showDcApiPresentationPreview"
