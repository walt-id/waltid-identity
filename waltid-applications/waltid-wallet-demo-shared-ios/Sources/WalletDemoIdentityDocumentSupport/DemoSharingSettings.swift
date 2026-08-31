import Foundation
import WalletSDK

/// Stable demo choices for one immutable proximity-presentation session.
public enum WalletDemoProximityTransportProfile: String, CaseIterable, Identifiable, Sendable {
    case defaultProfile = "default"
    case provisionalNfcV2Hybrid = "provisional_nfc_v2_hybrid"
    case provisionalNfcV2Direct = "provisional_nfc_v2_direct"

    public var id: String { rawValue }

    /// Resolves this persisted demo choice to the same typed SDK configuration as the Compose app.
    public var configuration: ProximityPresentationConfiguration {
        switch self {
        case .defaultProfile:
            return ProximityPresentationConfiguration(
                engagement: .qrAndNFC(.negotiatedHandover),
                retrieval: .conventional(.init(nfc: .init()))
            )
        case .provisionalNfcV2Hybrid:
            return ProximityPresentationConfiguration(
                engagement: .nfcOnly(.provisionalV2()),
                retrieval: .provisionalNFCV2(
                    .init(
                        bluetoothLowEnergy: .init(
                            roles: .centralClient,
                            bearerPolicy: .gattOnly
                        )
                    )
                )
            )
        case .provisionalNfcV2Direct:
            return ProximityPresentationConfiguration(
                engagement: .nfcOnly(.provisionalV2()),
                retrieval: .provisionalNFCV2()
            )
        }
    }
}

/// Demo UX preference for whether DC API / Identity Document presentations show the wallet preview.
///
/// Stored in the App Group so the host Settings toggle and the provider extension read the same
/// value. A missing key is on: that is the existing presentation path.
public enum DemoSharingSettings {
    public static let showDcApiPresentationPreviewKey =
        "id.walt.walletdemo.sharing.showDcApiPresentationPreview"
    public static let proximityTransportProfileKey =
        "id.walt.walletdemo.sharing.proximityTransportProfile"

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

    public static func proximityTransportProfile(
        appGroupIdentifier: String
    ) -> WalletDemoProximityTransportProfile {
        let rawValue = UserDefaults(suiteName: appGroupIdentifier)?
            .string(forKey: proximityTransportProfileKey)
        return rawValue.flatMap(WalletDemoProximityTransportProfile.init(rawValue:))
            ?? .defaultProfile
    }

    public static func setProximityTransportProfile(
        _ profile: WalletDemoProximityTransportProfile,
        appGroupIdentifier: String
    ) {
        UserDefaults(suiteName: appGroupIdentifier)?
            .set(profile.rawValue, forKey: proximityTransportProfileKey)
    }
}
