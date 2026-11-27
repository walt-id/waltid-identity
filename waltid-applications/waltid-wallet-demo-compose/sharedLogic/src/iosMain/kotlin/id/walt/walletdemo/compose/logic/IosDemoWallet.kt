package id.walt.walletdemo.compose.logic

import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.wallet2.mobile.WalletConfiguration
import id.walt.wallet2.mobile.WalletFactory
import id.walt.wallet2.mobile.digitalcredentials.WalletAppGroupAccess
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialsConfiguration
import id.walt.wallet2.mobile.identity.WalletKeyAuthorization
import id.walt.wallet2.mobile.issuance.IssuanceConfiguration
import id.walt.wallet2.mobile.presentation.PresentationConfiguration
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPrompt
import id.walt.walletdemo.attestation.DemoKeyAttestationProviders

/**
 * Creates the Compose demo's iOS wallet.
 *
 * @param crossProcessAccess Apple App Group and shared Keychain group so the document-provider
 * extension opens this same wallet. Only the Swift host can resolve them - the Keychain group needs a
 * build-expanded Team ID - so they are passed in rather than defaulted here, and they stay off the
 * portable [DemoWalletConfig] because they describe Apple host configuration, not demo behavior.
 * @param nfcHostPlatformAdapter Swift-owned Core NFC adapter shared with the native demo. Keeping
 * CardSession outside Kotlin limits common code to protocol state and APDU routing.
 * @param onDigitalCredentialRegistryChanged Called after the wallet re-published its desired Apple
 * registration state. Writing Apple's registration store needs `IdentityDocumentServices`, which only
 * the Swift host may call, so the host reconciles here; see [WalletConfiguration].
 */
fun createIosDemoWallet(
    config: DemoWalletConfig = DemoWalletConfig(),
    crossProcessAccess: WalletAppGroupAccess,
    nfcHostPlatformAdapter: NfcHostPlatformAdapter,
    onDigitalCredentialRegistryChanged: suspend () -> Unit,
): ProximityDemoWallet {

    return LazyProximityDemoWallet {
        val transactionDataProfiles = config.resolveDemoTransactionDataProfiles()
        SdkDemoWallet(
            WalletFactory(nfcHostPlatformAdapter).open(
                WalletConfiguration(
                    walletId = config.walletId,
                    keyAuthorization = WalletKeyAuthorization(
                        policy = config.signingProtectionMode.defaultSelection.toKeyUseAuthorizationPolicy(),
                        prompt = KeyUseAuthorizationPrompt(
                            reason = "Authorize wallet signing",
                            cancelText = "Cancel",
                        ),
                    ),
                    signingIdentity = id.walt.wallet2.mobile.identity.SigningIdentityConfiguration(
                        recoveryProviders = listOf(id.walt.wallet2.recovery.keychain.KeychainIdentityRecovery("wallet-demo", crossProcessAccess.keychainAccessGroup)),
                        alternativeAuthorizations = config.signingProtectionMode.alternativeAuthorizations(),
                        platform = id.walt.crypto2.keys.PlatformKeyConfiguration.IosKeychain(accessGroup = crossProcessAccess.keychainAccessGroup),
                    ),
                    issuance = IssuanceConfiguration(
                        attestation = config.toWalletAttestationConfig(),
                        walletProvider = config.toWalletProviderConfig(),
                    ),
                    presentation = PresentationConfiguration(
                        verifierTrust = DemoClientIdTrust.configuration,
                        transactionDataProfiles = transactionDataProfiles.profiles,
                        paymentCredentialIssuers = config.paymentCredentialIssuers(),
                    ),
                    digitalCredentials = DigitalCredentialsConfiguration(
                        crossProcessAccess = crossProcessAccess,
                        onRegistrationChanged = onDigitalCredentialRegistryChanged,
                    ),
                ),
            ).attachKeyAttestationProviderResolver(DemoKeyAttestationProviders()),
            warning = transactionDataProfiles.warning,
            isIos = true,
            onRegistrationRefreshed = onDigitalCredentialRegistryChanged,
            preferredSigningProtection = config.signingProtectionMode.defaultSelection,
        )
    }
}
