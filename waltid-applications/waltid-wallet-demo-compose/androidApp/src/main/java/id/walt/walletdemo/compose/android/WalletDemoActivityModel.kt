package id.walt.walletdemo.compose.android

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Retains the wallet session across configuration changes without retaining its Activity. */
internal class WalletDemoActivityModel(
    context: Context,
    private val config: DemoWalletConfig,
    activity: FragmentActivity,
) : ViewModel() {
    private val applicationContext = context.applicationContext
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

    /** A browser callback after process loss continues once, even if MainActivity then rotates. */
    fun drainOrphanCreateAuthorization() {
        while (true) {
            val (sessionId, callbackUri) = OrphanAuthorizationCallback.take() ?: return
            viewModelScope.launch {
                try {
                    wallet.bootstrap(config.selectedSigningProtection(applicationContext))
                    val outcome = wallet.continueAuthorizationIssuance(sessionId, callbackUri)
                    WalletDemoCredentialStoreNotifier.notifyChanged()
                    Log.i("WaltDigitalCredentials", "Recovered provider authorization: ${outcome::class.simpleName}")
                } catch (cause: CancellationException) { throw cause
                } catch (cause: Exception) {
                    Log.e("WaltDigitalCredentials", "Could not recover provider authorization", cause)
                } finally { DigitalCredentialCreateAuthHandoff.clear(applicationContext, sessionId) }
            }
        }
    }

    fun attach(activity: FragmentActivity) { activityReference = WeakReference(activity) }

    fun detach(activity: FragmentActivity) {
        if (activityReference.get() === activity) activityReference.clear()
    }
}
