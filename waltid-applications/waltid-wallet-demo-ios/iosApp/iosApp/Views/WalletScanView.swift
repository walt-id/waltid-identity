import CodeScanner
import SwiftUI
import UIKit
import WalletDemoSharingUI

/// Scanner-first sheet; manual input and paste retain their draft without starting a flow.
struct WalletScanView: View {
    let onBack: () -> Void
    let onOpen: (String, WalletLinkKind) -> Void
    @Environment(\.scenePhase) private var scenePhase
    @State private var input: String
    @State private var manual: Bool
    @State private var dispatched = false
    @State private var resolving = false
    @State private var resolutionError: String?
    @State private var resolutionTask: Task<Void, Never>?
    @FocusState private var inputFocused: Bool
    private let resolveLink: (String) async throws -> ResolvedWalletLink

    init(input: String = "", onBack: @escaping () -> Void, onOpen: @escaping (String, WalletLinkKind) -> Void,
        resolveLink: @escaping (String) async throws -> ResolvedWalletLink = { try await WalletLinkResolver.resolve($0) }
    ) {
        _input = State(initialValue: input)
        _manual = State(initialValue: !input.isEmpty)
        self.onBack = onBack
        self.onOpen = onOpen
        self.resolveLink = resolveLink
    }

    private var kind: WalletLinkKind { .classify(input) }

    var body: some View {
        WalletNavigationContainer {
            WalletReviewScaffold(showsActions: manual || resolving || resolutionError != nil) {
                if manual { manualInput }
                else if scenePhase == .active && !resolving && !dispatched {
                    CodeScannerView(codeTypes: [.qr], scanMode: .once, showViewfinder: true, requiresPhotoOutput: false) { result in
                        switch result {
                        case .success(let scan):
                            input = scan.string.trimmingCharacters(in: .whitespacesAndNewlines)
                            if [.offer, .presentation, .web, .authorizationCallback].contains(kind) { open(input) }
                            else { manual = true }
                        case .failure:
                            manual = true
                            resolutionError = "QR scanning is unavailable. Check camera access or enter a link."
                        }
                    }
                    .aspectRatio(1, contentMode: .fit).frame(maxHeight: 360).clipped()
                    .accessibilityIdentifier("wallet.scanPreview")
                }
                if let explanation, manual { Text(explanation).font(.subheadline).foregroundStyle(.secondary) }
            } actions: {
                if resolving { ProgressView("Opening link…").frame(maxWidth: .infinity, alignment: .leading) }
                if let resolutionError { Text(resolutionError).font(.subheadline).foregroundStyle(.red).frame(maxWidth: .infinity, alignment: .leading) }
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
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onBack) { Image(systemName: "xmark").frame(minWidth: 44, minHeight: 44) }
                        .accessibilityLabel("Close scanner").accessibilityIdentifier("wallet.flowBack")
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { inputFocused = false; manual.toggle(); resolutionError = nil } label: {
                        Image(systemName: manual ? "qrcode.viewfinder" : "keyboard").frame(minWidth: 44, minHeight: 44)
                    }.accessibilityLabel(manual ? "Scan QR code" : "Enter a link")
                        .disabled(resolving).accessibilityIdentifier("wallet.scanMode")
                }
            }
        }
        .accessibilityIdentifier("wallet.scanScreen")
        .onChange(of: input) { _ in resolutionError = nil }
        .onChange(of: manual) { manual in inputFocused = manual }
        .onDisappear { resolutionTask?.cancel() }
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
        guard !dispatched && !resolving else { return }
        resolving = true
        resolutionError = nil
        inputFocused = false
        resolutionTask = Task { @MainActor in
            defer { resolving = false; resolutionTask = nil }
            do {
                let result = try await resolveLink(value)
                try Task.checkCancellation()
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
}
