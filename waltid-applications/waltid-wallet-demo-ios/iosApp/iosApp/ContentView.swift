import SwiftUI
import WalletDemoSharingUI

struct ContentView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var expandingExternalFlow = false
    @State private var expandedExternalFlow = false

    var body: some View {
        Group {
            if viewModel.externalFlow != nil { Color(.systemGroupedBackground).ignoresSafeArea() }
            else { walletContent }
        }
        .sheet(isPresented: Binding(get: { viewModel.externalFlow != nil && !expandingExternalFlow },
            set: { if !$0 && !expandingExternalFlow { viewModel.closeExternalFlow() } }), onDismiss: {
                if expandingExternalFlow && viewModel.externalFlow != nil { expandedExternalFlow = true }
            }) {
            VStack(spacing: 0) {
                if viewModel.auth != .unlocked || !viewModel.isReady {
                    HStack {
                        WalletOpenInAppButton()
                        Spacer()
                        Button { viewModel.closeExternalFlow() } label: { Image(systemName: "xmark").frame(minWidth: 44, minHeight: 44) }
                            .accessibilityLabel("Close request")
                            .disabled(!viewModel.canDismissExternalFlow)
                            .accessibilityIdentifier("wallet.external.close")
                    }.padding()
                }
                walletContent
            }
            .modifier(ExternalSheetSize(isReady: viewModel.isReady && viewModel.auth == .unlocked))
            .interactiveDismissDisabled(!viewModel.canDismissExternalFlow || !viewModel.isReady)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("wallet.external.flow")
            .environment(\.walletOpenInApp, { expandingExternalFlow = true })
        }
        .fullScreenCover(isPresented: Binding(get: { expandedExternalFlow && viewModel.externalFlow != nil },
            set: { if !$0 { viewModel.closeExternalFlow() } })) {
                walletContent.accessibilityIdentifier("wallet.external.expanded")
            }
        .task { viewModel.prepareExternalFlow() }
        .onChange(of: viewModel.externalFlow) { flow in
            if flow == nil { expandingExternalFlow = false; expandedExternalFlow = false }
            viewModel.prepareExternalFlow()
        }
        .onChange(of: viewModel.isReady) { _ in viewModel.prepareExternalFlow() }
        .onChange(of: viewModel.isLoading) { _ in viewModel.prepareExternalFlow() }
        .onChange(of: viewModel.auth) { _ in viewModel.prepareExternalFlow() }
        .alert("Request already in progress", isPresented: Binding(
            get: { viewModel.incomingLinkNotice != nil }, set: { if !$0 { viewModel.incomingLinkNotice = nil } })) {
            Button("OK") { viewModel.incomingLinkNotice = nil }
        } message: { Text(viewModel.incomingLinkNotice ?? "") }
        .alert(
            "Biometric signing unavailable",
            isPresented: Binding(
                get: { viewModel.signingProtectionWarning != nil },
                set: { isPresented in
                    if !isPresented {
                        viewModel.dismissSigningProtectionWarning()
                    }
                }
            )
        ) {
            Button("OK") {
                viewModel.dismissSigningProtectionWarning()
            }
            .accessibilityIdentifier(WalletAccessibilityID.signingProtectionWarningDismiss)
        } message: {
            Text(viewModel.signingProtectionWarning ?? "")
                .accessibilityIdentifier(WalletAccessibilityID.signingProtectionWarning)
        }
    }

    private var walletContent: some View {
        Group {
            switch viewModel.auth {
            case .setup, .login:
                PinView(viewModel: viewModel)
            case .biometricSetup:
                BiometricSetupView(viewModel: viewModel)
            case .storageUnavailable(let message):
                pinStorageUnavailable(message)
            case .unlocked:
                HomeView(viewModel: viewModel)
            }
        }
    }

    private func pinStorageUnavailable(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("walt.id Wallet")
                .font(.largeTitle.weight(.bold))
            Text("PIN storage unavailable")
                .font(.title3.weight(.semibold))
            Text("\(message). The wallet remains locked.")
                .foregroundColor(.red)
            Spacer()
        }
        .padding(24)
    }
}

// Preview the side-effect-free components in WalletDemoSharingUI. App-host
// previews must not initialize PIN storage, reader stores or registration services.

/// Use native resizing where available; older iOS retains its standard full-height sheet.
private struct ExternalSheetSize: ViewModifier {
    let isReady: Bool
    @Environment(\.dynamicTypeSize) private var typeSize

    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 16, *) {
            ResizableExternalSheet(isReady: isReady, needsLargeText: typeSize.isAccessibilitySize, content: content)
        } else { content }
    }
}

@available(iOS 16, *)
private struct ResizableExternalSheet<Content: View>: View {
    let isReady: Bool
    let needsLargeText: Bool
    let content: Content
    @State private var detent: PresentationDetent = .medium
    @State private var keyboardVisible = false
    @State private var resizeAfterKeyboard = false

    var body: some View {
        content.presentationDetents([.medium, .large], selection: $detent)
            .presentationDragIndicator(.visible)
            .onAppear { resize() }
            .onChange(of: isReady) { _ in
                if keyboardVisible { resizeAfterKeyboard = true } else { resize() }
            }
            .onChange(of: needsLargeText) { _ in resize() }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in keyboardVisible = true }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidHideNotification)) { _ in
                keyboardVisible = false
                if resizeAfterKeyboard { resizeAfterKeyboard = false; resize() }
            }
    }

    private func resize() { detent = isReady && !needsLargeText ? .medium : .large }
}
