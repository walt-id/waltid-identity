import SwiftUI
import UIKit
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
    @State private var confirmDelete = false

    private var details: [CredentialDetails] {
        viewModel.credentials.map(CredentialDisplayNormalizer.details(for:))
    }

    private var expanded: CredentialDetails? {
        details.first { $0.id == selectedDetailsID }
    }

    private var expandedRawCredential: String {
        viewModel.credentials.first(where: { $0.id == selectedDetailsID })?.credentialDataJSON
            ?? "No raw credential available"
    }

    var body: some View {
        NavigationView {
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
                .animation(.easeOut(duration: 0.16), value: showDetailsBody)
            }
            .animation(.easeOut(duration: 0.2), value: selectedDetailsID)
            .navigationTitle(selectedDetailsID == nil ? branding.appTitle : "")
            .accessibilityIdentifier(WalletAccessibilityID.appTitle)
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(selectedDetailsID != nil)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Group {
                        if selectedDetailsID != nil {
                            Button {
                                closeDetails()
                            } label: {
                                Image(systemName: "xmark")
                                    .font(.system(size: 14, weight: .semibold))
                            }
                            .accessibilityIdentifier(WalletAccessibilityID.detailsBack)
                        }
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Group {
                        if selectedDetailsID != nil {
                            Menu {
                                Button("Copy") {
                                    UIPasteboard.general.string = expandedRawCredential
                                }
                                .accessibilityIdentifier(WalletAccessibilityID.copyRawCredential)
                                Button("Delete", role: .destructive) {
                                    confirmDelete = true
                                }
                                .accessibilityIdentifier(WalletAccessibilityID.deleteCredential)
                            } label: {
                                Image(systemName: "ellipsis")
                                    .font(.system(size: 16, weight: .semibold))
                            }
                            .accessibilityIdentifier(WalletAccessibilityID.detailsMenu)
                        } else {
                            NavigationLink {
                                SettingsView(viewModel: viewModel)
                            } label: {
                                Image(systemName: "gearshape")
                            }
                            .accessibilityIdentifier(WalletAccessibilityID.settingsButton)
                        }
                    }
                }
            }
            .accessibilityIdentifier(selectedDetailsID == nil
                ? WalletAccessibilityID.credentialsTabContent
                : WalletAccessibilityID.credentialDetailsScreen)
            .confirmationDialog(
                "Delete credential?",
                isPresented: $confirmDelete,
                titleVisibility: .visible
            ) {
                Button("Delete", role: .destructive) {
                    if let id = selectedDetailsID {
                        motionGeneration += 1
                        selectedDetailsID = nil
                        showDetailsBody = false
                        othersHidden = false
                        selectedAtTop = false
                        viewModel.deleteCredential(id: id)
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This removes the credential from the wallet. This cannot be undone.")
            }
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
            withAnimation(.easeIn(duration: 0.16)) {
                showDetailsBody = true
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
