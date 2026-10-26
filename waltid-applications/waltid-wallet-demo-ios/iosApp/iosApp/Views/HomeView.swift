import SwiftUI
import WalletDemoSharingUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @ObservedObject private var proximity: ProximityPresentationViewModel
    @State private var selectedCredentialDetailsID: String?
    @State private var showingSettings = false
    @State private var showingScanner = false
    @State private var dismissingSettings = false
    @State private var credentialCards: [CredentialCardItem] = []
    @State private var nearbySheet: NearbySheetPresentation = .idle

    init(viewModel: WalletViewModel) {
        self.viewModel = viewModel
        self.proximity = viewModel.proximityPresentation
    }

    var body: some View {
        Group {
            if !viewModel.isReady {
                if let model = viewModel.identityScreen {
                    NavigationView { WalletSetupView(viewModel: viewModel, model: model) }
                        .navigationViewStyle(.stack)
                } else {
                    VStack(spacing: 16) {
                        if viewModel.isLoading {
                            ProgressView("Opening wallet…").accessibilityIdentifier(WalletAccessibilityID.credentialsLoading)
                        }
                        else {
                            Text(viewModel.statusMessage)
                            WalletActions(primary: WalletAction("Retry opening wallet", perform: viewModel.retryOpeningWallet))
                        }
                    }.padding(24).frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            } else { credentialsContent }
        }
        .sheet(isPresented: Binding(get: { showingTaskSheet }, set: { if !$0 { returnHome() } })) {
            Group {
                if showingOnlineFlow {
                    switch viewModel.selectedTab {
                    case .receive: ReceiveView(viewModel: viewModel, onBack: returnHome)
                    case .present: PresentView(viewModel: viewModel, onBack: returnHome)
                    case .credentials: EmptyView()
                    }
                } else {
                    WalletScanView(onBack: returnHome, onOpen: openLink)
                }
            }
            .walletSheetSizing()
            .interactiveDismissDisabled(showingOnlineFlow && !viewModel.canDismissExternalFlow)
        }
        .sheet(isPresented: Binding(get: { nearbySheet.isPresented }, set: { if !$0 { dismissNearbySheet() } }),
            onDismiss: nearbySheetDidDismiss) {
            PresentView(viewModel: viewModel, onBack: returnHome, nearbySheet: true)
                .interactiveDismissDisabled(!proximity.canClose)
                .walletSheetSizing()
        }
        .fullScreenCover(isPresented: $showingSettings, onDismiss: { dismissingSettings = false }) {
            NavigationView {
                SettingsView(viewModel: viewModel)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button { showingSettings = false } label: {
                                Label("Back", systemImage: "chevron.backward")
                            }
                            .accessibilityIdentifier("wallet.settingsBack")
                        }
                    }
            }.navigationViewStyle(.stack)
        }
        .onChange(of: viewModel.isReady) { ready in if !ready { showingSettings = false; showingScanner = false } }
        .onChange(of: NearbySheetSessionState(active: proximity.active, entryReady: proximity.hasEntryContent,
            closing: proximity.closing)) { _ in synchronizeNearbySheet() }
        .onChange(of: viewModel.externalFlow) { flow in
            if flow != nil && showingSettings {
                dismissingSettings = true
                showingSettings = false
            }
        }
        .task(id: viewModel.credentials) {
            credentialCards = []
            let cards = await CredentialDisplayNormalizer.cards(for: viewModel.credentials)
            guard !Task.isCancelled else { return }
            credentialCards = cards
        }
    }

    private func openSettings() {
        showingSettings = true
    }

    private var showingOnlineFlow: Bool {
        viewModel.selectedTab != .credentials && !proximity.active && !nearbySheet.keepsHomeVisible
    }

    private var showingTaskSheet: Bool {
        viewModel.isReady && !showingSettings && !dismissingSettings && (showingScanner || showingOnlineFlow)
    }

    private var credentialsContent: some View {
        CredentialsTabView(viewModel: viewModel, selectedDetailsID: $selectedCredentialDetailsID, cards: credentialCards,
            onOpenSettings: openSettings, onScan: { showingScanner = true }, onShareNearby: startNearbySharing,
            nearbyPreparing: nearbySheet.isPreparing, nearbyEnabled: !proximity.closing)
            .accessibilityHidden(showingTaskSheet || nearbySheet.keepsHomeVisible)
    }

    private func returnHome() {
        showingScanner = false
        if viewModel.externalFlow != nil { viewModel.closeExternalFlow(); return }
        viewModel.startNewReceiveFlow()
        viewModel.startNewPresentationFlow()
        viewModel.proximityPresentation.dismiss()
        viewModel.selectedTab = .credentials
    }

    private func startNearbySharing() {
        if nearbySheet.isPreparing {
            proximity.requestClose()
            return
        }
        guard case .idle = nearbySheet, !proximity.active else { return }
        viewModel.startNewPresentationFlow()
        viewModel.selectedTab = .present
        nearbySheet = .preparing
        proximity.start()
        synchronizeNearbySheet()
    }

    private func synchronizeNearbySheet() {
        if proximity.active {
            switch nearbySheet {
            case .idle:
                nearbySheet = proximity.hasEntryContent ? .presented : .preparing
            case .preparing where proximity.hasEntryContent && !proximity.closing:
                nearbySheet = .presented
            case .hidden where !proximity.closing:
                // A rejected cancellation must restore the task and its actual SDK error.
                nearbySheet = .presented
            default: break
            }
        } else {
            switch nearbySheet {
            case .presented: nearbySheet = .dismissing
            case .preparing, .hidden: finishNearbyDismissal()
            default: break
            }
        }
    }

    private func dismissNearbySheet() {
        guard case .presented = nearbySheet else { return }
        guard !proximity.active || proximity.canClose else { return }
        // Visibility changes immediately, even while the SDK is still draining cleanup.
        nearbySheet = .dismissing
        if proximity.active { proximity.requestClose() }
    }

    private func nearbySheetDidDismiss() {
        if case .presented = nearbySheet { dismissNearbySheet() }
        guard case .dismissing = nearbySheet else { return }
        nearbySheet = .hidden
        synchronizeNearbySheet()
    }

    private func finishNearbyDismissal() {
        guard !proximity.active else { return }
        if viewModel.isReady && viewModel.externalFlow == nil && viewModel.presentationSharingReview == nil && !viewModel.presentationCompleted {
            viewModel.startNewPresentationFlow()
            viewModel.selectedTab = .credentials
        }
        proximity.finishDismissedPresentation()
        nearbySheet = .idle
    }

    private func openLink(_ value: String, kind: WalletLinkKind) {
        guard showingScanner, let url = URL(string: value) else { return }
        viewModel.openResolvedLink(url, kind: kind)
        showingScanner = false
    }

}

/// Native presentation and SDK cleanup complete independently; neither can reopen the other.
private enum NearbySheetPresentation {
    case idle
    case preparing
    case presented
    case dismissing
    case hidden

    var isPresented: Bool { if case .presented = self { return true }; return false }
    var isPreparing: Bool { if case .preparing = self { return true }; return false }
    var keepsHomeVisible: Bool { if case .idle = self { return false }; return true }

}

private struct NearbySheetSessionState: Equatable {
    let active: Bool
    let entryReady: Bool
    let closing: Bool
}

/// Keep wallet-opening status visible after the key operation succeeds.
private struct WalletSetupView: View {
    @ObservedObject var viewModel: WalletViewModel
    @ObservedObject var model: WalletIdentityScreenModel

    var body: some View {
        if model.identity == nil {
            WalletIdentityView(biometricAvailability: viewModel.biometricSigningRecoveryAvailability,
                biometricKind: viewModel.access.biometricKind, model: model)
        } else {
            VStack(alignment: .leading, spacing: 16) {
                Text("Your signing key is ready").font(.title2)
                if viewModel.isLoading {
                    WalletProgressRow(String(localized: "Opening wallet…"))
                } else {
                    Text(viewModel.statusMessage).foregroundStyle(.secondary)
                    WalletActions(primary: WalletAction("Retry opening wallet", perform: viewModel.retryOpeningWallet))
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .navigationTitle("Set up your wallet")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
