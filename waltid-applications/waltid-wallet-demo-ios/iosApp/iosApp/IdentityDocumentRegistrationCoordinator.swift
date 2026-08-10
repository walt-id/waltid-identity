import Foundation
import WalletDemoIdentityDocumentSupport
import WalletSDK

/// This demo's cross-process namespace, under the name the app and its tests already use.
enum IdentityDocumentSharedConfiguration {
    static let namespace = IdentityDocumentNamespace.nativeDemo
    static let appGroupIdentifier = namespace.appGroupIdentifier
    static let keychainAccessGroupSuffix = namespace.keychainAccessGroupSuffix
    static let supportedDocumentTypes = namespace.supportedDocumentTypes

    static var keychainAccessGroup: String? { namespace.keychainAccessGroup }
}

/// Reconciliation entry point for this demo, so call sites do not each construct a coordinator.
@available(iOS 26.0, *)
enum DemoIdentityDocumentRegistration {
    /// Applies the wallet's desired registrations to Apple's store, propagating failures.
    static func update() async throws {
        try await coordinator.reconcile()
    }

    /// Same reconciliation for Apple's registration-update callback, which cannot report an error.
    static func updateFromPlatformCallback() async {
        await coordinator.reconcileFromPlatformCallback()
    }

    private static let coordinator = IdentityDocumentRegistrationCoordinator(
        namespace: IdentityDocumentSharedConfiguration.namespace
    )
}
