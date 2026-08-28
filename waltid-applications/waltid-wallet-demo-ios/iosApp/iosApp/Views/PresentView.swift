import SwiftUI
import WalletDemoSharingUI
import WebKit
import WalletSDK

struct PresentView: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.walletDemoBranding) private var branding
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        NavigationView {
            Group {
                if let review = viewModel.presentationSharingReview {
                    reviewContent(review: review)
                } else {
                    entryContent
                }
            }
            .navigationTitle("Present")
            .walletSettingsToolbar(viewModel: viewModel)
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
        .fullScreenCover(
            isPresented: Binding(
                get: { viewModel.proximityPresentation.active },
                set: { if !$0 { viewModel.proximityPresentation.dismiss() } }
            )
        ) {
            ProximityPresentationView(viewModel: viewModel.proximityPresentation)
        }
    }

    private var entryContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("In-person presentation")
                        .font(.headline)
                    Text("Show a device engagement QR code and present an mdoc to a nearby reader over Bluetooth.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Button("Present to nearby reader") {
                        viewModel.proximityPresentation.start()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(branding.primary)
                    .disabled(
                        !viewModel.isReady || viewModel.isLoading || viewModel.credentials.isEmpty
                            || viewModel.presentationReview != nil
                    )
                    .accessibilityIdentifier(WalletAccessibilityID.proximityStartButton)
                }
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))

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

                if viewModel.credentials.isEmpty {
                    Text("No credentials available")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                WalletTabStatusBanner(viewModel: viewModel, tab: .present)

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

    private func reviewContent(review: SharingReviewModel) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
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
                    onSubmit: viewModel.submitPresentation,
                    onReject: viewModel.rejectPresentation,
                    onCancel: viewModel.cancelPresentationReview,
                    compact: false,
                    showActions: false
                )
            }
            .padding()
        }
        .safeAreaInset(edge: .bottom) {
            ReviewActions(
                selectionComplete: viewModel.presentationCredentialSelectionComplete,
                isLoading: !viewModel.presentationReviewEnabled,
                onSubmit: viewModel.submitPresentation,
                onReject: viewModel.rejectPresentation,
                onCancel: viewModel.cancelPresentationReview
            )
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.bar)
        }
    }
}

private struct PresentationErrorView: View {
    @Environment(\.walletDemoBranding) private var branding
    let error: PresentationPreviewError
    let isEnabled: Bool
    let onNotifyVerifier: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("This request cannot be completed")
                .font(.headline)
            SharingRequestSections(request: error.request.sharingRequest())
            Text(error.message)
            Text("OpenID4VP error: \(error.code.rawValue)")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("You can notify the verifier or dismiss the request without sending a response.")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack {
                Button("Notify verifier", action: onNotifyVerifier)
                    .buttonStyle(.borderedProminent)
                    .tint(branding.primary)
                    .disabled(!isEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.presentationErrorNotifyButton)

                Button("Dismiss", action: onDismiss)
                    .buttonStyle(.bordered)
                    .disabled(!isEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.presentationErrorDismissButton)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(WalletAccessibilityID.presentationError)
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
