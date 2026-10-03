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
    @Environment(\.walletDemoBranding) private var branding
    @ObservedObject var viewModel: WalletViewModel
    let onOpenSettings: () -> Void
    let onBack: (() -> Void)?
    @ObservedObject private var readerTrustSettings: DemoReaderTrustSettingsController
    @ObservedObject private var proximityPresentation: ProximityPresentationViewModel
    @StateObject private var proximityScreenPolicy = ProximityScreenPolicy()
    @State private var credentialDetailsByID: [String: CredentialDetails] = [:]

    init(viewModel: WalletViewModel, onOpenSettings: @escaping () -> Void, onBack: (() -> Void)? = nil) {
        self.onOpenSettings = onOpenSettings
        self.onBack = onBack
        _viewModel = ObservedObject(wrappedValue: viewModel)
        _readerTrustSettings = ObservedObject(wrappedValue: viewModel.readerTrustSettings)
        _proximityPresentation = ObservedObject(wrappedValue: viewModel.proximityPresentation)
    }

    var body: some View {
        NavigationView {
            Group {
                if let review = viewModel.presentationSharingReview {
                    onlineReviewContent(review: review)
                } else if proximityPresentation.active {
                    proximityContent
                } else if viewModel.externalFlow != nil {
                    WalletExternalFlowStatus(viewModel: viewModel, onRetry: viewModel.previewPresentation)
                } else {
                    entryContent
                }
            }
            .navigationTitle(proximityPresentation.active ? "Share nearby" : "Share credentials")
            .navigationBarTitleDisplayMode(.inline)
            .walletFlowToolbar(onBack: onBack, backEnabled: viewModel.externalFlow != nil ? viewModel.canDismissExternalFlow : !viewModel.isLoading,
                onOpenSettings: viewModel.externalFlow == nil ? onOpenSettings : nil, external: viewModel.externalFlow != nil)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier(WalletAccessibilityID.presentTabContent)
        }
        .navigationViewStyle(.stack)
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
        .task(id: ProximityCredentialLoadKey(credentials: viewModel.credentials, active: proximityPresentation.active)) {
            await refreshProximityCredentialDetails()
        }
        .onAppear(perform: updateProximityScreenPolicy)
        .onDisappear { proximityScreenPolicy.restore() }
        .onChange(of: proximityPresentation.displayedEngagement == .qr) { _ in
            updateProximityScreenPolicy()
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
        guard proximityPresentation.active else { return }
        let credentials = viewModel.credentials
        let details = await CredentialDisplayNormalizer.details(for: credentials)
        guard !Task.isCancelled, proximityPresentation.active, viewModel.credentials == credentials else { return }
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

    private var entryContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                WalletTabStatusBanner(viewModel: viewModel, tab: .present)

                Text("Online presentation")
                    .font(.headline)

                ScannableUrlEditor(
                    title: "Present",
                    label: "OpenID4VP request URL",
                    text: $viewModel.presentationRequestUrl,
                    inputIdentifier: WalletAccessibilityID.presentationInput,
                    scanButtonIdentifier: WalletAccessibilityID.presentationScanButton,
                    isEnabled: viewModel.presentationUrlEntryEnabled,
                    focusResetKey: viewModel.inputFocusResetKey
                )

                Button("Preview") {
                    viewModel.previewPresentation()
                }
                .buttonStyle(.borderedProminent)
                .tint(branding.primary)
                .disabled(!viewModel.presentationPreviewActionEnabled)
                .accessibilityIdentifier(WalletAccessibilityID.presentButton)

                VStack(alignment: .leading, spacing: 10) {
                    Text("In-person presentation")
                        .font(.headline)
                    Text("Show a QR code or hold this iPhone near a compatible reader to present an mdoc.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Button("Present to nearby reader") {
                        proximityPresentation.start()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(branding.primary)
                    .disabled(
                        !viewModel.isReady || viewModel.isLoading || viewModel.credentials.isEmpty
                            || viewModel.presentationReview != nil || readerTrustSettings.loading
                    )
                    .accessibilityIdentifier(WalletAccessibilityID.proximityStartButton)
                }
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))

                if viewModel.credentials.isEmpty {
                    Text("No credentials available")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if let warning = viewModel.transactionDataProfilesWarning {
                    WarningBannerView(message: warning)
                }

                if let error = viewModel.presentationError {
                    PresentationErrorView(
                        error: error,
                        isEnabled: viewModel.presentationReviewEnabled,
                        onNotifyVerifier: viewModel.rejectPresentation,
                        onDismiss: viewModel.cancelPresentationReview
                    )
                }
            }
            .padding()
        }
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
                onCancel: viewModel.cancelPresentationReview,
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
                onCancel: viewModel.cancelPresentationReview,
                paymentReview: viewModel.paymentReview
            )
        }
    }

    private var proximityContent: some View {
        ProximityPresentationView(viewModel: proximityPresentation, approvalMode: $viewModel.proximityApprovalMode,
            credentialDetailsByID: credentialDetailsByID)
            .id(proximityPresentation.review?.reviewID)
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
