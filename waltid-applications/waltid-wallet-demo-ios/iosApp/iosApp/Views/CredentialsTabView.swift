import SwiftUI
import UIKit
import WalletDemoSharingUI
import WalletSDK

struct CredentialsTabView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?
    let cards: [CredentialCardItem]
    let onOpenSettings: () -> Void
    var onScan: (() -> Void)? = nil
    var onShareNearby: (() -> Void)? = nil
    @Environment(\.walletDemoBranding) private var branding
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var confirmDelete = false

    @State private var expanded: CredentialDetails?

    private var selectedCredential: Credential? {
        viewModel.credentials.first { $0.id == selectedDetailsID }
    }

    private var expandedRawCredential: String {
        viewModel.credentials.first(where: { $0.id == selectedDetailsID })?.credentialDataJSON
            ?? "No raw credential available"
    }

    var body: some View {
        WalletNavigationContainer {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if selectedDetailsID == nil {
                        if let onShareNearby {
                            HStack {
                                Spacer()
                                Button("Share nearby", action: onShareNearby).frame(minHeight: 44)
                                    .accessibilityIdentifier(WalletAccessibilityID.proximityStartButton)
                            }
                        }
                        if !viewModel.deferredCredentials.isEmpty {
                            WalletSection {
                                WalletNavigationRow(String(format: String(localized: "Pending · %d"), viewModel.deferredCredentials.count)) {
                                    viewModel.startNewReceiveFlow()
                                    viewModel.selectedTab = .receive
                                }.accessibilityIdentifier("issuance-pending-work")
                            }
                        }

                        if let warning = viewModel.transactionDataProfilesWarning {
                            WarningBannerView(message: warning)
                        }
                    }

                    if !viewModel.isReady {
                        if viewModel.isLoading {
                            ProgressView("Loading credentials…")
                                .accessibilityIdentifier(WalletAccessibilityID.credentialsLoading)
                        }
                    } else if viewModel.credentials.isEmpty {
                        EmptyCredentialsView()
                    } else if cards.isEmpty {
                        ProgressView("Loading credentials…")
                            .accessibilityIdentifier(WalletAccessibilityID.credentialsLoading)
                    } else {
                        CredentialCardStackView(
                            cards: cards,
                            expandedID: selectedDetailsID,
                            othersHidden: selectedDetailsID != nil,
                            selectedAtTop: selectedDetailsID != nil
                        ) { id in
                            if selectedDetailsID == id {
                                closeDetails()
                            } else {
                                openDetails(id)
                            }
                        }

                        if selectedDetailsID != nil, let expanded, expanded.id == selectedDetailsID {
                            CredentialDetailsView(details: expanded)
                            .transition(.opacity)
                        } else if selectedCredential != nil {
                            ProgressView("Loading details…")
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.top, 8)
                .padding(.bottom)
                .animation(reduceMotion ? nil : .easeOut(duration: 0.16), value: expanded?.id)
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if selectedDetailsID == nil { WalletTabFeedback(viewModel: viewModel, tab: .credentials) }
            }
            .background(Color(.systemGroupedBackground))
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.22), value: selectedDetailsID)
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
                            .accessibilityLabel("Close credential information")
                            .frame(minWidth: 44, minHeight: 44)
                            .accessibilityIdentifier(WalletAccessibilityID.detailsBack)
                        }
                    }
                }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
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
                            if let onScan {
                                Button(action: onScan) { Image(systemName: "qrcode.viewfinder") }
                                    .accessibilityLabel("Scan or paste a link")
                                    .accessibilityIdentifier("wallet.scanButton")
                            }
                            Button(action: onOpenSettings) {
                                Image(systemName: "gearshape")
                            }
                            .accessibilityLabel("Settings")
                .accessibilityIdentifier(WalletAccessibilityID.settingsButton)
                        }
                    }
                }
            }
            .accessibilityElement(children: .contain)
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
                        selectedDetailsID = nil
                        viewModel.deleteCredential(id: id)
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This removes the credential from the wallet. This cannot be undone.")
            }
        }
        .task(id: selectedCredential) {
            expanded = nil
            guard let selectedCredential else { return }
            let snapshot = await CredentialDisplayNormalizer.details(for: [selectedCredential])
            guard !Task.isCancelled else { return }
            expanded = snapshot.first
        }
    }

    private func openDetails(_ id: String) {
        expanded = nil
        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.22)) { selectedDetailsID = id }
    }

    private func closeDetails() {
        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.22)) { selectedDetailsID = nil }
    }
}
