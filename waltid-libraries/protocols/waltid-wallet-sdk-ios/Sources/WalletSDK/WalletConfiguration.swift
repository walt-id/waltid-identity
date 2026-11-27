import Foundation

/// Configuration captured when a wallet opens.
public struct WalletConfiguration: Sendable {
    /// Stable local wallet identifier used by the underlying store.
    public var walletID: String

    /// Wallet-local storage and encryption.
    public var persistence: WalletPersistence

    /// Default key protection and signing prompt.
    public var keyAuthorization: KeyAuthorization

    /// Signing identity lifecycle and optional recovery integrations.
    public var signingIdentity: SigningIdentityConfiguration

    /// Issuer metadata trust and attestation integrations.
    public var issuance: Issuance

    /// Verifier authentication, transaction profiles and trusted payment issuers.
    public var presentation: Presentation

    /// Shared app container and Keychain settings for a provider extension.
    public var crossProcessAccess: WalletCrossProcessAccess?

    /// Publishes eligible credentials to the platform registry when available. Defaults to true.
    public var registersDigitalCredentials: Bool

    /// Ordered BCP 47 languages. Defaults to the device preferences. An empty array skips preferred-language lookup.
    public var preferredLocales: [String]

    /// Creates the wallet configuration.
    /// - Parameters:
    ///   - walletID: Stable local wallet identifier used by the underlying store.
    ///   - persistence: Wallet-local storage and encryption.
    ///   - keyAuthorization: Default key protection and signing prompt.
    ///   - signingIdentity: Signing identity lifecycle and optional recovery integrations.
    ///   - issuance: Issuer metadata trust and attestation integrations.
    ///   - presentation: Verifier authentication, transaction profiles and trusted payment issuers.
    ///   - crossProcessAccess: Shared app container and Keychain settings for a provider extension.
    ///   - registersDigitalCredentials: Whether this wallet publishes eligible credentials to the platform.
    ///   - preferredLocales: Ordered BCP 47 languages. Omit to capture the device's current preferences or pass an empty array to skip preferred-language lookup.
    public init(
        walletID: String = "default",
        persistence: WalletPersistence = .init(),
        keyAuthorization: KeyAuthorization = .init(),
        signingIdentity: SigningIdentityConfiguration = .init(),
        issuance: Issuance = .init(),
        presentation: Presentation = .init(),
        crossProcessAccess: WalletCrossProcessAccess? = nil,
        registersDigitalCredentials: Bool = true,
        preferredLocales: [String] = Locale.preferredLanguages
    ) {
        self.walletID = walletID
        self.persistence = persistence
        self.keyAuthorization = keyAuthorization
        self.signingIdentity = signingIdentity
        self.issuance = issuance
        self.presentation = presentation
        self.crossProcessAccess = crossProcessAccess
        self.registersDigitalCredentials = registersDigitalCredentials
        self.preferredLocales = preferredLocales
    }

    /// Protection for wallet signing keys.
    public struct KeyAuthorization: Sendable {
        /// Default authorization for newly created wallet signing keys.
        public var policy: WalletKeyUseAuthorizationPolicy

        /// Text presented for protected signing operations.
        public var prompt: WalletKeyUseAuthorizationPrompt

        /// Creates these settings.
        /// - Parameters:
        ///   - policy: Default authorization for newly created wallet signing keys.
        ///   - prompt: Text presented for protected signing operations.
        public init(
            policy: WalletKeyUseAuthorizationPolicy = .biometricCurrentSet,
            prompt: WalletKeyUseAuthorizationPrompt = .init()
        ) {
            self.policy = policy
            self.prompt = prompt
        }
    }

    /// Issuer trust and attestation services.
    public struct Issuance: Sendable {
        /// Optional client-attestation service.
        public var attestation: WalletAttestationConfiguration?

        /// Enterprise service supplying wallet-instance or holder-key attestations.
        public var walletProvider: WalletProviderConfiguration?

        /// Verifier for signed Credential Issuer Metadata. The wallet requests signed metadata only when configured.
        public var metadataTrustResolver: (any IssuerMetadataTrustResolver)?
        /// Optional receipt authorities. An empty configured list denies all credential receipts.
        public var receiptAuthorities: [CredentialReceiptAuthority]?

        /// Runtime attester for issuers requiring holder-key attestation. Supply it again when reopening the wallet.
        public var keyAttestationProvider: (any KeyAttestationProviderResolver)?

        /// Creates these settings.
        /// - Parameters:
        ///   - attestation: Optional client-attestation service.
        ///   - walletProvider: Enterprise service supplying wallet-instance or holder-key attestations.
        ///   - metadataTrustResolver: Verifier for signed Credential Issuer Metadata. The wallet requests signed metadata only when configured.
        ///   - receiptAuthorities: Independently provisioned issuer verification keys. Nil uses ordinary receipt.
        ///   - keyAttestationProvider: Runtime attester for issuers requiring holder-key attestation. Supply it again when reopening the wallet.
        public init(
            attestation: WalletAttestationConfiguration? = nil,
            walletProvider: WalletProviderConfiguration? = nil,
            metadataTrustResolver: (any IssuerMetadataTrustResolver)? = nil,
            receiptAuthorities: [CredentialReceiptAuthority]? = nil,
            keyAttestationProvider: (any KeyAttestationProviderResolver)? = nil
        ) {
            self.attestation = attestation
            self.walletProvider = walletProvider
            self.metadataTrustResolver = metadataTrustResolver
            self.receiptAuthorities = receiptAuthorities
            self.keyAttestationProvider = keyAttestationProvider
        }
    }

    /// OpenID4VP rules captured when the wallet opens.
    public struct Presentation: Sendable, Equatable {
        /// Host-controlled anchors and pre-registered verifier metadata.
        public let verifierTrust: WalletClientIDTrustConfiguration
        /// Accepted transaction profiles. Each type and each field within a profile must be unique.
        public let transactionDataProfiles: [WalletTransactionDataProfile]
        /// Independent issuer verification keys for payment consent.
        public let paymentCredentialIssuers: [WalletPaymentCredentialIssuer]

        /// Creates the presentation section. The wallet validates it before opening storage.
        /// - Parameters:
        ///   - verifierTrust: Trust used to authenticate verifier Request Objects.
        ///   - transactionDataProfiles: Transaction data types the host can explain to the user.
        ///   - paymentCredentialIssuers: Independent keys for strict payment consent.
        public init(
            verifierTrust: WalletClientIDTrustConfiguration = .init(),
            transactionDataProfiles: [WalletTransactionDataProfile] = [],
            paymentCredentialIssuers: [WalletPaymentCredentialIssuer] = []
        ) {
            self.verifierTrust = verifierTrust
            self.transactionDataProfiles = transactionDataProfiles
            self.paymentCredentialIssuers = paymentCredentialIssuers
        }
    }
}
