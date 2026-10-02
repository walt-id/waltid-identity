import SwiftUI
import UIKit
import WalletDemoSharingUI

struct WalletScanView: View {
    let onBack: () -> Void
    let onOpen: (String, WalletLinkKind) -> Void
    @State private var input = ""
    @State private var dispatched = false
    @State private var resolving = false
    @State private var resolutionError: String?
    @State private var resolutionTask: Task<Void, Never>?
    private let resolveLink: (String) async throws -> ResolvedWalletLink

    init(input: String = "", onBack: @escaping () -> Void, onOpen: @escaping (String, WalletLinkKind) -> Void,
        resolveLink: @escaping (String) async throws -> ResolvedWalletLink = { try await WalletLinkResolver.resolve($0) }
    ) {
        _input = State(initialValue: input)
        self.onBack = onBack
        self.onOpen = onOpen
        self.resolveLink = resolveLink
    }

    private var kind: WalletLinkKind { .classify(input) }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Scan a QR code or paste a link to receive or share credentials.")
                        .font(.body)
                    WalletSection {
                        HStack(spacing: 12) {
                            Spacer(minLength: 0)
                            ScanCodeButton(identifier: "wallet.scanCamera", isEnabled: !dispatched && !resolving) { value in
                                input = value
                                let scannedKind = WalletLinkKind.classify(value)
                                if [.offer, .presentation, .web].contains(scannedKind) { open(value) }
                            }
                            if #available(iOS 16, *) {
                                PasteButton(payloadType: String.self) { values in input = values.first ?? "" }
                            } else {
                                Button("Paste link") { input = UIPasteboard.general.string ?? "" }
                            }
                        }
                        .padding(12)
                        .disabled(resolving)
                    }
                    UrlEditor(
                        title: "", label: "Credential offer or request", text: $input,
                        inputIdentifier: "wallet.scanInput", isEnabled: !dispatched && !resolving, focusResetKey: 0
                    )
                    if !input.isEmpty {
                        Button("Clear link") { input = "" }.disabled(resolving).frame(maxWidth: .infinity, minHeight: 44, alignment: .trailing)
                    }
                    if let explanation { Text(explanation).font(.subheadline).foregroundStyle(.secondary) }
                    if resolving { ProgressView("Opening link…") }
                    if let resolutionError { Text(resolutionError).foregroundStyle(.red) }
                }.padding(20)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Scan or paste")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onBack) { Label("Back to wallet", systemImage: "chevron.backward") }
                        .accessibilityIdentifier("wallet.flowBack")
                }
            }
            .safeAreaInset(edge: .bottom) {
                WalletActionBar(primary: WalletAction(
                    resolving ? "Opening link…" : resolutionError != nil ? "Try again"
                        : kind == .authorizationCallback ? "Continue sign-in" : "Continue",
                    enabled: !dispatched && !resolving && [.offer, .presentation, .authorizationCallback, .web].contains(kind),
                    identifier: "wallet.scanContinue"
                ) { open(input) })
            }
        }.navigationViewStyle(.stack)
        .accessibilityIdentifier("wallet.scanScreen")
        .onChange(of: input) { _ in resolutionError = nil }
        .onDisappear { resolutionTask?.cancel() }
    }

    private var explanation: String? {
        switch kind {
        case .fidoHybrid: return "This is a passkey sign-in code. Use your device's system camera to scan it. This wallet cannot complete FIDO hybrid sign-in."
        case .unsupported: return "This code is not a supported credential offer or sharing request. Check the link or scan another code."
        default: return nil
        }
    }

    private func open(_ value: String) {
        guard !dispatched && !resolving else { return }
        resolving = true
        resolutionError = nil
        resolutionTask = Task { @MainActor in
            defer { resolving = false; resolutionTask = nil }
            do {
                let result = try await resolveLink(value)
                try Task.checkCancellation()
                dispatched = true
                onOpen(result.url, result.kind)
            } catch {
                guard !Task.isCancelled else { return }
                resolutionError = (error as? WalletLinkError)?.message
                    ?? "Could not open this link. Check your connection and try again."
            }
        }
    }
}
