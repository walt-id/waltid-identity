import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct CredentialsTabView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?
    @Environment(\.walletDemoBranding) private var branding
    @State private var othersHidden = false
    @State private var selectedAtTop = false
    @State private var showDetailsBody = false
    @State private var motionGeneration = 0

    private var details: [CredentialDetails] {
        viewModel.credentials.map(CredentialDisplayNormalizer.details(for:))
    }

    private var expanded: CredentialDetails? {
        details.first { $0.id == selectedDetailsID }
    }

    var body: some View {
        NavigationView {
            ZStack(alignment: .topTrailing) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        if selectedDetailsID == nil {
                            WalletTabStatusBanner(viewModel: viewModel, tab: .credentials)

                            if let warning = viewModel.transactionDataProfilesWarning {
                                WarningBannerView(message: warning)
                            }
                        }

                        if details.isEmpty {
                            EmptyCredentialsView()
                        } else {
                            CredentialCardStackView(
                                details: details,
                                expandedID: selectedDetailsID,
                                othersHidden: othersHidden,
                                selectedAtTop: selectedAtTop
                            ) { id in
                                if selectedDetailsID == id {
                                    closeDetails()
                                } else {
                                    openDetails(id)
                                }
                            }

                            if showDetailsBody, let expanded {
                                CredentialDetailsView(
                                    details: expanded,
                                    onCardTap: { closeDetails() },
                                    showCard: false
                                )
                                .transition(.opacity)
                            }
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, 8)
                    .padding(.bottom)
                    .animation(.easeOut(duration: 0.2), value: showDetailsBody)
                }

                if selectedDetailsID != nil {
                    Button {
                        closeDetails()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.primary)
                            .frame(width: 36, height: 36)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                    .padding(12)
                    .accessibilityIdentifier(WalletAccessibilityID.detailsBack)
                    .transition(.opacity)
                }
            }
            .animation(.easeOut(duration: 0.2), value: selectedDetailsID)
            .navigationTitle(branding.appTitle)
            .accessibilityIdentifier(WalletAccessibilityID.appTitle)
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarHidden(selectedDetailsID != nil)
            .walletSettingsToolbar(viewModel: viewModel)
            .accessibilityIdentifier(selectedDetailsID == nil
                ? WalletAccessibilityID.credentialsTabContent
                : WalletAccessibilityID.credentialDetailsScreen)
        }
        .navigationViewStyle(.stack)
    }

    private func openDetails(_ id: String) {
        motionGeneration += 1
        let generation = motionGeneration
        selectedDetailsID = id
        showDetailsBody = false
        selectedAtTop = false
        withAnimation(.easeOut(duration: 0.22)) {
            othersHidden = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) {
            guard generation == motionGeneration, selectedDetailsID == id else { return }
            withAnimation(.spring(response: 0.42, dampingFraction: 0.86)) {
                selectedAtTop = true
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.38) {
                guard generation == motionGeneration, selectedDetailsID == id else { return }
                withAnimation(.easeIn(duration: 0.2)) {
                    showDetailsBody = true
                }
            }
        }
    }

    private func closeDetails(resetSelection: Bool = true) {
        guard selectedDetailsID != nil else { return }
        motionGeneration += 1
        let generation = motionGeneration
        withAnimation(.easeOut(duration: 0.2)) {
            showDetailsBody = false
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            guard generation == motionGeneration else { return }
            withAnimation(.spring(response: 0.42, dampingFraction: 0.86)) {
                selectedAtTop = false
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.38) {
                guard generation == motionGeneration else { return }
                withAnimation(.easeIn(duration: 0.22)) {
                    othersHidden = false
                }
                if resetSelection {
                    selectedDetailsID = nil
                }
            }
        }
    }
}
