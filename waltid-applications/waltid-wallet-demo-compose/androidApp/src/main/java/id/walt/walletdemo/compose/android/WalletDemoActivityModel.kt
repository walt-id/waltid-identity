package id.walt.walletdemo.compose.android

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController
import id.walt.walletdemo.compose.logic.DemoWalletConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoProximityController
import id.walt.walletdemo.compose.logic.createAndroidDemoBiometricAuthenticator
import id.walt.walletdemo.compose.logic.createAndroidDemoPinStore
import id.walt.walletdemo.compose.logic.createAndroidDemoReaderTrustSettingsStore
import id.walt.walletdemo.compose.logic.createAndroidDemoSharingSettingsStore
import id.walt.walletdemo.compose.logic.createAndroidDemoWallet
import java.lang.ref.WeakReference

/** Retains the wallet session across configuration changes without retaining its Activity. */
internal class WalletDemoActivityModel(
    context: Context,
    config: DemoWalletConfig,
    activity: FragmentActivity,
) : ViewModel() {
    private var activityReference = WeakReference(activity)
    private val wallet = createAndroidDemoWallet(context.applicationContext, config) { activityReference.get() }
    private val sharingSettings = createAndroidDemoSharingSettingsStore(context.applicationContext)

    val controller = WalletDemoController(
        wallet = wallet,
        pinStore = createAndroidDemoPinStore(context.applicationContext, config.walletId),
        biometricAuthenticator = createAndroidDemoBiometricAuthenticator { activityReference.get() },
        signingProtectionMode = config.signingProtectionMode,
        signingProtectionStore = config.signingProtectionStore(context.applicationContext),
        sharingSettings = sharingSettings,
        scope = viewModelScope,
    )
    val readerTrustSettingsController = DemoReaderTrustSettingsController(
        createAndroidDemoReaderTrustSettingsStore(context.applicationContext),
        scope = viewModelScope,
    )
    val proximityController = WalletDemoProximityController(
        wallet = wallet,
        profileProvider = sharingSettings::proximityTransportProfile,
        approvalModeProvider = sharingSettings::proximityApprovalMode,
        readerTrustSettingsProvider = readerTrustSettingsController::sessionSnapshot,
        scope = viewModelScope,
    )

    fun attach(activity: FragmentActivity) { activityReference = WeakReference(activity) }

    fun detach(activity: FragmentActivity) {
        if (activityReference.get() === activity) activityReference.clear()
    }
}
