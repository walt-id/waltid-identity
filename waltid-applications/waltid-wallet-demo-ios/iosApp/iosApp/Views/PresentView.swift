import SwiftUI
import WebKit
import WalletSDK

struct PresentView: View {
    @Environment(\.openURL) private var openURL
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?

    private var presentationDetails: [CredentialDetails] {
        viewModel.presentationPreview?.credentialOptions.map(CredentialDisplayNormalizer.details(for:)) ?? []
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
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
                    .tint(.waltBlue)
                    .disabled(!viewModel.presentationPreviewActionEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.presentButton)

                    if viewModel.credentials.isEmpty {
                        Text("No credentials available")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    StatusBannerView(
                        message: viewModel.statusMessage(for: .present),
                        isLoading: viewModel.statusIsLoading(for: .present),
                        isError: viewModel.statusIsError(for: .present)
                    )

                    if let warning = viewModel.transactionDataProfilesWarning {
                        WarningBannerView(message: warning)
                    }

                    if viewModel.presentationCompleted {
                        Button("New presentation", action: viewModel.startNewPresentationFlow)
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier(WalletAccessibilityID.presentationNewButton)
                    }

                    if let preview = viewModel.presentationPreview {
                        PresentationReviewView(
                            preview: preview,
                            selectedCredentialOptions: viewModel.selectedPresentationCredentialOptions,
                            selectedDisclosureOptions: viewModel.selectedPresentationDisclosureOptions,
                            selectionComplete: viewModel.presentationCredentialSelectionComplete,
                            isLoading: !viewModel.presentationReviewEnabled,
                            isReadOnly: viewModel.presentationCompleted,
                            onToggleCredential: viewModel.togglePresentationCredential,
                            onToggleDisclosure: viewModel.togglePresentationDisclosure,
                            onCredentialSelected: { detailsID in selectedDetailsID = detailsID },
                            onSubmit: viewModel.submitPresentation,
                            onReject: viewModel.rejectPresentation
                        )
                    }

                    if let error = viewModel.presentationError {
                        PresentationErrorView(
                            error: error,
                            isEnabled: viewModel.presentationReviewEnabled,
                            onNotifyVerifier: viewModel.rejectPresentation,
                            onDismiss: viewModel.startNewPresentationFlow
                        )
                    }
                }
                .padding()
            }
            .navigationTitle("Present")
            .background(detailsNavigationLink)
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
    }

    private var detailsNavigationLink: some View {
        NavigationLink(
            destination: detailsDestination,
            isActive: Binding(
                get: { selectedDetailsID != nil },
                set: { isActive in
                    if !isActive {
                        selectedDetailsID = nil
                    }
                }
            )
        ) {
            EmptyView()
        }
        .hidden()
    }

    private var detailsDestination: some View {
        Group {
            if let detailsID = selectedDetailsID {
                CredentialDetailsDestination(
                    detailsID: detailsID,
                    details: presentationDetails
                )
            } else {
                EmptyView()
            }
        }
    }
}

private struct PresentationErrorView: View {
    let error: PresentationPreviewError
    let isEnabled: Bool
    let onNotifyVerifier: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("This request cannot be completed")
                .font(.headline)
            VerifierReviewSections(request: error.request)
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
                    .tint(.waltBlue)
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
