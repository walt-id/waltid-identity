import SwiftUI
import WalletDemoSharingUI
import WebKit
import WalletSDK

struct PresentView: View {
    @Environment(\.openURL) private var openURL
    @ObservedObject var viewModel: WalletViewModel
    var showsInput = true
    var onClose: (() -> Void)? = nil
    @State private var reviewRoute: ReviewRoute = .summary
    @State private var selectedDetailsID: String?

    private var presentationDetails: [CredentialDetails] {
        viewModel.presentationPreview?.credentialOptions.map(CredentialDisplayNormalizer.details(for:)) ?? []
    }

    private var screenTitle: String {
        if case .technicalDetails(let islandID) = reviewRoute,
           let island = viewModel.presentationSharingReview?.reviewIslands().first(where: { $0.id == islandID }) {
            return island.title
        }
        return viewModel.presentationSharingReview == nil ? "Present" : "Share information"
    }

    private var showsNoCredentialsMessage: Bool {
        guard viewModel.credentials.isEmpty else { return false }
        return viewModel.presentationSharingReview?.credentialOptions.isEmpty ?? true
    }

    var body: some View {
        NavigationView {
            if let selectedDetails {
                CredentialDetailsScreen(
                    details: selectedDetails,
                    storedCredentialActions: false,
                    onBack: { selectedDetailsID = nil }
                )
            } else {
                ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Color.clear
                        .frame(width: 1, height: 1)
                        .accessibilityElement()
                        .accessibilityIdentifier(WalletAccessibilityID.presentTabContent)

                    if showsInput {
                        ScannableUrlEditor(
                            title: nil,
                            label: "Request URL",
                            text: $viewModel.presentationRequestUrl,
                            inputIdentifier: WalletAccessibilityID.presentationInput,
                            scanButtonIdentifier: WalletAccessibilityID.presentationScanButton,
                            isEnabled: viewModel.presentationUrlEntryEnabled,
                            focusResetKey: viewModel.inputFocusResetKey
                        )

                        Button("Preview") {
                            viewModel.previewPresentation()
                        }
                        .buttonStyle(WalletPrimaryButtonStyle())
                        .disabled(!viewModel.presentationPreviewActionEnabled)
                        .accessibilityIdentifier(WalletAccessibilityID.presentButton)
                    }

                    if showsNoCredentialsMessage {
                        Text("No credentials available")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    if viewModel.statusShouldPersist(for: .present) {
                        StatusBannerView(
                            message: viewModel.statusMessage(for: .present),
                            isLoading: viewModel.statusIsLoading(for: .present),
                            isError: viewModel.statusIsError(for: .present),
                            onDismiss: viewModel.dismissStatus
                        )
                    }

                    if let warning = viewModel.transactionDataProfilesWarning {
                        WarningBannerView(message: warning)
                    }

                    if viewModel.presentationCompleted {
                        Button("New presentation", action: viewModel.startNewPresentationFlow)
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier(WalletAccessibilityID.presentationNewButton)
                    }

                    if let review = viewModel.presentationSharingReview {
                        SharingReviewView(
                            review: review,
                            selection: viewModel.presentationSharingSelection,
                            selectionComplete: viewModel.presentationCredentialSelectionComplete,
                            isLoading: !viewModel.presentationReviewEnabled,
                            isReadOnly: viewModel.presentationCompleted,
                            showsActions: false,
                            reviewRoute: $reviewRoute,
                            showsTechnicalHeader: false,
                            onToggleCredential: viewModel.togglePresentationCredential,
                            onToggleDisclosure: viewModel.togglePresentationDisclosure,
                            onCredentialSelected: { detailsID in selectedDetailsID = detailsID },
                            onSubmit: viewModel.submitPresentation,
                            // Normal OpenID4VP can tell the verifier it was refused, so Reject is
                            // offered alongside dismissing the review locally.
                            onReject: viewModel.rejectPresentation,
                            onCancel: viewModel.cancelPresentationReview
                        )
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
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                }
                .background(Color(.systemGroupedBackground))
                .navigationTitle(screenTitle)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        if reviewRoute != .summary {
                            Button { reviewRoute = .summary } label: {
                                Image(systemName: "chevron.left")
                            }
                            .accessibilityLabel("Back to review")
                        } else {
                            Button { onClose?() } label: {
                                Image(systemName: "xmark")
                            }
                            .accessibilityLabel("Close")
                            .disabled(onClose == nil)
                            .opacity(onClose == nil ? 0 : 1)
                        }
                    }
                }
                .safeAreaInset(edge: .bottom) {
                    if viewModel.presentationSharingReview != nil, !viewModel.presentationCompleted {
                        SharingReviewActions(
                            selectionComplete: viewModel.presentationCredentialSelectionComplete,
                            isLoading: !viewModel.presentationReviewEnabled,
                            onSubmit: viewModel.submitPresentation,
                            onReject: viewModel.rejectPresentation,
                            onCancel: viewModel.cancelPresentationReview
                        )
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(.regularMaterial)
                    }
                }
            }
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
        .onChange(of: viewModel.presentationSharingReview == nil) { _ in reviewRoute = .summary }
        .onChange(of: viewModel.presentationNavigationResetKey) { _ in selectedDetailsID = nil }
    }

    private var selectedDetails: CredentialDetails? {
        guard let selectedDetailsID else { return nil }
        return presentationDetails.first { $0.id == selectedDetailsID }
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
