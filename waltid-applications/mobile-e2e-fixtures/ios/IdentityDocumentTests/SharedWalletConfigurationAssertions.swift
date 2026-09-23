import Foundation
import Security
import WalletDemoIdentityDocumentSupport
import WalletSDK
import XCTest

/// Asserts a demo's namespace names the App Group and Keychain group its two bundles are entitled to.
///
/// The identifiers are spelled out at the call site rather than derived, because the failure this
/// catches is a namespace renamed on one side only: the host keeps working, and the extension opens an
/// empty container. What the *entitlements* grant is asserted on the built products by
/// `.github/scripts/mobile-ci/verify-ios-identity-document-provider.sh`; this pins the runtime half.
///
/// - Parameters:
///   - namespace: The demo namespace under test.
///   - appGroupIdentifier: The App Group the demo's two bundles are entitled to.
///   - keychainAccessGroupSuffix: The shared Keychain group, without the `AppIdentifierPrefix`.
///   - file: Call-site file, for failure attribution.
///   - line: Call-site line, for failure attribution.
public func assertNamespaceMatchesTheEntitledIdentifiers(
    namespace: IdentityDocumentNamespace,
    appGroupIdentifier: String,
    keychainAccessGroupSuffix: String,
    file: StaticString = #filePath,
    line: UInt = #line
) throws {
    XCTAssertEqual(
        namespace.appGroupIdentifier,
        appGroupIdentifier,
        "The namespace opens a different App Group than the one both bundles are entitled to",
        file: file,
        line: line
    )
    XCTAssertEqual(
        namespace.keychainAccessGroupSuffix,
        keychainAccessGroupSuffix,
        "The namespace names a different shared Keychain group than the entitlements grant",
        file: file,
        line: line
    )

    // The running bundle's expanded group has to be the same one, or the wallet writes its signing key
    // where the extension cannot read it.
    let resolved = try XCTUnwrap(
        namespace.keychainAccessGroup,
        "The test host must carry \(IdentityDocumentNamespace.keychainAccessGroupInfoKey)",
        file: file,
        line: line
    )
    XCTAssertTrue(
        resolved.hasSuffix(".\(keychainAccessGroupSuffix)"),
        "The build resolved the Keychain group to \(resolved), which is not this demo's shared group",
        file: file,
        line: line
    )
    XCTAssertNotEqual(
        resolved,
        keychainAccessGroupSuffix,
        "AppIdentifierPrefix was not expanded, so this is not a group the entitlement grants",
        file: file,
        line: line
    )
}

/// Asserts that a demo's shared wallet configuration puts state and key material where the provider
/// extension looks for them.
///
/// Both wallets are opened in the *test host process*, so this covers the app-side half of the contract
/// only. Whether another process is entitled to reach them is decided by the built entitlements, which
/// `.github/scripts/mobile-ci/verify-ios-identity-document-provider.sh` asserts, and finally by
/// acceptance on a physical device.
///
/// - Parameters:
///   - namespace: The demo namespace under test; supplies the App Group and Keychain group.
///   - walletID: Wallet identifier, used for both opens.
///   - file: Call-site file, for failure attribution.
///   - line: Call-site line, for failure attribution.
public func assertWalletReopensSharedStateAndSigningKey(
    namespace: IdentityDocumentNamespace,
    walletID: String,
    file: StaticString = #filePath,
    line: UInt = #line
) async throws {
    let keychainAccessGroup = try XCTUnwrap(
        namespace.keychainAccessGroup,
        "The test host must carry WALTKeychainAccessGroup, or app and extension cannot share Keychain items",
        file: file,
        line: line
    )
    XCTAssertTrue(
        keychainAccessGroup.hasSuffix(".\(namespace.keychainAccessGroupSuffix)"),
        "The resolved Keychain group \(keychainAccessGroup) does not belong to this demo's namespace",
        file: file,
        line: line
    )

    // Only the host initializes the signing key; the provider reopens established state.
    var hostConfiguration = try namespace.walletConfiguration(walletID: walletID)
    // Simulators cannot create Secure Enclave keys protected by biometryCurrentSet. These
    // assertions cover shared-storage wiring; protected extension signing remains device coverage.
    hostConfiguration.defaultKeyUseAuthorizationPolicy = .none
    let hostWallet = try await Wallet(configuration: hostConfiguration)
    let hostBootstrap = try await initializeSigningIdentity(hostWallet)
    let hostCredentials = try await hostWallet.credentials()

    // `providerWallet(walletID:)` is the extension's own entry point, and does not bootstrap.
    let providerWallet = try await namespace.providerWallet(walletID: walletID)
    let providerCredentials = try await providerWallet.credentials()
    XCTAssertEqual(
        providerCredentials.map(\.id).sorted(),
        hostCredentials.map(\.id).sorted(),
        "A wallet opened without bootstrap does not see the credentials the host app stored",
        file: file,
        line: line
    )

    // Asserted on the Keychain item rather than through a wallet API, because the only wallet API that
    // forces key resolution is `identities.initialize()`, which writes.
    try assertSigningKeyIsUsableFromSharedAccessGroup(
        publicJWK: hostBootstrap.publicJWK,
        expectedAccessGroup: keychainAccessGroup,
        file: file,
        line: line
    )

    // On a wallet that already has a DID, `identities.initialize()` creates nothing and fails if the platform cannot
    // load the persisted key. Run on a throwaway instance rather than on `providerWallet`, which must
    // stay un-bootstrapped for the assertions above to mean anything.
    let bootstrapProbeWallet = try await Wallet(configuration: hostConfiguration)
    let probedResolution = try await initializeSigningIdentity(bootstrapProbeWallet)
    XCTAssertEqual(
        probedResolution.keyID,
        hostBootstrap.keyID,
        "Reopening the wallet resolved a different signing key instead of the persisted one",
        file: file,
        line: line
    )
    XCTAssertEqual(
        probedResolution.did,
        hostBootstrap.did,
        "Reopening the wallet did not find the persisted DID state",
        file: file,
        line: line
    )

    try await providerWallet.deleteLocalData()
}

/// Asserts the provider extension opens the wallet the host published, not a hard-coded `"default"`.
///
/// The regression this pins down is invisible in the default configuration: a host launched with
/// `WALLET_ID=test-123` stores its credentials in `wallet_test-123`, so an extension that assumes
/// `"default"` opens an empty database and shows an empty credential picker rather than an error.
///
/// - Parameters:
///   - namespace: The demo namespace under test; supplies the App Group the projection is published to.
///   - walletID: A deliberately non-default wallet identifier.
///   - file: Call-site file, for failure attribution.
///   - line: Call-site line, for failure attribution.
public func assertProviderResolvesThePublishedWalletID(
    namespace: IdentityDocumentNamespace,
    walletID: String,
    file: StaticString = #filePath,
    line: UInt = #line
) async throws {
    XCTAssertNotEqual(walletID, "default", "This assertion is meaningless against the default wallet id", file: file, line: line)

    // Initializing the signing key publishes the projection: it synchronizes the platform registry afterwards,
    // and the iOS registry writes the wallet id it was built with into the App Group.
    var hostConfiguration = try namespace.walletConfiguration(walletID: walletID)
    // This assertion covers publishing the selected wallet identifier, not biometric enforcement.
    hostConfiguration.defaultKeyUseAuthorizationPolicy = .none
    let hostWallet = try await Wallet(configuration: hostConfiguration)
    _ = try await initializeSigningIdentity(hostWallet)

    XCTAssertEqual(
        try namespace.activeWalletID(),
        walletID,
        "The projection in \(namespace.appGroupIdentifier) does not name the wallet the host opened",
        file: file,
        line: line
    )

    // No walletID argument, exactly as the extension calls it.
    let providerWallet = try await namespace.providerWallet()
    let resolvedWalletID = await providerWallet.configuration.walletID
    XCTAssertEqual(
        resolvedWalletID,
        walletID,
        "The provider extension would open wallet_\(resolvedWalletID) while the host uses wallet_\(walletID)",
        file: file,
        line: line
    )

    try await providerWallet.deleteLocalData()
}

/// Asserts the wallet's signing key is in the shared access group and can actually sign.
///
/// Signs and verifies rather than only reading attributes, because the extension has to produce a
/// device-authentication signature with this key.
private func assertSigningKeyIsUsableFromSharedAccessGroup(
    publicJWK: String,
    expectedAccessGroup: String,
    file: StaticString,
    line: UInt
) throws {
    // Match the public key, not an adapter's private alias/tag convention. The wallet's logical
    // key ID is stable across recovery, while each native entry has its own alias.
    let jwk = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(publicJWK.utf8)) as? [String: String])
    XCTAssertEqual(jwk["kty"], "EC", file: file, line: line)
    XCTAssertEqual(jwk["crv"], "P-256", file: file, line: line)
    func coordinate(_ name: String) throws -> Data {
        let value = try XCTUnwrap(jwk[name], file: file, line: line)
            .replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        let data = try XCTUnwrap(Data(base64Encoded: value + String(repeating: "=", count: (4 - value.count % 4) % 4)), file: file, line: line)
        XCTAssertEqual(data.count, 32, file: file, line: line)
        return data
    }
    let expectedPublicKey = try Data([0x04]) + coordinate("x") + coordinate("y")
    let query: [CFString: Any] = [
        kSecClass: kSecClassKey,
        kSecAttrKeyClass: kSecAttrKeyClassPrivate,
        kSecAttrKeyType: kSecAttrKeyTypeECSECPrimeRandom,
        kSecAttrAccessGroup: expectedAccessGroup,
        kSecUseDataProtectionKeychain: true,
        kSecMatchLimit: kSecMatchLimitAll,
        kSecReturnRef: true,
    ]
    var result: CFTypeRef?
    XCTAssertEqual(
        SecItemCopyMatching(query as CFDictionary, &result),
        errSecSuccess,
        "No private signing key is accessible in the shared Keychain group",
        file: file,
        line: line
    )
    let keys = try XCTUnwrap(result as? [SecKey], file: file, line: line)
    let matchingKeys = keys.filter { key in
        guard let publicKey = SecKeyCopyPublicKey(key),
              let bytes = SecKeyCopyExternalRepresentation(publicKey, nil) as Data? else { return false }
        return bytes == expectedPublicKey
    }
    XCTAssertEqual(matchingKeys.count, 1, "The wallet's public key must identify one shared signing key", file: file, line: line)
    let privateKey = try XCTUnwrap(matchingKeys.first, file: file, line: line)
    let publicKey = try XCTUnwrap(
        SecKeyCopyPublicKey(privateKey),
        "The shared signing key has no public half",
        file: file,
        line: line
    )
    let algorithm = SecKeyAlgorithm.ecdsaSignatureMessageX962SHA256
    let message = Data("shared access group device authentication".utf8)
    var signingError: Unmanaged<CFError>?
    let signature = try XCTUnwrap(
        SecKeyCreateSignature(privateKey, algorithm, message as CFData, &signingError) as Data?,
        "Signing with the shared key failed: \(String(describing: signingError?.takeRetainedValue()))",
        file: file,
        line: line
    )
    XCTAssertTrue(
        SecKeyVerifySignature(publicKey, algorithm, message as CFData, signature as CFData, nil),
        "A signature made with the shared signing key did not verify against its own public key",
        file: file,
        line: line
    )
}

private func initializeSigningIdentity(_ wallet: Wallet) async throws -> SigningIdentity {
    guard case .active(let identity) = try await wallet.signingIdentity.initialize() else {
        throw WalletError.invalidInput("Expected an active test identity")
    }
    return identity
}
