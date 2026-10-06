import SwiftUI
import WalletDemoSharingUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @ObservedObject private var proximity: ProximityPresentationViewModel
    @State private var selectedCredentialDetailsID: String?
    @State private var showingSettings = false
    @State private var showingScanner = false
    @State private var credentialCards: [CredentialCardItem] = []

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
        .sheet(isPresented: $showingScanner) {
            if #available(iOS 16, *) {
                WalletScanView(onBack: { showingScanner = false }, onOpen: openLink)
                    .presentationDetents([.medium, .large]).presentationDragIndicator(.visible)
            } else { WalletScanView(onBack: { showingScanner = false }, onOpen: openLink) }
        }
        .sheet(isPresented: Binding(get: { proximity.active }, set: { if !$0 { proximity.requestClose() } })) {
            PresentView(viewModel: viewModel, onOpenSettings: openSettings, onBack: returnHome)
                .interactiveDismissDisabled(!proximity.canClose)
                .modifier(NearbySheetSize(expanded: proximity.review != nil || proximity.displayedEngagement == .qr))
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
        .onChange(of: proximity.active) { active in
            if !active && viewModel.externalFlow == nil && viewModel.presentationSharingReview == nil && !viewModel.presentationCompleted {
                viewModel.startNewPresentationFlow()
                viewModel.selectedTab = .credentials
            }
        }
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
        if proximity.active { credentialsContent }
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
            onOpenSettings: openSettings, onScan: { showingScanner = true }, onShareNearby: {
                viewModel.startNewPresentationFlow()
                viewModel.selectedTab = .present
                viewModel.proximityPresentation.start()
            })
    }

    private func returnHome() {
        if viewModel.externalFlow != nil { viewModel.closeExternalFlow(); return }
        viewModel.startNewReceiveFlow()
        viewModel.startNewPresentationFlow()
        viewModel.proximityPresentation.dismiss()
        viewModel.selectedTab = .credentials
    }

    private func openLink(_ value: String, kind: WalletLinkKind) {
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

/// Keep wallet-opening status visible after the key operation succeeds.
private struct WalletSetupView: View {
    @ObservedObject var viewModel: WalletViewModel
    @ObservedObject var model: WalletIdentityScreenModel

    var body: some View {
        if model.identity == nil {
            WalletIdentityView(model: model)
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

private struct NearbySheetSize: ViewModifier {
    let expanded: Bool
    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 16, *) { content.presentationDetents(expanded ? [.large] : [.medium, .large]).presentationDragIndicator(.visible) }
        else { content }
    }
}
