import SwiftUI
import WalletDemoSharingUI
import WebKit
import WalletSDK

enum ProximityPresentationLifecyclePolicy {
    static func shouldInterrupt(for phase: ScenePhase) -> Bool {
        phase == .background
    }
}

struct PresentView: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @ObservedObject var viewModel: WalletViewModel
    let onBack: (() -> Void)?
    let nearbySheet: Bool
    @ObservedObject private var proximityPresentation: ProximityPresentationViewModel
    @StateObject private var proximityScreenPolicy = ProximityScreenPolicy()
    @State private var showingConnectionOptions = false
    @State private var credentialDetailsByID: [String: CredentialDetails] = [:]

    init(viewModel: WalletViewModel, onBack: (() -> Void)? = nil, nearbySheet: Bool = false) {
        self.onBack = onBack
        self.nearbySheet = nearbySheet
        _viewModel = ObservedObject(wrappedValue: viewModel)
        _proximityPresentation = ObservedObject(wrappedValue: viewModel.proximityPresentation)
    }

    var body: some View {
        WalletNavigationContainer {
            Group {
                if nearbySheet { proximityContent }
                else if viewModel.presentationCompleted { resultContent }
                else if let review = viewModel.presentationSharingReview {
                    onlineReviewContent(review: review)
                } else if proximityPresentation.active {
                    proximityContent
                } else if viewModel.pendingPresentationContinuationURL != nil || viewModel.pendingPresentationFormPostHTML != nil {
                    WalletReviewScaffold(showsActions: false) { ProgressView("Finishing the response…") } actions: {}
                } else {
                    WalletRequestStatus(viewModel: viewModel, tab: .present,
                        onRetry: viewModel.previewPresentation, onClose: closeReview)
                }
            }
            .navigationTitle(nearbySheet || proximityPresentation.active ? "Share nearby" : viewModel.presentationCompleted ? "Sharing result" : "Share credentials")
            .navigationBarTitleDisplayMode(.inline)
            .walletDetailDestination(isPresented: $showingConnectionOptions) {
                ConnectionSettingsView(viewModel: viewModel)
                    .walletFlowToolbar(onBack: proximityPresentation.requestClose, backEnabled: proximityPresentation.canClose,
                        closing: proximityPresentation.closing || !proximityPresentation.active)
            }
            .walletFlowToolbar(onBack: nearbySheet || proximityPresentation.active ? proximityPresentation.requestClose : onBack,
                backEnabled: nearbySheet || proximityPresentation.active ? proximityPresentation.canClose : viewModel.externalFlow != nil ? viewModel.canDismissExternalFlow : !viewModel.isLoading,
                external: viewModel.externalFlow != nil,
                closing: proximityPresentation.closing || nearbySheet && !proximityPresentation.active)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier(WalletAccessibilityID.presentTabContent)
        }
        .walletDetailDismissal(perform: nearbySheet || proximityPresentation.active ? proximityPresentation.requestClose : onBack,
            enabled: nearbySheet || proximityPresentation.active ? proximityPresentation.canClose : viewModel.externalFlow != nil ? viewModel.canDismissExternalFlow : !viewModel.isLoading,
            identifier: viewModel.externalFlow != nil ? "wallet.external.close" : "wallet.flowBack")
        .id(viewModel.presentationNavigationResetKey)
        .onChange(of: viewModel.pendingPresentationContinuationURL) { url in
            guard let url else { return }
            openURL(url) { accepted in
                if accepted {
                    viewModel.completePresentationContinuation()
                } else {
                    viewModel.failPresentationContinuation("No application can open the verifier response")
                }
            }
        }
        .background {
            if let html = viewModel.pendingPresentationFormPostHTML {
                PresentationFormPostWebView(
                    html: html,
                    onCompleted: viewModel.completePresentationContinuation,
                    onFailed: viewModel.failPresentationContinuation
                )
                .frame(width: 1, height: 1)
                .opacity(0.01)
                .accessibilityHidden(true)
            }
        }
        .task(id: ProximityCredentialLoadKey(credentials: viewModel.credentials, active: nearbySheet || proximityPresentation.active)) {
            await refreshProximityCredentialDetails()
        }
        .onAppear(perform: updateProximityScreenPolicy)
        .onDisappear { proximityScreenPolicy.restore() }
        .onChange(of: proximityPresentation.displayedEngagement == .qr) { _ in
            updateProximityScreenPolicy()
        }
        .onChange(of: proximityPresentation.canChangeConnectionOptions) { allowed in
            if !allowed && proximityPresentation.active && !proximityPresentation.closing { showingConnectionOptions = false }
        }
        .onChange(of: proximityPresentation.active) { _ in
            updateProximityScreenPolicy()
        }
        .onChange(of: proximityPresentation.isTerminal) { _ in
            updateProximityScreenPolicy()
        }
        .onChange(of: proximityPresentation.preparingApproval) { _ in
            updateProximityScreenPolicy()
        }
        .onChange(of: viewModel.selectedTab) { selectedTab in
            updateProximityScreenPolicy()
            if selectedTab != .present && proximityPresentation.active {
                proximityPresentation.cancel()
            }
        }
        .onChange(of: scenePhase) { phase in
            updateProximityScreenPolicy()
            if ProximityPresentationLifecyclePolicy.shouldInterrupt(for: phase) {
                proximityPresentation.handleLifecycleInterruption()
            }
        }
    }

    private func refreshProximityCredentialDetails() async {
        credentialDetailsByID = [:]
        guard nearbySheet || proximityPresentation.active else { return }
        let credentials = viewModel.credentials
        let details = await CredentialDisplayNormalizer.details(for: credentials)
        guard !Task.isCancelled, nearbySheet || proximityPresentation.active, viewModel.credentials == credentials else { return }
        credentialDetailsByID = Dictionary(details.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private func updateProximityScreenPolicy() {
        let foreground = scenePhase == .active
        let qrVisible = foreground && viewModel.selectedTab == .present
            && proximityPresentation.displayedEngagement == .qr
        proximityScreenPolicy.update(
            active: foreground && proximityPresentation.active && (!proximityPresentation.isTerminal || proximityPresentation.preparingApproval),
            qrVisible: qrVisible
        )
    }

    private func closeReview() {
        if let onBack { onBack() }
        else { viewModel.cancelPresentationReview(); viewModel.selectedTab = .credentials }
    }

    private var resultContent: some View {
        WalletReviewScaffold {
            Text(viewModel.statusMessage(for: .present)).font(.body)
                .foregroundStyle(viewModel.statusIsError(for: .present) ? Color.red : Color.primary)
        } actions: {
            WalletActions(primary: WalletAction("Done", identifier: "wallet.presentationDone", perform: closeReview))
        }.accessibilityIdentifier("wallet.presentationResult")
            .walletSuccessDismissal(key: viewModel.presentationNavigationResetKey,
                enabled: !viewModel.isLoading && !viewModel.statusIsError(for: .present), onDone: closeReview)
    }

    private func onlineReviewContent(review: SharingReviewModel) -> some View {
        let reviewedSelection = viewModel.presentationSharingSelection
        let reviewedPayment = viewModel.paymentReview
        let submit = {
            guard review == viewModel.presentationSharingReview,
                  reviewedSelection == viewModel.presentationSharingSelection,
                  reviewedPayment == viewModel.paymentReview else { return }
            viewModel.submitPresentation()
        }
        return WalletReviewScaffold(showsActions: true) {
            WalletTabStatusBanner(viewModel: viewModel, tab: .present)
            if let warning = viewModel.transactionDataProfilesWarning {
                WarningBannerView(message: warning)
            }

            SharingReviewView(
                review: review,
                selection: viewModel.presentationSharingSelection,
                selectionComplete: viewModel.presentationCredentialSelectionComplete,
                isLoading: !viewModel.presentationReviewEnabled,
                isReadOnly: false,
                onToggleCredential: viewModel.togglePresentationCredential,
                onToggleDisclosure: viewModel.togglePresentationDisclosure,
                onSubmit: submit,
                onReject: viewModel.rejectPresentation,
                onCancel: closeReview,
                compact: false,
                showActions: false,
                paymentReview: viewModel.paymentReview
            )
        } actions: {
            ReviewActions(
                selectionComplete: viewModel.presentationCredentialSelectionComplete,
                isLoading: !viewModel.presentationReviewEnabled,
                onSubmit: submit,
                onReject: viewModel.rejectPresentation,
                onCancel: closeReview,
                paymentReview: viewModel.paymentReview,
                showCancelWithReject: false
            )
        }
    }

    private var proximityContent: some View {
        ProximityPresentationView(viewModel: proximityPresentation, approvalMode: $viewModel.proximityApprovalMode,
            headerOwnsClose: true,
            onConnectionOptions: proximityPresentation.showsConnectionOptions
                ? { showingConnectionOptions = true } : nil,
            credentialDetailsByID: credentialDetailsByID)
            .id(proximityPresentation.review?.reviewID)
            .disabled(proximityPresentation.closing || !proximityPresentation.active)
            .allowsHitTesting(proximityPresentation.active && !proximityPresentation.closing)
    }

}

private struct PresentationFormPostWebView: UIViewRepresentable {
    let html: String
    let onCompleted: () -> Void
    let onFailed: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onCompleted: onCompleted, onFailed: onFailed)
    }

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.navigationDelegate = context.coordinator
        webView.loadHTMLString(html, baseURL: nil)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {}

    static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        webView.stopLoading()
        webView.navigationDelegate = nil
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        private let onCompleted: () -> Void
        private let onFailed: (String) -> Void
        private var submittedNavigation: WKNavigation?
        private var finished = false

        init(onCompleted: @escaping () -> Void, onFailed: @escaping (String) -> Void) {
            self.onCompleted = onCompleted
            self.onFailed = onFailed
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            if let url = webView.url,
               url.absoluteString != "about:blank",
               url.scheme != "data" {
                submittedNavigation = navigation
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard let submittedNavigation, submittedNavigation === navigation, !finished else { return }
            finished = true
            onCompleted()
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            fail(error)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            guard (error as NSError).code != NSURLErrorCancelled else { return }
            fail(error)
        }

        private func fail(_ error: Error) {
            guard !finished else { return }
            finished = true
            onFailed(error.localizedDescription)
        }
    }
}

private struct ProximityCredentialLoadKey: Equatable {
    let credentials: [Credential]
    let active: Bool
}
