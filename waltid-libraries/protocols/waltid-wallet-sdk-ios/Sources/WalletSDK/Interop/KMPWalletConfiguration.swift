import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension WalletConfiguration {
    func toKMPConfiguration() throws -> WalletBridgeConfiguration {
        guard !keyAuthorization.prompt.message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !keyAuthorization.prompt.cancelText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw WalletError.invalidInput("Key authorization prompt and cancellation text must be nonblank")
        }
        return try WalletBridgeConfiguration(
            walletId: walletID,
            persistence: persistence.toKMPPersistence(),
            databaseKeyProvider: persistence.toKMPDatabaseKeyProvider(),
            attestation: issuance.attestation?.toKMPAttestationConfiguration(),
            walletProvider: issuance.walletProvider?.toKMPWalletProvider(),
            issuerMetadataTrustResolver: issuance.metadataTrustResolver.map {
                KMPIssuerMetadataTrustResolverAdapter(resolver: $0)
            },
            receiptAuthoritiesJson: try issuance.receiptAuthorities.map { String(decoding: try JSONEncoder().encode($0.map(\.fields)), as: UTF8.self) },
            preferredLocales: preferredLocales,
            transactionDataProfiles: presentation.transactionDataProfiles.map { $0.toKMPTransactionDataProfile() },
            paymentCredentialIssuers: presentation.paymentCredentialIssuers.map { WalletBridgePaymentCredentialIssuer(issuer: $0.issuer, publicJwkJson: $0.publicJWKJSON, algorithm: $0.algorithm) },
            clientIdTrustConfiguration: presentation.verifierTrust.toKMPClientIDTrustConfiguration(),
            registersDigitalCredentials: registersDigitalCredentials,
            appGroupIdentifier: crossProcessAccess?.appGroupIdentifier,
            keychainAccessGroup: crossProcessAccess?.keychainAccessGroup,
            defaultKeyUseAuthorizationPolicy: keyAuthorization.policy.toKMPAuthorizationPolicy(),
            keyUseAuthorizationPrompt: Waltid_openid4vc_wallet_persistence_mobileKeyUseAuthorizationPrompt(
                reason: keyAuthorization.prompt.message,
                cancelText: keyAuthorization.prompt.cancelText
            ),
            signingIdentity: signingIdentity.toKMPSigningIdentityConfiguration()
        )
    }
}

final class KMPKeyAttestationProviderResolverAdapter: WalletBridgeKeyAttestationProviderResolver, @unchecked Sendable {
    init(resolver: any KeyAttestationProviderResolver) { self.resolver = resolver }

    func __resolve(credentialIssuer: String) async throws -> (any WalletBridgeKeyAttestationProvider)? {
        guard let provider = try await resolver.resolve(credentialIssuer: credentialIssuer) else { return nil }
        try Task.checkCancellation()
        return KMPKeyAttestationProviderAdapter(provider: provider)
    }

    private let resolver: any KeyAttestationProviderResolver
}

final class KMPKeyAttestationProviderAdapter: WalletBridgeKeyAttestationProvider, @unchecked Sendable {
    init(provider: any KeyAttestationProvider) {
        self.provider = provider
    }

    var verificationPublicJwk: String { provider.verificationPublicJWK }

    func __attest(request: WalletBridgeKeyAttestationRequest) async throws -> String {
        try await provider.attest(KeyAttestationRequest(
            credentialIssuer: request.credentialIssuer,
            proofKeyJWK: request.proofKeyJwk,
            nonce: request.nonce,
            requiredKeyStorage: request.requiredKeyStorage,
            requiredUserAuthentication: request.requiredUserAuthentication
        ))
    }

    private let provider: any KeyAttestationProvider
}

private final class KMPIssuerMetadataTrustResolverAdapter: WalletBridgeIssuerMetadataTrustResolver, @unchecked Sendable {
    init(resolver: any IssuerMetadataTrustResolver) {
        self.resolver = resolver
    }

    func __verify(compactJwt: String, expectedCredentialIssuer: String) async throws -> WalletBridgeIssuerMetadataSigner {
        let signer = try await resolver.verify(
            compactJWT: compactJwt,
            expectedCredentialIssuer: expectedCredentialIssuer
        )
        return WalletBridgeIssuerMetadataSigner(
            keyId: signer.keyID,
            algorithm: signer.algorithm,
            trustType: signer.trustType.toKMPTrustType()
        )
    }

    private let resolver: any IssuerMetadataTrustResolver
}

private extension MetadataTrustType {
    func toKMPTrustType() -> WalletBridgeIssuerMetadataSignerTrustType {
        switch self {
        case .trustedIssuer:
            return .trustedIssuer
        case .trustedDelegate:
            return .trustedDelegate
        }
    }
}

private extension WalletClientIDTrustConfiguration {
    func toKMPClientIDTrustConfiguration() -> WalletBridgeClientIdTrustConfiguration {
        WalletBridgeClientIdTrustConfiguration(
            x509TrustAnchorsPem: x509TrustAnchorsPEM,
            preRegisteredClientMetadataJson: preRegisteredClientMetadataJSON
        )
    }
}

private extension WalletTransactionDataProfile {
    func toKMPTransactionDataProfile() -> WalletCore.TransactionDataProfile {
        WalletCore.TransactionDataProfile(
            type: type,
            displayName: displayName,
            fields: fields
        )
    }
}
#endif
