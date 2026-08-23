import SwiftUI
import UIKit
import WalletDemoSharingUI

struct CredentialDetailsScreen: View {
    let details: CredentialDetails
    var storedCredentialActions = false
    var onDelete: ((String) -> Void)? = nil
    var onBack: (() -> Void)? = nil
    @State private var confirmsDelete = false
    @State private var copiedRawData = false
    @State private var reviewRoute: ReviewRoute = .summary

    private var screenTitle: String {
        if case .technicalDetails(let islandID) = reviewRoute,
           let island = details.reviewIslands().first(where: { $0.id == islandID }) {
            return island.title
        }
        return "Credential details"
    }

    var body: some View {
        ScrollView {
            if storedCredentialActions {
                ReviewIslandNavigationView(
                    islands: details.reviewIslands(),
                    showsModelExpandedValues: { $0.id != "credential" },
                    hasCustomExpandedContent: {
                        $0.id == "credential" &&
                            (details.cardSummary.holderName != nil || details.groups.contains { !$0.items.isEmpty })
                    },
                    route: $reviewRoute,
                    showsTechnicalHeader: false,
                    expandedContent: { island in
                        if island.id == "credential" {
                            StoredCredentialClaims(details: details)
                        }
                    }
                )
                    .padding()
            } else {
                CredentialDetailsView(details: details)
                    .padding()
            }
        }
        .safeAreaInset(edge: .bottom) {
            if storedCredentialActions {
                HStack(spacing: 12) {
                    Button {
                        UIPasteboard.general.string = details.rawCredentialDataJSON
                        copiedRawData = true
                    } label: {
                        Text(copiedRawData ? "Copied" : "Copy raw data")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(WalletSecondaryButtonStyle())
                    .disabled(
                        details.rawCredentialDataJSON?
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                            .isEmpty != false
                    )
                    .accessibilityIdentifier(WalletAccessibilityID.credentialCopyRawData)

                    Button(role: .destructive) {
                        confirmsDelete = true
                    } label: {
                        Text("Delete").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(WalletSecondaryButtonStyle())
                    .accessibilityIdentifier(WalletAccessibilityID.credentialDelete)
                }
                .padding()
                .background(.bar)
            }
        }
        .confirmationDialog(
            "Delete credential?",
            isPresented: $confirmsDelete,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) { onDelete?(details.id) }
                .accessibilityIdentifier(WalletAccessibilityID.credentialDeleteConfirm)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the credential from this wallet.")
        }
        .navigationTitle(screenTitle)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(reviewRoute != .summary || onBack != nil)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                if reviewRoute != .summary {
                    Button { reviewRoute = .summary } label: {
                        Image(systemName: "chevron.left")
                    }
                    .accessibilityLabel("Back to credential details")
                } else if let onBack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                    }
                    .accessibilityLabel("Back to review")
                }
            }
        }
        .task(id: copiedRawData) {
            guard copiedRawData else { return }
            do {
                try await Task.sleep(nanoseconds: 2_000_000_000)
            } catch {
                return
            }
            copiedRawData = false
        }
    }
}

private struct StoredCredentialClaims: View {
    let details: CredentialDetails

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let holder = details.cardSummary.holderName?.presentableValue {
                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    Text("Holder")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(holder)
                        .font(.caption)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
            ForEach(details.groups.filter { !$0.items.isEmpty }) { group in
                Text(group.title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tint)
                ForEach(Array(group.items.enumerated()), id: \.element.id) { index, item in
                    if index > 0 { Divider() }
                    ClaimValueRow(item: item)
                }
            }
        }
    }
}

private extension String {
    var presentableValue: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
