import SwiftUI
import WalletDemoSharingUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @ObservedObject private var proximity: ProximityPresentationViewModel
    @State private var selectedCredentialDetailsID: String?
    @State private var showingSettings = false
    @State private var showingScanner = false
    @State private var credentialCards: [CredentialCardItem] = []
    @State private var nearbySheetHeight: CGFloat?
    @State private var scannerSheetHeight: CGFloat?
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
            } else { walletContent }
        }
        .background {
            GeometryReader { geometry in
                // Measure only pure controls, with room for the native sheet's horizontal margins.
                let width = max(1, min(geometry.size.width, 540) - 24)
                ProximityEngagementMenu(methods: [.nfc, .qr], approvalMode: .constant(viewModel.proximityApprovalMode),
                    enabled: false, onShowEngagement: { _ in }, onConnectionOptions: {})
                    .padding(.horizontal, 16).padding(.vertical, 8)
                    .frame(width: width).fixedSize(horizontal: false, vertical: true)
                    .background(GeometryReader { menu in
                        let available = max(1, geometry.size.height - 44)
                        let nearby = menu.size.height + WalletEntrySheetHeights.nativeChrome
                        let scanner = min(360, width - 40) + 48 + WalletEntrySheetHeights.nativeChrome
                        Color.clear.preference(key: WalletEntrySheetHeightsKey.self,
                            value: WalletEntrySheetHeights(nearby: nearby < available ? nearby : nil,
                                scanner: scanner < available ? scanner : nil))
                    })
                    .hidden().accessibilityHidden(true).allowsHitTesting(false)
            }
        }
        .onPreferenceChange(WalletEntrySheetHeightsKey.self) { heights in
            nearbySheetHeight = heights.nearby
            scannerSheetHeight = heights.scanner
        }
        .sheet(isPresented: $showingScanner) {
            WalletScanView(preferredSheetHeight: scannerSheetHeight, onBack: { showingScanner = false }, onOpen: openLink)
        }
        .sheet(isPresented: Binding(get: { nearbySheet.isPresented }, set: { if !$0 { dismissNearbySheet() } }),
            onDismiss: nearbySheetDidDismiss) {
            PresentView(viewModel: viewModel, onOpenSettings: openSettings, onBack: returnHome, nearbySheet: true)
                .interactiveDismissDisabled(!proximity.canClose)
                .walletSheetSizing(preferredHeight: nearbySheet.preferredHeight,
                    expanded: !proximity.showsEngagement || proximity.review != nil || proximity.displayedEngagement == .qr)
        }
        .fullScreenCover(isPresented: $showingSettings) {
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
        .onChange(of: viewModel.selectedTab) { tab in if tab != .credentials { showingScanner = false } }
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

    @ViewBuilder private var walletContent: some View {
        if proximity.active || nearbySheet.keepsHomeVisible { credentialsContent }
        else {
            switch viewModel.selectedTab {
            case .credentials: credentialsContent
            case .receive: ReceiveView(viewModel: viewModel, onOpenSettings: openSettings, onBack: returnHome)
            case .present: PresentView(viewModel: viewModel, onOpenSettings: openSettings, onBack: returnHome)
            }
        }
    }

    private var credentialsContent: some View {
        CredentialsTabView(viewModel: viewModel, selectedDetailsID: $selectedCredentialDetailsID, cards: credentialCards,
            onOpenSettings: openSettings, onScan: { showingScanner = true }, onShareNearby: startNearbySharing,
            nearbyPreparing: nearbySheet.isPreparing, nearbyEnabled: !proximity.closing)
    }

    private func returnHome() {
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
        nearbySheet = .preparing(nearbySheetHeight)
        proximity.start()
        synchronizeNearbySheet()
    }

    private func synchronizeNearbySheet() {
        if proximity.active {
            switch nearbySheet {
            case .idle:
                nearbySheet = proximity.hasEntryContent ? .presented(nearbySheetHeight) : .preparing(nearbySheetHeight)
            case .preparing(let height) where proximity.hasEntryContent && !proximity.closing:
                nearbySheet = .presented(height)
            case .hidden(let height) where !proximity.closing:
                // A rejected cancellation must restore the task and its actual SDK error.
                nearbySheet = .presented(height)
            default: break
            }
        } else {
            switch nearbySheet {
            case .presented(let height): nearbySheet = .dismissing(height)
            case .preparing, .hidden: finishNearbyDismissal()
            default: break
            }
        }
    }

    private func dismissNearbySheet() {
        guard case .presented(let height) = nearbySheet else { return }
        guard !proximity.active || proximity.canClose else { return }
        // Visibility changes immediately, even while the SDK is still draining cleanup.
        nearbySheet = .dismissing(height)
        if proximity.active { proximity.requestClose() }
    }

    private func nearbySheetDidDismiss() {
        if case .presented = nearbySheet { dismissNearbySheet() }
        guard case .dismissing(let height) = nearbySheet else { return }
        nearbySheet = .hidden(height)
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
        guard showingScanner else { return }
        showingScanner = false
        switch kind {
        case .offer:
            viewModel.startNewPresentationFlow()
            viewModel.startNewReceiveFlow()
            viewModel.selectedTab = .receive
            viewModel.offerUrl = value
            viewModel.previewOffer()
        case .presentation:
            viewModel.startNewReceiveFlow()
            viewModel.startNewPresentationFlow()
            viewModel.selectedTab = .present
            viewModel.presentationRequestUrl = value
            viewModel.previewPresentation()
        case .authorizationCallback:
            if let url = URL(string: value) { viewModel.handleDeepLink(url) }
        default: break
        }
    }
}

/// Native presentation and SDK cleanup complete independently; neither can reopen the other.
private enum NearbySheetPresentation {
    case idle
    case preparing(CGFloat?)
    case presented(CGFloat?)
    case dismissing(CGFloat?)
    case hidden(CGFloat?)

    var isPresented: Bool { if case .presented = self { return true }; return false }
    var isPreparing: Bool { if case .preparing = self { return true }; return false }
    var keepsHomeVisible: Bool { if case .idle = self { return false }; return true }
    var preferredHeight: CGFloat? {
        switch self {
        case .idle: return nil
        case .preparing(let height), .presented(let height), .dismissing(let height), .hidden(let height): return height
        }
    }
}

private struct NearbySheetSessionState: Equatable {
    let active: Bool
    let entryReady: Bool
    let closing: Bool
}

private struct WalletEntrySheetHeights: Equatable {
    // The measured body excludes the inline navigation toolbar and sheet drag-handle clearance.
    static let nativeChrome: CGFloat = 96
    var nearby: CGFloat?
    var scanner: CGFloat?
}

private struct WalletEntrySheetHeightsKey: PreferenceKey {
    static let defaultValue = WalletEntrySheetHeights()
    static func reduce(value: inout WalletEntrySheetHeights, nextValue: () -> WalletEntrySheetHeights) {
        let next = nextValue()
        value.nearby = next.nearby ?? value.nearby
        value.scanner = next.scanner ?? value.scanner
    }
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
                    ProgressView("Opening wallet…")
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
