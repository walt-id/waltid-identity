package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.WalletConfiguration
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialsConfiguration
import id.walt.wallet2.mobile.digitalcredentials.UnavailableCredentialRegistry
import id.walt.wallet2.mobile.digitalcredentials.WalletAppGroupAccess
import id.walt.wallet2.mobile.identity.SigningIdentityConfiguration
import id.walt.wallet2.mobile.identity.WalletKeyAuthorization
import id.walt.wallet2.mobile.platformPreferredLocales
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPrompt

/**
 * Configuration used when creating an iOS [WalletSdkBridge].
 *
 * @property walletId Stable identifier for wallet-local data.
 * @property persistence Encrypted database key ownership and custom stores.
 * @property defaultKeyUseAuthorizationPolicy Default authorization policy for new keys.
 * @property keyUseAuthorizationPrompt Prompt for protected signing operations.
 * @property signingIdentity Identity policy, recovery providers and key custodians.
 * @property issuance Issuer trust and attestation integrations.
 * @property presentation Verifier trust and consent profiles.
 * @property registersDigitalCredentials Whether to publish wallet metadata to the platform registry.
 * @property crossProcessAccess Shared App Group container and Keychain access group.
 * @property preferredLocales Ordered BCP 47 preferences for display metadata.
 */
public data class WalletBridgeConfiguration(
    public val walletId: String = "default",
    public val persistence: WalletBridgePersistence = WalletBridgePersistence(),
    public val defaultKeyUseAuthorizationPolicy: WalletBridgeKeyUseAuthorizationPolicy =
        WalletBridgeKeyUseAuthorizationPolicy.BiometricCurrentSet,
    public val keyUseAuthorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
    public val signingIdentity: SigningIdentityConfiguration = SigningIdentityConfiguration(),
    public val issuance: WalletBridgeIssuanceConfiguration = WalletBridgeIssuanceConfiguration(),
    public val presentation: WalletBridgePresentationConfiguration = WalletBridgePresentationConfiguration(),
    public val registersDigitalCredentials: Boolean = true,
    public val crossProcessAccess: WalletAppGroupAccess? = null,
    public val preferredLocales: List<String> = platformPreferredLocales(),
)

internal fun WalletBridgeConfiguration.toWalletConfiguration(): WalletConfiguration = WalletConfiguration(
    walletId = walletId,
    persistence = persistence.toWalletPersistence(),
    keyAuthorization = WalletKeyAuthorization(
        policy = defaultKeyUseAuthorizationPolicy.toCorePolicy(),
        prompt = keyUseAuthorizationPrompt,
    ),
    signingIdentity = signingIdentity,
    issuance = issuance.toIssuanceConfiguration(),
    presentation = presentation.toPresentationConfiguration(),
    digitalCredentials = DigitalCredentialsConfiguration(
        registry = if (registersDigitalCredentials) null else UnavailableCredentialRegistry,
        crossProcessAccess = crossProcessAccess,
    ),
    preferredLocales = preferredLocales,
)
