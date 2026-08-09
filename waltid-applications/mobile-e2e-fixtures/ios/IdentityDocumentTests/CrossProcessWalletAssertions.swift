import Foundation
import Security
import WalletDemoIdentityDocumentSupport
import WalletSDK
import XCTest

/// Asserts that a host app and its document-provider extension really do share one wallet.
///
/// Compiled into both demos' test targets from here so both check the same invariants: a per-demo
/// copy would let one demo's namespace regress unnoticed. The interesting failure is not "the wallet did not open" but
/// "the wallet opened and the signing key is unreachable from the extension", which only shows up as
/// a failed presentation on a device - hence the explicit access-group and signing assertions.
///
/// - Parameters:
///   - namespace: The demo namespace under test; supplies the App Group and Keychain group.
///   - walletID: Wallet identifier, shared by both processes.
///   - file: Call-site file, for failure attribution.
///   - line: Call-site line, for failure attribution.
public func assertWalletIsSharedAcrossProcesses(
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

    // The host app's configuration, built the way the app builds it.
    let hostConfiguration = try namespace.walletConfiguration(walletID: walletID)
    let hostWallet = try await Wallet(configuration: hostConfiguration)
    let hostBootstrap = try await hostWallet.bootstrap()
    let hostCredentials = try await hostWallet.credentials()

    // The extension's configuration, rebuilt from the namespace exactly as the provider does. Any
    // divergence between the two - App Group, Keychain group, wallet ID - fails from here on.
    let extensionConfiguration = try namespace.walletConfiguration(walletID: walletID)
    let extensionWallet = try await Wallet(configuration: extensionConfiguration)
    let extensionBootstrap = try await extensionWallet.bootstrap()
    let extensionCredentials = try await extensionWallet.credentials()

    XCTAssertEqual(
        extensionBootstrap.did,
        hostBootstrap.did,
        "The provider extension opened a different wallet database than the host app",
        file: file,
        line: line
    )
    XCTAssertEqual(
        extensionBootstrap.keyID,
        hostBootstrap.keyID,
        "The provider extension resolved a different signing key than the host app",
        file: file,
        line: line
    )
    XCTAssertEqual(
        extensionCredentials.map(\.id).sorted(),
        hostCredentials.map(\.id).sorted(),
        "The provider extension does not see the host app's credentials",
        file: file,
        line: line
    )

    try assertSigningKeyIsUsableFromSharedAccessGroup(
        keyID: hostBootstrap.keyID,
        expectedAccessGroup: keychainAccessGroup,
        file: file,
        line: line
    )

    try await extensionWallet.deleteLocalData()
}

/// Asserts the wallet's signing key is in the shared access group and can actually sign.
///
/// Reachability alone is not enough: the extension has to produce a device-authentication signature
/// with this key, so the test signs and verifies rather than only reading attributes.
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
    let message = Data("cross-process device authentication".utf8)
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
