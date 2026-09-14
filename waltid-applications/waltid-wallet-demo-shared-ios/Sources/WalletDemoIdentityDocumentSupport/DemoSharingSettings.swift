import Foundation

/// Demo UX preference for whether DC API / Identity Document presentations show the wallet preview.
///
/// Stored in the App Group so the host Settings toggle and the provider extension read the same
/// value. A missing key is on: that is the existing presentation path.
public enum DemoSharingSettings {
    public static let showDcApiPresentationPreviewKey =
        "id.walt.walletdemo.sharing.showDcApiPresentationPreview"

    public static func showDcApiPresentationPreview(appGroupIdentifier: String) -> Bool {
        let defaults = UserDefaults(suiteName: appGroupIdentifier)
        guard defaults?.object(forKey: showDcApiPresentationPreviewKey) != nil else {
            return true
        }
        return defaults?.bool(forKey: showDcApiPresentationPreviewKey) ?? true
    }

    public static func setShowDcApiPresentationPreview(
        _ enabled: Bool,
        appGroupIdentifier: String
    ) {
        UserDefaults(suiteName: appGroupIdentifier)?
            .set(enabled, forKey: showDcApiPresentationPreviewKey)
    }
}
