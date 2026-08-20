import SwiftUI
import UIKit
import WalletDemoSharingUI

struct CredentialDetailsScreen: View {
    let details: CredentialDetails
    var storedCredentialActions = false
    var onDelete: ((String) -> Void)? = nil
    @State private var confirmsDelete = false
    @State private var copiedRawData = false

    var body: some View {
        ScrollView {
            if storedCredentialActions {
                ReviewIslandNavigationView(islands: details.reviewIslands())
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
                    .buttonStyle(.bordered)
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
                    .buttonStyle(.bordered)
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
        .navigationTitle("Credential details")
        .navigationBarTitleDisplayMode(.inline)
    }
}
