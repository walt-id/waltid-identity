import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct ProximityPresentationView: View {
    @ObservedObject var viewModel: ProximityPresentationViewModel
    @Binding var approvalMode: WalletDemoProximityApprovalMode
    let credentialDetailsByID: [String: CredentialDetails]

    var body: some View {
        Group {
            if viewModel.showsEngagement {
                presentationBody.padding(.horizontal).padding(.vertical, 8)
                    .safeAreaInset(edge: .bottom) {
                        actions.padding().frame(maxWidth: .infinity, alignment: .trailing).background(.bar)
                    }
            } else {
                WalletReviewScaffold(showsActions: canCancel || viewModel.review != nil || viewModel.isTerminal) {
                    presentationBody
                } actions: { actions }
            }
        }
    }

    private var presentationBody: some View {
        Group {
            if viewModel.showsEngagement {
                ProximityEngagementContent(viewModel: viewModel, approvalMode: $approvalMode, credentialDetailsByID: credentialDetailsByID)
            } else {
                VStack(alignment: .leading, spacing: 16) {
                    if let message = viewModel.actionErrorMessage {
                        StatusBannerView(message: String(localized: "Action failed: \(message)"), isLoading: false, isError: true)
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
        !viewModel.isTerminal && (viewModel.sessionState == nil || viewModel.sessionState?.legalActions.contains(.cancel) == true)
    }

    @ViewBuilder private var actions: some View {

            if viewModel.preparingApproval {
                let expired = viewModel.recentPlan?.isExpired == true
                WalletActions(
                    primary: WalletAction(expired ? String(localized: "Get a new request") : String(localized: "Approve and get ready"),
                        enabled: expired || viewModel.canApprove, identifier: WalletAccessibilityID.proximityApproveButton) {
                            if expired { viewModel.restart() } else { viewModel.approve() }
                        },
                    secondary: WalletAction(String(localized: "Cancel"), identifier: WalletAccessibilityID.proximityCancelButton,
                        perform: viewModel.cancel)
                )
            } else if viewModel.review != nil {
                ReviewActions(
                    selectionComplete: viewModel.canApprove,
                    isLoading: viewModel.pendingReviewID != nil,
                    onSubmit: { viewModel.approve() },
                    onReject: viewModel.decline,
                    onCancel: viewModel.cancel,
                    presentation: .proximity
                )
            } else if canCancel {
                Button("Cancel", action: viewModel.cancel)
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .accessibilityIdentifier(WalletAccessibilityID.proximityCancelButton)
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
                ProximityEngagementContent(viewModel: viewModel, approvalMode: $approvalMode, credentialDetailsByID: credentialDetailsByID)
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
