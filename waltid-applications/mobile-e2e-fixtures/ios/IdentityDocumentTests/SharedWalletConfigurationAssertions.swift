import Foundation
import Security
import WalletDemoIdentityDocumentSupport
import WalletSDK
import XCTest

/// Asserts that a demo's shared wallet configuration puts state and key material where the provider
/// extension looks for them.
///
/// Scope, stated precisely: both wallets below are opened in the *test host process*, so this proves the
/// app-side half of the contract - the namespace resolves to this demo's App Group and Keychain group, a
/// second wallet built from that same namespace reopens the existing database and signing key instead of
/// creating new ones, the key really lands in the shared access group, and it can sign. It does not prove
/// that a *different* process is entitled to reach them: that depends on the built entitlements, which
/// `.github/scripts/mobile-ci/verify-ios-identity-document-provider.sh` asserts on the built products, and
/// finally on acceptance on a physical device, which no simulator can stand in for.
///
/// The second wallet is obtained from ``IdentityDocumentNamespace/providerWallet(walletID:)``, the
/// extension's own entry point, and every assertion about what the extension can see is made before
/// anything bootstraps it - that is what proves the extension does not need to.
///
/// Compiled into both demos' test targets from here so both check the same invariants: a per-demo copy
/// would let one demo's namespace regress unnoticed. The interesting failure is not "the wallet did not
/// open" but "the wallet opened and the signing key is unreachable from the extension", which otherwise
/// only shows up as a failed presentation on a device - hence the explicit access-group and signing
/// assertions.
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

    // The host app's configuration, built the way the app builds it. Only this wallet bootstraps,
    // because only the host app ever does.
    let hostConfiguration = try namespace.walletConfiguration(walletID: walletID)
    let hostWallet = try await Wallet(configuration: hostConfiguration)
    let hostBootstrap = try await hostWallet.bootstrap()
    let hostCredentials = try await hostWallet.credentials()

    // The provider's own entry point, not a re-creation of it: `providerWallet(walletID:)` is what the
    // extension calls, and it deliberately does not bootstrap. Any divergence between the two - App
    // Group, Keychain group, wallet id - fails from here on, and so would an Annex C read path that
    // secretly depended on bootstrap having run in this process.
    let providerWallet = try await namespace.providerWallet(walletID: walletID)
    let providerCredentials = try await providerWallet.credentials()
    XCTAssertEqual(
        providerCredentials.map(\.id).sorted(),
        hostCredentials.map(\.id).sorted(),
        "A wallet opened without bootstrap does not see the credentials the host app stored",
        file: file,
        line: line
    )

    // Resolving the signing key is the other half of what Annex C needs, and this is where it is
    // checked: the wallet resolves a persisted key by looking up exactly this Keychain item, so a key
    // that is present, in the shared group, and able to sign is a key the extension can device-sign
    // with. Asserted on the item rather than through a wallet API because the only wallet API that
    // forces key resolution is `bootstrap()`, which writes - see below.
    try assertSigningKeyIsUsableFromSharedAccessGroup(
        keyID: hostBootstrap.keyID,
        expectedAccessGroup: keychainAccessGroup,
        file: file,
        line: line
    )

    // Belt and braces on a throwaway instance, deliberately not on `providerWallet`: `bootstrap()` is
    // the wallet's own key-resolution path - on a wallet that already has a DID it creates nothing and
    // fails if the platform cannot load the persisted key - but it also republishes the desired
    // projection, which is why the extension must not call it. Keeping it off the provider wallet is
    // what leaves the assertions above proving that the extension's path needs no bootstrap.
    let bootstrapProbeWallet = try await Wallet(configuration: hostConfiguration)
    let probedResolution = try await bootstrapProbeWallet.bootstrap()
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
/// `"default"` opens an empty database and shows the user an empty credential picker rather than an
/// error. Apple gives the extension no way to derive the wallet from the request - the registration's
/// `documentIdentifier` is not part of `ISO18013MobileDocumentRequestContext` - so the published
/// projection is the only source, and this asserts the extension actually uses it.
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

    // Bootstrapping is what publishes the projection: it synchronizes the platform registry afterwards,
    // and the iOS registry writes the wallet id it was built with into the App Group.
    let hostWallet = try await Wallet(configuration: try namespace.walletConfiguration(walletID: walletID))
    _ = try await hostWallet.bootstrap()

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
/// Finding the item is not enough: the extension has to produce a device-authentication signature with
/// this key, so the test signs and verifies rather than only reading attributes.
private func assertSigningKeyIsUsableFromSharedAccessGroup(
    keyID: String,
    expectedAccessGroup: String,
    file: StaticString,
    line: UInt
) throws {
    // Signum's IosKeychainProvider keys the item by the wallet's alias in kSecAttrApplicationLabel
    // and its own constant tag in kSecAttrApplicationTag, so keyID is the only handle a test has.
    let query: [CFString: Any] = [
        kSecClass: kSecClassKey,
        kSecAttrKeyClass: kSecAttrKeyClassPrivate,
        kSecAttrApplicationLabel: keyID,
        kSecAttrApplicationTag: Data("supreme.privatekey".utf8),
        kSecReturnAttributes: true,
        kSecReturnRef: true,
    ]
    var result: CFTypeRef?
    XCTAssertEqual(
        SecItemCopyMatching(query as CFDictionary, &result),
        errSecSuccess,
        "The wallet signing key \(keyID) is not in the Keychain under the alias the wallet reported",
        file: file,
        line: line
    )
    let attributes = try XCTUnwrap(result as? [CFString: Any], file: file, line: line)
    XCTAssertEqual(
        attributes[kSecAttrAccessGroup] as? String,
        expectedAccessGroup,
        "The signing key landed outside the shared access group, so the extension cannot sign with it",
        file: file,
        line: line
    )

    let privateKey = try XCTUnwrap(attributes[kSecValueRef] as! SecKey?, file: file, line: line)
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
