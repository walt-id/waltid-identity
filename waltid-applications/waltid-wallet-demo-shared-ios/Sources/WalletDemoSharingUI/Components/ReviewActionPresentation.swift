import SwiftUI

/// The supported transport-specific presentations for the shared review actions.
public enum ReviewActionPresentation {
    case sharing
    case proximity

    var submitTitle: String {
        String(localized: "Share", bundle: .module)
    }

    var rejectTitle: String {
        self == .sharing ? String(localized: "Reject", bundle: .module) : String(localized: "Decline", bundle: .module)
    }

    var cancelTitle: String? {
        self == .sharing ? nil : String(localized: "Cancel", bundle: .module)
    }

    var submitAccessibilityIdentifier: String {
        self == .sharing
            ? WalletAccessibilityID.presentationSubmitButton
            : WalletAccessibilityID.proximityApproveButton
    }

    var rejectAccessibilityIdentifier: String {
        self == .sharing
            ? WalletAccessibilityID.presentationRejectButton
            : WalletAccessibilityID.proximityDeclineButton
    }

    var cancelAccessibilityIdentifier: String {
        self == .sharing
            ? WalletAccessibilityID.presentationCancelButton
            : WalletAccessibilityID.proximityCancelButton
    }
}
