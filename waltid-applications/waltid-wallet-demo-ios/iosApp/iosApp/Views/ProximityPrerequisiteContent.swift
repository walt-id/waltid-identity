import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct ProximityPrerequisiteContent: View {
    let capabilities: ProximityCapabilities
    let actionInProgress: ProximityRemediationAction?
    let onRetry: () -> Void
    let onContinueWithAvailableConnection: () -> Void
    let onRemediate: (ProximityRemediationAction) -> Void

    var body: some View {
        ReviewMetadataSection(
            title: primaryAction?.label ?? String(localized: "Action needed")
        ) {
            Text(message)
            if actionInProgress != nil { ProgressView() }
            WalletActions(
                primary: WalletAction(primaryAction?.label ?? String(localized: "Check again"),
                    enabled: actionInProgress == nil,
                    identifier: primaryAction == nil ? WalletAccessibilityID.proximityRetryButton : nil) {
                        if let primaryAction { onRemediate(primaryAction) } else { onRetry() }
                    },
                secondary: capabilities.mayStart ? WalletAction(String(localized: "Continue with available connection"),
                    enabled: actionInProgress == nil, perform: onContinueWithAvailableConnection) : nil
            )
        }
    }

    private var primaryAction: ProximityRemediationAction? {
        capabilities.remediationActions.first { $0 != .useSupportedDevice }
    }

    private var message: String {
        let selected = [capabilities.nfcEngagement, capabilities.qrEngagement, capabilities.bluetoothLowEnergy,
            capabilities.nfcRetrieval, capabilities.nfcV2Retrieval, capabilities.wifiAwareRetrieval]
        if let primaryAction, let error = selected.first(where: {
            $0.selected && $0.remediationActions.contains(primaryAction)
        })?.unavailable { return error.message }
        return capabilities.selectedUnavailableMessage ?? String(localized: "Nearby presentation is not available yet.")
    }
}

private extension ProximityCapabilities {
    var selectedUnavailableMessage: String? {
        [
            nfcEngagement,
            bluetoothLowEnergy,
            nfcRetrieval,
            nfcV2Retrieval,
            qrEngagement,
            wifiAwareRetrieval,
        ].first { $0.selected && $0.unavailable != nil }?.unavailable?.message
    }
}
