package id.walt.wallet2.mobile

import id.walt.wallet2.mobile.credentials.WalletPersistence
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialsConfiguration
import id.walt.wallet2.mobile.identity.WalletKeyAuthorization
import id.walt.wallet2.mobile.identity.SigningIdentityConfiguration
import id.walt.wallet2.mobile.issuance.IssuanceConfiguration
import id.walt.wallet2.mobile.presentation.PresentationConfiguration

/**
 * Configuration captured when a [Wallet] opens.
 *
 * @property walletId Stable identifier used for database naming and persisted wallet state.
 * @property persistence Storage and encryption for wallet-local state.
 * @property keyAuthorization Default protection for new keys and prompt for protected operations.
 * @param signingIdentity Signing identity settings and the host's recovery and custody registrations.
 * @property issuance Issuer metadata trust and attestation integrations.
 * @property presentation Verifier authentication, transaction profiles and trusted payment issuers.
 * @property digitalCredentials OS registry, shared storage and reader trust integrations.
 * @param preferredLocales Ordered BCP 47 languages captured from the device by default. An empty list
 * skips preferred-language lookup.
 * @property onEvent Inline suspending hook invoked after broadcasting each event. Its latency and failures
 * participate in the operation. Use [Wallet.events] for independent UI observation.
 */
public class WalletConfiguration(
    public val walletId: String = "default",
    public val persistence: WalletPersistence = WalletPersistence(),
    public val keyAuthorization: WalletKeyAuthorization = WalletKeyAuthorization(),
    signingIdentity: SigningIdentityConfiguration = SigningIdentityConfiguration(),
    public val issuance: IssuanceConfiguration = IssuanceConfiguration(),
    public val presentation: PresentationConfiguration = PresentationConfiguration(),
    public val digitalCredentials: DigitalCredentialsConfiguration = DigitalCredentialsConfiguration(),
    preferredLocales: List<String> = platformPreferredLocales(),
    public val onEvent: suspend (WalletEvent) -> Unit = {},
) {
    /** Captured registrations. The host continues to own each provider and custodian. */
    public val signingIdentity: SigningIdentityConfiguration = signingIdentity.copy(
        recoveryProviders = buildList { addAll(signingIdentity.recoveryProviders) },
        keyCustodians = buildList { addAll(signingIdentity.keyCustodians) },
        alternativeAuthorizations = buildList { addAll(signingIdentity.alternativeAuthorizations) },
    )

    /**
     * Ordered BCP 47 preferences, defaulting to the device languages. An explicit empty list skips
     * localized lookup. Display metadata can fall back to the first entry. Payment consent requires
     * a complete catalogue in a preferred exact or base language.
     */
    public val preferredLocales: List<String> = buildList { addAll(preferredLocales) }

    init {
        require(walletId.isNotBlank()) { "Wallet ID cannot be blank" }
    }

    /** Derives configuration with the same validation and collection ownership as construction. */
    public fun copy(
        walletId: String = this.walletId,
        persistence: WalletPersistence = this.persistence,
        keyAuthorization: WalletKeyAuthorization = this.keyAuthorization,
        signingIdentity: SigningIdentityConfiguration = this.signingIdentity,
        issuance: IssuanceConfiguration = this.issuance,
        presentation: PresentationConfiguration = this.presentation,
        digitalCredentials: DigitalCredentialsConfiguration = this.digitalCredentials,
        preferredLocales: List<String> = this.preferredLocales,
        onEvent: suspend (WalletEvent) -> Unit = this.onEvent,
    ): WalletConfiguration = WalletConfiguration(
        walletId, persistence, keyAuthorization, signingIdentity, issuance, presentation,
        digitalCredentials, preferredLocales, onEvent,
    )
}

internal expect fun platformPreferredLocales(): List<String>
