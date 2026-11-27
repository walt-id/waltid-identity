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

    @State private var detailUnavailable = false

    private var selectedCredential: CredentialSummary? {
        viewModel.credentials.first { $0.id == selectedDetailsID }
    }

    private var expandedRawCredential: String {
        expanded?.credentialDataJSON
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
                        } else if selectedCredential != nil && detailUnavailable {
                            Text(String(localized: "Credential details unavailable"))
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
                if selectedDetailsID == nil { homeFeedback }
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
                                .disabled(expanded?.id != selectedDetailsID || expanded == nil)
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
            detailUnavailable = false
            do {
                guard let credential = try await viewModel.credential(id: selectedCredential.id) else {
                    if !Task.isCancelled { detailUnavailable = true }
                    return
                }
                let snapshot = await CredentialDisplayNormalizer.loadDetails(for: credential)
                guard !Task.isCancelled else { return }
                expanded = snapshot
            } catch {
                if !Task.isCancelled { detailUnavailable = true }
            }
        }
    }

    @ViewBuilder
    private var homeFeedback: some View {
        let operation = WalletTabStatusBanner(viewModel: viewModel, tab: .credentials)
        let operationKind = viewModel.statusKind(for: .credentials)
        let credentialNotices = statusNotices.compactMap { notice in
            cards.first { $0.id == notice.credentialID }.map { (notice, $0.summary.title) }
        }
        if operation.isVisible || !credentialNotices.isEmpty {
            WalletFooter {
                Group {
                    if operation.isVisible && (operationKind == .busy || operationKind == .error) {
                        operation
                    } else if let (notice, title) = credentialNotices.first {
                        CredentialStatusHomeNotice(title: title, notice: notice, remaining: credentialNotices.count - 1, onOpen: {
                            openDetails(notice.credentialID)
                            viewModel.acknowledgeCredentialStatusNotice(notice)
                        }, onDismiss: {
                            viewModel.acknowledgeCredentialStatusNotice(notice)
                        })
                        .id(notice.version)
                    } else {
                        operation
                    }
                }
                .frame(maxWidth: credentialCardMaxWidth).frame(maxWidth: .infinity)
            }
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
    let remaining: Int
    let onOpen: () -> Void
    let onDismiss: () -> Void
    @Environment(\.locale) private var locale
    @Environment(\.timeZone) private var timeZone
    @Environment(\.sizeCategory) private var sizeCategory

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
        WalletNoticeCard(tone: .warning, onDismiss: onDismiss) {
            ZStack(alignment: .topTrailing) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(title) - \(kind)").font(.callout.weight(.semibold))
                    Text(checked).font(.footnote)
                    noticeActions
                }
                .multilineTextAlignment(.leading)
                .padding(.leading, 14).padding(.trailing, 50).padding(.top, 12).padding(.bottom, 8)
                .frame(maxWidth: .infinity, alignment: .leading)

                WalletNoticeClose(label: String(localized: "Dismiss credential status notice"), action: onDismiss)
            }
        }
    }

    @ViewBuilder
    private var noticeActions: some View {
        if remaining == 0 {
            openButton
        } else if #available(iOS 16, *) {
            ViewThatFits(in: .horizontal) {
                inlineActions.fixedSize(horizontal: true, vertical: false)
                stackedActions
            }
        } else if sizeCategory.isAccessibilityCategory {
            stackedActions
        } else {
            inlineActions
        }
    }

    private var inlineActions: some View {
        HStack(spacing: 8) {
            openButton
            remainingText
        }
    }

    private var stackedActions: some View {
        VStack(alignment: .leading, spacing: 4) {
            openButton
            remainingText
        }
    }

    private var openButton: some View {
        Button(action: onOpen) {
            Text("View credential")
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 10).padding(.vertical, 10)
                .background { RoundedRectangle(cornerRadius: 8).fill(.foreground).opacity(0.12) }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var remainingText: some View {
        Text("\(remaining) more").font(.footnote)
    }
}
