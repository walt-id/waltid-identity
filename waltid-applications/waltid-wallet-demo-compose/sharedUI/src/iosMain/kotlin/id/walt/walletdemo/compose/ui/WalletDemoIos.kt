package id.walt.walletdemo.compose.ui

import androidx.compose.ui.window.ComposeUIViewController
import id.walt.wallet2.mobile.MobileWalletCrossProcessAccess
import id.walt.mdoc.proximity.mobile.NfcHostAvailability
import id.walt.mdoc.proximity.mobile.NfcHostPreparation
import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.mdoc.proximity.mobile.PreparedNfcHostSession
import id.walt.walletdemo.compose.logic.DemoWalletConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoProximityController
import id.walt.walletdemo.compose.logic.createIosDemoWallet
import id.walt.walletdemo.compose.logic.createIosDemoPinStore
import id.walt.walletdemo.compose.logic.createIosDemoSharingSettingsStore
import id.walt.walletdemo.compose.logic.createIosDemoBiometricAuthenticator
import id.walt.walletdemo.compose.logic.createIosDemoSigningProtectionStore
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import platform.UIKit.UIViewController

private var iosController: WalletDemoController? = null
private var iosProximityController: WalletDemoProximityController? = null
private var pendingDeepLink: String? = null

/**
 * Constructs Compose-framework NFC host results without re-exporting the mobile wallet dependency.
 *
 * Kotlin/Native assigns the same Kotlin declarations different Swift identities in independently
 * linked frameworks. These factories keep the concrete sealed result construction in the framework
 * that owns those identities while the Swift adapter performs only byte and enum conversion.
 */
fun composeNfcHostAvailable(): NfcHostAvailability = NfcHostAvailability.Available

fun composeNfcHostUnavailable(code: String, message: String): NfcHostAvailability =
    NfcHostAvailability.Unavailable(code, message)

fun composeNfcHostReady(session: PreparedNfcHostSession): NfcHostPreparation =
    NfcHostPreparation.Ready(session)

fun composeNfcHostUnavailablePreparation(code: String, message: String): NfcHostPreparation =
    NfcHostPreparation.Unavailable(NfcHostAvailability.Unavailable(code, message))

/**
 * Hosts the Compose demo in a `UIViewController` for the SwiftUI app.
 *
 * @param appGroupIdentifier App Group holding the encrypted wallet database, shared with the
 * document-provider extension.
 * @param keychainAccessGroup Build-expanded shared Keychain access group. Without it the wallet's
 * signing key lands in a group the extension cannot read, so the extension would see the credential
 * and then fail to sign for it.
 * @param nfcHostPlatformAdapter Swift-owned Core NFC adapter retained by the wallet for the
 * proximity-session lifetime.
 * @param onDigitalCredentialRegistryChanged Called from Kotlin whenever the wallet's credential set
 * changed and its desired Apple registration state was re-published. The host reconciles Apple's
 * `IdentityDocumentProviderRegistrationStore` here; the wallet cannot, since only the app process may
 * call `IdentityDocumentServices`, and Apple's `performRegistrationUpdates()` runs only periodically.
 * Without it an issued or deleted credential reaches the platform no earlier than the next foreground
 * transition. Not a suspending callback: Kotlin/Native cannot export a suspending function type to
 * Swift, and the host has nothing to report back anyway - the credential change is committed before
 * this runs, so the host starts its own task and the wallet does not wait for Apple's store.
 * @throws IllegalArgumentException When [appGroupIdentifier] or [keychainAccessGroup] is empty. This
 * demo ships a document-provider extension, so falling back to process-local storage would produce a
 * wallet that looks healthy and is invisible to the extension - a failure that otherwise only appears
 * during a presentation on a device. A build that genuinely has no App Group capability has to fail
 * here instead.
 */
fun walletDemoViewController(
    appGroupIdentifier: String,
    keychainAccessGroup: String,
    nfcHostPlatformAdapter: NfcHostPlatformAdapter,
    onDigitalCredentialRegistryChanged: () -> Unit,
    walletId: String = "default",
    attestationBaseUrl: String = "",
    attestationAttesterPath: String = "",
    attestationBearerToken: String = "",
    attestationHostHeader: String = "",
    transactionDataProfilesUrl: String = "",
    signingProtectionMode: String = "optional",
): UIViewController {
    val parsedSigningProtectionMode = WalletDemoSigningProtectionMode.parse(signingProtectionMode)
    val config = DemoWalletConfig(
        walletId = walletId,
        attestationBaseUrl = attestationBaseUrl,
        attestationAttesterPath = attestationAttesterPath,
        attestationBearerToken = attestationBearerToken,
        attestationHostHeader = attestationHostHeader,
        transactionDataProfilesUrl = transactionDataProfilesUrl,
        signingProtectionMode = parsedSigningProtectionMode,
    )
    require(appGroupIdentifier.isNotEmpty()) {
        "This demo ships a document-provider extension and requires an App Group"
    }
    require(keychainAccessGroup.isNotEmpty()) {
        "This demo ships a document-provider extension and requires a shared Keychain access group"
    }
    val crossProcessAccess = MobileWalletCrossProcessAccess(
        appGroupIdentifier = appGroupIdentifier,
        keychainAccessGroup = keychainAccessGroup,
    )
    val wallet = createIosDemoWallet(
        config = config,
        crossProcessAccess = crossProcessAccess,
        nfcHostPlatformAdapter = nfcHostPlatformAdapter,
        onDigitalCredentialRegistryChanged = { onDigitalCredentialRegistryChanged() },
    )
    val controller = WalletDemoController(
        wallet = wallet,
        pinStore = createIosDemoPinStore(config.walletId),
        biometricAuthenticator = createIosDemoBiometricAuthenticator(),
        signingProtectionMode = parsedSigningProtectionMode,
        signingProtectionStore = createIosDemoSigningProtectionStore(config.walletId),
        sharingSettings = createIosDemoSharingSettingsStore(appGroupIdentifier),
    )
    val proximityController = WalletDemoProximityController(wallet)
    iosController = controller
    iosProximityController?.dismiss()
    iosProximityController = proximityController
    pendingDeepLink?.let(controller::handleDeepLink)
    pendingDeepLink = null
    return ComposeUIViewController {
        MobileWalletDemoApp(controller, proximityController)
    }
}

fun handleWalletDemoDeepLink(url: String) {
    val controller = iosController
    if (controller == null) {
        pendingDeepLink = url
    } else {
        controller.handleDeepLink(url)
    }
}

fun handleWalletDemoApplicationForegrounded() {
    iosController?.handleApplicationForegrounded()
}
