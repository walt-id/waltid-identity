import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct ProximityPresentationView: View {
    @ObservedObject var viewModel: ProximityPresentationViewModel
    @Binding var approvalMode: WalletDemoProximityApprovalMode
    var headerOwnsClose = false
    var onConnectionOptions: (() -> Void)? = nil
    let credentialDetailsByID: [String: CredentialDetails]

    var body: some View {
        Group {
            if viewModel.closing {
                WalletReviewScaffold { EmptyView() } actions: { ProgressView("Closing the secure connection…").frame(maxWidth: .infinity, alignment: .leading) }
            } else if viewModel.showsEngagement {
                presentationBody.padding(.horizontal).padding(.vertical, 8)
                    .safeAreaInset(edge: .bottom, spacing: 0) { WalletFooter { actions } }
            } else {
                WalletReviewScaffold(showsActions: (!headerOwnsClose && canCancel) || viewModel.review != nil || viewModel.isTerminal || viewModel.actionErrorMessage != nil) {
                    presentationBody
                } actions: { actions }
            }
        }
    }

    private var presentationBody: some View {
        Group {
            if viewModel.showsEngagement {
                ProximityEngagementContent(viewModel: viewModel, approvalMode: $approvalMode, credentialDetailsByID: credentialDetailsByID,
                    onConnectionOptions: onConnectionOptions)
            } else {
                VStack(alignment: .leading, spacing: 16) {
                    if let onConnectionOptions {
                        WalletSection { WalletNavigationRow("Connection options", symbol: "network", action: onConnectionOptions)
                            .accessibilityIdentifier("proximity-connection-options") }
                    }
                    if let sharing = pendingPreparedSharing {
                        ProximityPreparedSharingSummary(sharing: sharing, credentialDetailsByID: credentialDetailsByID)
                    }
                    content
                    if let route = viewModel.connectedRoute {
                        ProximityConnectionDetails(route: route)
                    }
                }
            }
        }
        .accessibilityIdentifier(WalletAccessibilityID.proximityScreen)
    }

    private var canCancel: Bool {
        !viewModel.closing && !viewModel.isTerminal && (viewModel.sessionState == nil || viewModel.sessionState?.legalActions.contains(.cancel) == true)
    }

    @ViewBuilder private var actions: some View {
            if let message = viewModel.actionErrorMessage {
                StatusBannerView(message: message, isLoading: false, isError: true)
            }
            if viewModel.preparingApproval {
                let expired = viewModel.recentPlan?.isExpired == true
                WalletActions(
                    primary: WalletAction(expired ? String(localized: "Get a new request") : String(localized: "Approve and get ready"),
                        enabled: expired || viewModel.canApprove, identifier: WalletAccessibilityID.proximityApproveButton) {
                            if expired { viewModel.restart() } else { viewModel.approve() }
                        },
                    secondary: headerOwnsClose ? nil : WalletAction(String(localized: "Cancel"), identifier: WalletAccessibilityID.proximityCancelButton,
                        perform: viewModel.requestClose)
                )
            } else if viewModel.review != nil {
                ReviewActions(
                    selectionComplete: viewModel.canApprove,
                    isLoading: viewModel.pendingReviewID != nil,
                    onSubmit: { viewModel.approve() },
                    onReject: viewModel.decline,
                    onCancel: viewModel.requestClose,
                    presentation: .proximity, showCancelWithReject: !headerOwnsClose
                )
            } else if canCancel && !headerOwnsClose {
                WalletActions(secondary: WalletAction("Cancel", identifier: WalletAccessibilityID.proximityCancelButton,
                    perform: viewModel.requestClose))
            } else if viewModel.isTerminal {
                ProximityOutcomeActions(viewModel: viewModel)
            }
    }

    private var pendingPreparedSharing: ProximityPreparedSharing? {
        switch viewModel.sessionState {
        case .checkingPrerequisites, .preparing, .engagementReady, .connecting, .awaitingRequest:
            return viewModel.preparedSharing
        default: return nil
        }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.startupFailed {
            ProximityFailureContent(
                message: String(localized: "The in-person presentation could not be started")
            )
        } else if let state = viewModel.sessionState {
            switch state {
            case .checkingPrerequisites(let capabilities):
                if capabilities.mayStart && !capabilities.remediationActions.contains(.requestBluetoothPermission) {
                    ProximityProgressContent(message: String(localized: "Preparing a secure presentation…"))
                } else {
                    ProximityPrerequisiteContent(
                        capabilities: capabilities,
                        actionInProgress: viewModel.hostActionInProgress,
                        onRetry: viewModel.retryPrerequisites,
                        onContinueWithAvailableConnection: viewModel.continueWithAvailableConnection,
                        onRemediate: viewModel.remediate
                    )
                }
            case .preparing:
                ProximityProgressContent(message: String(localized: "Preparing a secure presentation…"))
            case .engagementReady:
                ProximityEngagementContent(viewModel: viewModel, approvalMode: $approvalMode, credentialDetailsByID: credentialDetailsByID,
                    onConnectionOptions: onConnectionOptions)
            case .connecting:
                ProximityProgressContent(message: String(localized: "Connecting to reader…"))
            case .awaitingRequest:
                ProximityProgressContent(
                    message: String(localized: "Connected. Waiting for the reader's request…")
                )
            case .reviewRequired(let review, let reason):
                if reason == .preparedSharingChanged {
                    Text("The reader or request has changed. Nothing was shared using your earlier approval. Review these new details.")
                        .foregroundStyle(.red)
                }
                ProximityReviewContent(
                    review: review,
                    selections: viewModel.selections,
                    credentialDetailsByID: credentialDetailsByID,
                    onSelectCredential: viewModel.selectCredential,
                    onToggleElement: viewModel.toggleElement,
                    continueAfterResponse: viewModel.continueAfterResponse,
                    onContinueAfterResponseChange: viewModel.setContinueAfterResponse,
                    allowContinuation: true
                )
            case .preparationRequired(let plan, let reason):
                preparationContent(plan: plan, reason: reason)
            case .authorizingHolderKey:
                ProximityProgressContent(message: String(localized: "Confirming the selected credentials…"))
            case .sendingResponse:
                ProximityProgressContent(message: String(localized: "Sharing the approved data…"))
            case .awaitingNextRequest:
                ProximityProgressContent(
                    message: String(localized: "Response sent. Waiting for another request…")
                )
            case .terminating:
                ProximityProgressContent(message: String(localized: "Closing the secure connection…"))
            case .completed(_, let declined, let receipt):
                ProximityTerminalContent(
                    title: declined
                        ? String(localized: "Request declined")
                        : String(localized: "Presentation complete"),
                    message: declined
                        ? String(localized: "No credential data was shared for this request.")
                        : String(localized: "The approved credential data was sent to the reader."),
                    receipt: receipt, credentialDetailsByID: credentialDetailsByID
                )
                .walletSuccessDismissal(key: 0,
                    enabled: !declined && receipt != nil && viewModel.actionErrorMessage == nil
                        && viewModel.hostActionInProgress == nil, onDone: viewModel.requestClose)
            case .noData:
                ProximityTerminalContent(
                    title: String(localized: "No data shared"),
                    message: String(localized: "No credential data was shared for this request.")
                )
            case .cancelled:
                ProximityTerminalContent(
                    title: String(localized: "Presentation cancelled"),
                    message: String(localized: "The nearby presentation was closed.")
                )
            case .failed(let error):
                ProximityFailureContent(message: error.message)
            }
        } else {
            ProximityProgressContent(message: String(localized: "Checking this device…"))
        }
    }

    @ViewBuilder
    private func preparationContent(plan: ProximitySharingPlan, reason: ProximityReviewReason) -> some View {
        Text("Review before reconnecting").font(.title2.bold()).accessibilityAddTraits(.isHeader)
        Text("No data has been shared. Choose what to share with this reader, then approve once before reconnecting.")
        if reason == .preparedSharingChanged {
            Text("The reader or request has changed. Your earlier approval was not used.").foregroundStyle(.red)
        }
        ProximityReviewContent(
            review: plan.review,
            selections: viewModel.selections,
            credentialDetailsByID: credentialDetailsByID,
            onSelectCredential: viewModel.selectCredential,
            onToggleElement: viewModel.toggleElement,
            continueAfterResponse: false,
            onContinueAfterResponseChange: viewModel.setContinueAfterResponse,
            allowContinuation: false
        )
        MetadataDisclosure(title: "Reader certificate identity", initiallyExpanded: false) {
            Text(plan.readerCertificateSHA256).font(.caption).textSelection(.enabled)
        }
    }

}
