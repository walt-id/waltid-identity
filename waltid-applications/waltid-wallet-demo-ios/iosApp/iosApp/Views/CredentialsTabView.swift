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
    var nearbyPreparing = false
    var nearbyEnabled = true
    var statusNotices: [PendingCredentialStatusNotice] = []
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
                            WalletProgressRow(String(localized: "Loading credentials…"))
                                .accessibilityIdentifier(WalletAccessibilityID.credentialsLoading)
                        }
                    } else if viewModel.credentials.isEmpty {
                        EmptyCredentialsView()
                    } else if cards.isEmpty {
                        WalletProgressRow(String(localized: "Loading credentials…"))
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
                            WalletProgressRow(String(localized: "Loading details…"))
                        }
                    }
                }
                .frame(maxWidth: credentialCardMaxWidth)
                .frame(maxWidth: .infinity)
                .padding(.horizontal)
                .padding(.top, 8)
                .padding(.bottom)
                .animation(reduceMotion ? nil : .easeOut(duration: 0.16), value: expanded?.id)
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if selectedDetailsID == nil {
                    VStack(spacing: 0) {
                        if let (notice, title) = statusNotices.lazy.compactMap({ notice in
                            cards.first { $0.id == notice.credentialID }.map { (notice, $0.summary.title) }
                        }).first {
                            CredentialStatusHomeNotice(title: title, notice: notice, onOpen: {
                                openDetails(notice.credentialID)
                                viewModel.acknowledgeCredentialStatusNotice(notice)
                            }, onDismiss: {
                                viewModel.acknowledgeCredentialStatusNotice(notice)
                            })
                            .id(notice.version)
                            .frame(maxWidth: credentialCardMaxWidth).frame(maxWidth: .infinity)
                            .padding(.horizontal, 16).padding(.vertical, 12)
                        }
                        WalletTabFeedback(viewModel: viewModel, tab: .credentials)
                    }
                }
            }
            .background(Color(.systemGroupedBackground))
            .animation(WalletMotion.navigation(reduceMotion: reduceMotion), value: selectedDetailsID)
            .navigationTitle(selectedDetailsID == nil ? branding.appTitle : "")
            .accessibilityIdentifier(WalletAccessibilityID.appTitle)
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(selectedDetailsID != nil)
            .toolbar {
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
                                WalletToolbarIcon("ellipsis")
                            }
                            .accessibilityIdentifier(WalletAccessibilityID.detailsMenu)
                            Button(action: closeDetails) {
                                WalletToolbarIcon("xmark")
                            }
                            .accessibilityLabel("Close credential information")
                            .accessibilityIdentifier(WalletAccessibilityID.detailsBack)
                        } else {
                            if let onShareNearby {
                                Button(action: onShareNearby) {
                                    WalletToolbarIcon("dot.radiowaves.left.and.right", isBusy: nearbyPreparing)
                                }
                                    .accessibilityLabel(nearbyPreparing ? "Cancel starting nearby sharing" : "Share nearby")
                                    .disabled(!nearbyEnabled)
                                    .accessibilityIdentifier(WalletAccessibilityID.proximityStartButton)
                            }
                            if let onScan {
                                Button(action: onScan) { WalletToolbarIcon("qrcode.viewfinder") }
                                    .accessibilityLabel("Scan or paste a link")
                                    .accessibilityIdentifier("wallet.scanButton")
                            }
                            Button(action: onOpenSettings) {
                                WalletToolbarIcon("gearshape")
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
        withAnimation(WalletMotion.navigation(reduceMotion: reduceMotion)) { selectedDetailsID = id }
    }

    private func closeDetails() {
        withAnimation(WalletMotion.navigation(reduceMotion: reduceMotion)) { selectedDetailsID = nil }
    }
}

private struct CredentialStatusHomeNotice: View {
    let title: String
    let notice: PendingCredentialStatusNotice
    let onOpen: () -> Void
    let onDismiss: () -> Void
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.locale) private var locale
    @Environment(\.timeZone) private var timeZone

    private var kind: String {
        switch notice.kind {
        case .revoked: String(localized: "Revoked")
        case .suspended: String(localized: "Suspended")
        default: String(localized: "Invalid")
        }
    }

    private var checked: String {
        let minute = Date(timeIntervalSince1970: floor(notice.checkedAt.timeIntervalSince1970 / 60) * 60)
        let date = WalletInstant(minute).formatted(timeZone: timeZone, locale: locale).fullText
        return notice.historical
            ? String(localized: "Checked: \(date) (last known)")
            : String(localized: "Checked: \(date)")
    }

    var body: some View {
        let dark = colorScheme == .dark
        let background = dark
            ? Color(red: 0x34 / 255.0, green: 0x2A / 255.0, blue: 0x1B / 255.0)
            : Color(red: 0xFD / 255.0, green: 0xF0 / 255.0, blue: 0xD5 / 255.0)
        let border = dark
            ? Color(red: 0x8E / 255.0, green: 0x75 / 255.0, blue: 0x4D / 255.0)
            : Color(red: 0xC6 / 255.0, green: 0xA3 / 255.0, blue: 0x5C / 255.0)
        let foreground = dark
            ? Color(red: 0xFF / 255.0, green: 0xE5 / 255.0, blue: 0xAE / 255.0)
            : Color(red: 0x4B / 255.0, green: 0x32 / 255.0, blue: 0x08 / 255.0)
        let shape = RoundedRectangle(cornerRadius: 16)
        ZStack(alignment: .topTrailing) {
            Button(action: onOpen) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(title) - \(kind)").font(.callout.weight(.semibold))
                    Text(checked).font(.footnote)
                    Text("View credential")
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(foreground.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                        .padding(.top, 4)
                }
                .multilineTextAlignment(.leading)
                .padding(.leading, 14).padding(.trailing, 50).padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button(action: onDismiss) {
                Image(systemName: "xmark").font(.callout.weight(.semibold))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss credential status notice")
        }
        .foregroundStyle(foreground)
        .background(background, in: shape)
        .overlay { shape.strokeBorder(border, lineWidth: 1).allowsHitTesting(false) }
    }
}
