import SwiftUI
import UIKit
import WalletDemoSharingUI

struct CredentialDetailsScreen: View {
    let details: CredentialDetails
    var rawCredentialJSON: String?
    var onDelete: (() -> Void)?
    @Environment(\.dismiss) private var dismiss
    @State private var confirmDelete = false

    var body: some View {
        ScrollView {
            CredentialDetailsView(details: details, onCardTap: { dismiss() })
                .padding()
        }
        .accessibilityIdentifier(WalletAccessibilityID.credentialDetailsScreen)
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                }
                .accessibilityIdentifier(WalletAccessibilityID.detailsBack)
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button("Copy") {
                        UIPasteboard.general.string = rawCredentialJSON?.nilIfEmpty ?? "No raw credential available"
                    }
                    .accessibilityIdentifier(WalletAccessibilityID.copyRawCredential)
                    if onDelete != nil {
                        Button("Delete", role: .destructive) {
                            confirmDelete = true
                        }
                        .accessibilityIdentifier(WalletAccessibilityID.deleteCredential)
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 16, weight: .semibold))
                }
                .accessibilityIdentifier(WalletAccessibilityID.detailsMenu)
            }
        }
        .confirmationDialog(
            "Delete credential?",
            isPresented: $confirmDelete,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                onDelete?()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the credential from the wallet. This cannot be undone.")
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
