import CodeScanner
import SwiftUI
import UIKit
import WalletDemoSharingUI

/// Scanner-first sheet; manual input and paste retain their draft without starting a flow.
struct WalletScanView: View {
    let onBack: () -> Void
    let onOpen: (String, WalletLinkKind) -> Void
    private let preferredSheetHeight: CGFloat?
    @Environment(\.scenePhase) private var scenePhase
    @State private var input: String
    @State private var manual: Bool
    @State private var dispatched = false
    @State private var closed = false
    @State private var resolving = false
    @State private var resolutionError: String?
    @State private var resolutionTask: Task<Void, Never>?
    @FocusState private var inputFocused: Bool
    private let resolveLink: (String) async throws -> ResolvedWalletLink

    init(input: String = "", preferredSheetHeight: CGFloat? = nil, onBack: @escaping () -> Void, onOpen: @escaping (String, WalletLinkKind) -> Void,
        resolveLink: @escaping (String) async throws -> ResolvedWalletLink = { try await WalletLinkResolver.resolve($0) }
    ) {
        _input = State(initialValue: input)
        _manual = State(initialValue: !input.isEmpty)
        self.onBack = onBack
        self.preferredSheetHeight = preferredSheetHeight
        self.onOpen = onOpen
        self.resolveLink = resolveLink
    }

    private var kind: WalletLinkKind { .classify(input) }

    var body: some View {
        WalletNavigationContainer {
            WalletReviewScaffold(showsActions: manual,
                background: Color(.secondarySystemGroupedBackground)) {
                if manual { manualInput }
                else {
                    ZStack {
                        if resolving || dispatched {
                            ProgressView("Opening request…")
                        } else if scenePhase == .active {
                            CodeScannerView(codeTypes: [.qr], scanMode: .once, showViewfinder: true, requiresPhotoOutput: false) { result in
                                guard !closed && !resolving && !dispatched else { return }
                                switch result {
                                case .success(let scan):
                                    input = scan.string.trimmingCharacters(in: .whitespacesAndNewlines)
                                    guard !input.isEmpty else { return }
                                    if [.offer, .presentation, .web, .authorizationCallback].contains(kind) { open(input) }
                                    else { manual = true }
                                case .failure:
                                    manual = true
                                    resolutionError = "QR scanning is unavailable. Check camera access or enter a link."
                                }
                            }
                        } else { Text("Camera paused").foregroundStyle(.secondary) }
                    }
                    .frame(maxWidth: .infinity)
                    .aspectRatio(1, contentMode: .fit).frame(maxHeight: 360).clipped()
                    .accessibilityIdentifier("wallet.scanPreview")
                }
                if let explanation, manual { Text(explanation).font(.subheadline).foregroundStyle(.secondary) }
                if let resolutionError { Text(resolutionError).font(.subheadline).foregroundStyle(.red) }
                Color.clear.frame(height: 16)
            } actions: {
                if manual {
                    WalletActions(primary: WalletAction(
                        resolving ? "Opening link…" : resolutionError != nil ? "Try again"
                            : kind == .authorizationCallback ? "Continue sign-in" : "Continue",
                        enabled: !dispatched && !resolving && [.offer, .presentation, .authorizationCallback, .web].contains(kind),
                        identifier: "wallet.scanContinue"
                    ) { open(input) })
                }
            }
            .navigationTitle(manual ? "Enter a link" : "Scan QR code")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button { inputFocused = false; manual.toggle(); resolutionError = nil } label: {
                        Image(systemName: manual ? "qrcode.viewfinder" : "keyboard").frame(minWidth: 44, minHeight: 44)
                    }.accessibilityLabel(manual ? "Scan QR code" : "Enter a link")
                        .disabled(resolving).accessibilityIdentifier("wallet.scanMode")
                    Button(action: close) { WalletToolbarIcon("xmark") }
                        .accessibilityLabel("Close scanner").accessibilityIdentifier("wallet.flowBack")
                }
            }
        }
        .accessibilityIdentifier("wallet.scanScreen")
        .walletSheetSizing(preferredHeight: preferredSheetHeight, expanded: manual)
        .modifier(ScannerSheetSurface())
        .onChange(of: input) { _ in resolutionError = nil }
        .onChange(of: manual) { manual in inputFocused = manual }
        .onDisappear { closed = true; resolutionTask?.cancel() }
    }

    private var manualInput: some View {
        HStack(alignment: .center, spacing: 8) {
            TextField("Credential offer or request", text: $input)
                .textInputAutocapitalization(.never).disableAutocorrection(true).keyboardType(.URL)
                .submitLabel(.go).onSubmit { if [.offer, .presentation, .web, .authorizationCallback].contains(kind) { open(input) } }
                .focused($inputFocused).padding(12).frame(minHeight: 52)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                .disabled(resolving).accessibilityIdentifier("wallet.scanInput")
            if #available(iOS 16, *) {
                PasteButton(payloadType: String.self) { values in input = values.first ?? "" }
                    .labelStyle(.iconOnly).frame(minWidth: 44, minHeight: 44)
                    .disabled(resolving).accessibilityIdentifier("wallet.scanPaste")
            } else {
                Button { input = UIPasteboard.general.string ?? "" } label: {
                    Image(systemName: "doc.on.clipboard").frame(minWidth: 44, minHeight: 44)
                }.accessibilityLabel("Paste link").disabled(resolving).accessibilityIdentifier("wallet.scanPaste")
            }
        }
    }

    private var explanation: String? {
        switch kind {
        case .fidoHybrid: return "This is a passkey sign-in code. Scan it with your device's system camera."
        case .unsupported: return "This is not a supported credential offer or sharing request."
        default: return nil
        }
    }

    private func open(_ value: String) {
        guard !closed && !dispatched && !resolving else { return }
        resolving = true
        resolutionError = nil
        inputFocused = false
        resolutionTask = Task { @MainActor in
            defer { resolving = false; resolutionTask = nil }
            do {
                let result = try await resolveLink(value)
                try Task.checkCancellation()
                guard !closed else { return }
                dispatched = true
                onOpen(result.url, result.kind)
            } catch {
                guard !Task.isCancelled else { return }
                manual = true
                resolutionError = (error as? WalletLinkError)?.message
                    ?? "Could not open this link. Check your connection and try again."
            }
        }
    }

    private func close() {
        guard !closed else { return }
        closed = true
        resolutionTask?.cancel()
        inputFocused = false
        onBack()
    }
}

/// Native sheet chrome provides the shadow/scrim; a separate surface keeps it visible in dark mode.
private struct ScannerSheetSurface: ViewModifier {
    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 16.4, *) {
            content.presentationBackground(Color(.secondarySystemGroupedBackground))
                .presentationCornerRadius(28)
        } else { content }
    }
}
