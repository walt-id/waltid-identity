import SwiftUI
import UIKit
import WalletDemoSharingUI

struct CredentialDetailsScreen: View {
    let details: CredentialDetails
    var rawCredentialJSON: String?
    var onDelete: (() -> Void)?
    @State private var confirmDelete = false

    var body: some View {
        ScrollView {
            CredentialDetailsView(details: details)
                .padding()
        }
        .navigationTitle("Credential details")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
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
