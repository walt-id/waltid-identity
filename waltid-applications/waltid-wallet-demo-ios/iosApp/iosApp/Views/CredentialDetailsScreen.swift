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
        ZStack(alignment: .topTrailing) {
            ScrollView {
                CredentialDetailsView(details: details, onCardTap: { dismiss() })
                    .padding()
                    .padding(.top, 28)
            }
            HStack {
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
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.primary)
                        .frame(width: 36, height: 36)
                        .background(.ultraThinMaterial, in: Circle())
                }
                .accessibilityIdentifier(WalletAccessibilityID.detailsBack)
            }
            .padding(12)
        }
        .accessibilityIdentifier(WalletAccessibilityID.credentialDetailsScreen)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .navigationBarHidden(true)
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
