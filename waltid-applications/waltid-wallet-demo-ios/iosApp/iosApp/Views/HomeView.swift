import CodeScanner
import SwiftUI
import WalletSDK

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var selectedDetailsID: String?

    private var credentialDetails: [CredentialDetails] {
        viewModel.credentials.map(CredentialDisplayNormalizer.details(for:))
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Your credentials")
                            .font(.largeTitle.bold())
                        Text("Keep credentials ready to receive and share when you choose.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    HStack(spacing: 12) {
                        WalletActionButton(
                            title: "Receive",
                            subtitle: "Add a credential",
                            systemImage: "tray.and.arrow.down.fill",
                            accessibilityIdentifier: WalletAccessibilityID.homeReceiveButton,
                            action: viewModel.startReceiveCapture
                        )
                        WalletActionButton(
                            title: "Present",
                            subtitle: "Share information",
                            systemImage: "square.and.arrow.up.fill",
                            accessibilityIdentifier: WalletAccessibilityID.homePresentButton,
                            action: viewModel.startPresentCapture
                        )
                    }
                    .disabled(!viewModel.isReady)

                    StatusBannerView(
                        message: viewModel.statusMessage(for: .credentials),
                        isLoading: viewModel.statusIsLoading(for: .credentials),
                        isError: viewModel.statusIsError(for: .credentials)
                    )

                    if let warning = viewModel.transactionDataProfilesWarning {
                        WarningBannerView(message: warning)
                    }

                    if credentialDetails.isEmpty {
                        EmptyCredentialsView()
                    } else {
                        ForEach(credentialDetails) { details in
                            CredentialCardButton(details: details) {
                                selectedDetailsID = details.id
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Wallet")
            .navigationBarTitleDisplayMode(.inline)
            .background(detailsNavigationLink)
            .accessibilityIdentifier(WalletAccessibilityID.walletHome)
        }
        .navigationViewStyle(.stack)
        .sheet(
            isPresented: Binding(
                get: { viewModel.interactionSheetPresented },
                set: { isPresented in
                    if !isPresented && viewModel.interactionSheetPresented {
                        viewModel.cancelInteraction()
                    }
                }
            )
        ) {
            WalletInteractionSheet(
                viewModel: viewModel,
                onCredentialSelected: { detailsID in
                    viewModel.finishInteraction()
                    selectedDetailsID = detailsID
                }
            )
            .interactiveDismissDisabled(viewModel.isLoading)
            .walletInteractionPresentation()
            .alert(
                "Replace current request?",
                isPresented: Binding(
                    get: { viewModel.replacementRequest != nil },
                    set: { if !$0 { viewModel.keepCurrentRequest() } }
                )
            ) {
                Button("Keep current", role: .cancel, action: viewModel.keepCurrentRequest)
                Button("Replace", role: .destructive, action: viewModel.replaceCurrentRequest)
            } message: {
                Text("Replacing it will cancel the current interaction locally. Nothing will be sent to the current issuer or verifier.")
            }
        }
    }

    private var detailsNavigationLink: some View {
        NavigationLink(
            destination: Group {
                if let selectedDetailsID {
                    CredentialDetailsDestination(detailsID: selectedDetailsID, details: credentialDetails)
                }
            },
            isActive: Binding(
                get: { selectedDetailsID != nil },
                set: { if !$0 { selectedDetailsID = nil } }
            )
        ) { EmptyView() }
        .hidden()
    }
}

private extension View {
    @ViewBuilder
    func walletInteractionPresentation() -> some View {
        if #available(iOS 16.0, *) {
            presentationDetents([.fraction(0.82), .large])
                .presentationDragIndicator(.visible)
        } else {
            self
        }
    }
}

private struct WalletActionButton: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let accessibilityIdentifier: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 10) {
                Image(systemName: systemImage)
                    .font(.title2)
                Text(title)
                    .font(.headline)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, minHeight: 112, alignment: .leading)
            .padding(14)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityIdentifier)
    }
}

private struct WalletInteractionSheet: View {
    @ObservedObject var viewModel: WalletViewModel
    let onCredentialSelected: (String) -> Void
    @State private var selectedReviewDetailsID: String?

    private var reviewCredentialDetails: [CredentialDetails] {
        let stored = viewModel.credentials.map(CredentialDisplayNormalizer.details(for:))
        let requested = viewModel.presentationPreview?.credentialOptions.map(CredentialDisplayNormalizer.details(for:)) ?? []
        var seen = Set<String>()
        return (stored + requested).filter { seen.insert($0.id).inserted }
    }

    var body: some View {
        NavigationView {
            Group {
                if case .success(_, let outcome, let message) = viewModel.interactionState {
                    WalletSuccessView(
                        outcome: outcome,
                        message: message,
                        receivedCredentialID: viewModel.lastReceivedCredentialIDs.first,
                        onDone: viewModel.finishInteraction,
                        onViewCredential: onCredentialSelected
                    )
                } else if let preview = viewModel.offerPreview {
                    AddCredentialSheet(viewModel: viewModel, preview: preview)
                } else if let preview = viewModel.presentationPreview {
                    ShareInformationSheet(
                        viewModel: viewModel,
                        preview: preview,
                        onCredentialSelected: { selectedReviewDetailsID = $0 }
                    )
                } else {
                    RequestCaptureSheet(viewModel: viewModel)
                }
            }
            .background(reviewDetailsNavigationLink)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: viewModel.cancelInteraction)
                        .disabled(viewModel.isLoading)
                }
            }
        }
        .navigationViewStyle(.stack)
    }

    private var reviewDetailsNavigationLink: some View {
        NavigationLink(
            destination: Group {
                if let selectedReviewDetailsID {
                    CredentialDetailsDestination(
                        detailsID: selectedReviewDetailsID,
                        details: reviewCredentialDetails
                    )
                }
            },
            isActive: Binding(
                get: { selectedReviewDetailsID != nil },
                set: { if !$0 { selectedReviewDetailsID = nil } }
            )
        ) { EmptyView() }
        .hidden()
    }
}

private struct RequestCaptureSheet: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var scannerError: String?

    private var kind: WalletInteractionKind {
        viewModel.interactionState.kind ?? .receive
    }

    private var manualText: Binding<String> {
        kind == .receive ? $viewModel.offerUrl : $viewModel.presentationRequestUrl
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text(kind == .receive ? "Receive a credential" : "Share information")
                    .font(.title2.bold())
                    .accessibilityIdentifier(WalletAccessibilityID.requestCaptureSheet)
                Text(kind == .receive
                    ? "Scan the credential offer QR code."
                    : "Scan the request QR code from the service asking for information.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            captureContent
            #if DEBUG
            if let rawURL = ProcessInfo.processInfo.environment["E2E_INCOMING_REQUEST_URL"],
               let url = URL(string: rawURL) {
                Button("Inject request") {
                    viewModel.handleDeepLink(url)
                }
                .accessibilityIdentifier("wallet.e2eIncomingRequest")
            }
            #endif
            Spacer(minLength: 0)
        }
        .padding()
        .navigationTitle(kind == .receive ? "Receive" : "Present")
        .alert(
            "QR scanner unavailable",
            isPresented: Binding(
                get: { scannerError != nil },
                set: { if !$0 { scannerError = nil } }
            )
        ) {
            Button("Enter link manually") {
                scannerError = nil
                viewModel.showManualEntry()
            }
        } message: {
            Text(scannerError ?? "Check camera access and try again.")
        }
    }

    @ViewBuilder
    private var captureContent: some View {
        switch viewModel.interactionState {
        case .capturing(_, let mode, let error):
            if mode == .scanner {
                CodeScannerView(
                    codeTypes: [.qr],
                    scanMode: .once,
                    showViewfinder: true,
                    requiresPhotoOutput: false
                ) { result in
                    switch result {
                    case .success(let scan):
                        viewModel.submitCapturedRequest(scan.string, source: .qr)
                    case .failure:
                        scannerError = "Camera access is unavailable. You can still paste the request link."
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 300, maxHeight: 380)
                .clipShape(RoundedRectangle(cornerRadius: 16))

                Button("Enter link manually", action: viewModel.showManualEntry)
                    .buttonStyle(.borderless)
                    .accessibilityIdentifier(WalletAccessibilityID.manualEntry)
            } else {
                TextEditor(text: manualText)
                    .font(.footnote.monospaced())
                    .frame(minHeight: 140, maxHeight: 220)
                    .padding(8)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(.separator)))
                    .accessibilityIdentifier(kind == .receive ? WalletAccessibilityID.offerInput : WalletAccessibilityID.presentationInput)

                Button("Continue") {
                    viewModel.submitCapturedRequest(manualText.wrappedValue, source: .manual)
                }
                .buttonStyle(.borderedProminent)
                .tint(.waltBlue)
                .disabled(manualText.wrappedValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .accessibilityIdentifier(kind == .receive ? WalletAccessibilityID.receiveButton : WalletAccessibilityID.presentButton)

                Button("Scan QR instead", action: viewModel.showScanner)
                    .buttonStyle(.borderless)
            }
            if let error {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
            }

        case .validating, .resolving:
            HStack(spacing: 12) {
                ProgressView()
                Text("Checking request…")
            }

        case .wrongRequestType(let expected, let request):
            Text(expected == .receive
                ? "This QR code is a presentation request, not a credential offer."
                : "This QR code is a credential offer, not a presentation request.")
                .foregroundStyle(.orange)
            Button(request.kind == .receive ? "Switch to Receive" : "Switch to Present") {
                viewModel.switchToDetectedRequest()
            }
            .buttonStyle(.borderedProminent)
            Button("Scan a different code", action: viewModel.showScanner)
                .buttonStyle(.bordered)

        case .failure(_, let message):
            Label(message, systemImage: "exclamationmark.triangle.fill")
                .foregroundStyle(.red)
            Button("Try again", action: viewModel.retryInteraction)
                .buttonStyle(.borderedProminent)

        default:
            EmptyView()
        }
    }
}

private struct AddCredentialSheet: View {
    @ObservedObject var viewModel: WalletViewModel
    let preview: OfferResolution

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Add this credential?")
                    .font(.title2.bold())
                Text("Review who is offering it and what it contains.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                StatusBannerView(
                    message: viewModel.statusMessage,
                    isLoading: viewModel.isLoading,
                    isError: viewModel.isError
                )
                OfferReviewView(
                    preview: preview,
                    isAcceptEnabled: viewModel.acceptOfferEnabled,
                    isReviewEnabled: viewModel.offerReviewEnabled,
                    txCode: viewModel.txCode,
                    onTxCodeChange: viewModel.updateTxCode,
                    onAccept: viewModel.acceptOffer,
                    onDecline: viewModel.declineOffer,
                    showsActions: false
                )
            }
            .padding()
        }
        .navigationTitle("Add credential")
        .safeAreaInset(edge: .bottom) {
            OfferReviewActionsView(
                isAcceptEnabled: viewModel.acceptOfferEnabled,
                isReviewEnabled: viewModel.offerReviewEnabled,
                onAccept: viewModel.acceptOffer,
                onDecline: viewModel.declineOffer
            )
            .padding()
            .background(.ultraThinMaterial)
        }
    }
}

private struct ShareInformationSheet: View {
    @ObservedObject var viewModel: WalletViewModel
    let preview: PresentationPreview
    let onCredentialSelected: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Share this information?")
                    .font(.title2.bold())
                Text("The verifier details below come from the request. Review them before sharing.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                StatusBannerView(
                    message: viewModel.statusMessage,
                    isLoading: viewModel.isLoading,
                    isError: viewModel.isError
                )
                PresentationReviewView(
                    preview: preview,
                    selectedCredentialOptions: viewModel.selectedPresentationCredentialOptions,
                    selectedDisclosureOptions: viewModel.selectedPresentationDisclosureOptions,
                    selectionComplete: viewModel.presentationCredentialSelectionComplete,
                    isLoading: !viewModel.presentationReviewEnabled,
                    isReadOnly: false,
                    onToggleCredential: viewModel.togglePresentationCredential,
                    onToggleDisclosure: viewModel.togglePresentationDisclosure,
                    onCredentialSelected: onCredentialSelected,
                    onSubmit: viewModel.submitPresentation,
                    onReject: viewModel.rejectPresentation,
                    onCancel: viewModel.cancelPresentationReview,
                    showsActions: false
                )
            }
            .padding()
        }
        .navigationTitle("Share information")
        .safeAreaInset(edge: .bottom) {
            PresentationReviewActionsView(
                selectionComplete: viewModel.presentationCredentialSelectionComplete,
                isLoading: !viewModel.presentationReviewEnabled,
                onSubmit: viewModel.submitPresentation,
                onReject: viewModel.rejectPresentation,
                onCancel: viewModel.cancelPresentationReview
            )
            .padding()
            .background(.ultraThinMaterial)
        }
    }
}

private struct WalletSuccessView: View {
    let outcome: WalletInteractionSuccessOutcome
    let message: String
    let receivedCredentialID: String?
    let onDone: () -> Void
    let onViewCredential: (String) -> Void

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.green)
            Text(successTitle)
                .font(.title2.bold())
            Text(message)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .accessibilityIdentifier(WalletAccessibilityID.status)
            if outcome == .credentialAdded, let receivedCredentialID {
                Button("View credential") { onViewCredential(receivedCredentialID) }
                    .buttonStyle(.borderedProminent)
            }
            Button("Done", action: onDone)
                .buttonStyle(.bordered)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var successTitle: String {
        switch outcome {
        case .credentialAdded: return "Credential added"
        case .informationShared: return "Information shared"
        case .offerDeclined: return "Offer declined"
        case .presentationRejected: return "Request rejected"
        }
    }
}
