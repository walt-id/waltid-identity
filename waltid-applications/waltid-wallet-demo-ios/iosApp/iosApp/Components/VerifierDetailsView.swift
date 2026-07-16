import SwiftUI
import WalletSDK

struct VerifierDetailsView: View {
    let request: PresentationRequestInfo
    @State private var technicalDetailsExpanded = false

    private var transactionDataGroups: [ClaimGroup] {
        CredentialDisplayNormalizer.transactionDataGroups(for: request)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Verifier")
                .font(.subheadline.weight(.semibold))
            Text(VerifierDisplayName.value(
                verifierName: request.verifierName,
                clientID: request.clientID,
                responseURI: request.responseURI
            ))
                .font(.subheadline.weight(.medium))
            DetailLine(label: "Trust", value: CredentialDisplayText.unknown)
            ForEach(transactionDataGroups) { group in
                ClaimGroupView(group: group)
            }

            Button(technicalDetailsExpanded ? "Hide technical details" : "Show technical details") {
                technicalDetailsExpanded.toggle()
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier(WalletAccessibilityID.verifierTechnicalDetailsToggle)

            if technicalDetailsExpanded {
                VStack(alignment: .leading, spacing: 6) {
                    DetailLine(label: "Client ID", value: request.clientID)
                    DetailLine(label: "Response URI", value: request.responseURI?.absoluteString)
                    DetailLine(label: "State", value: request.state)
                    DetailLine(label: "Nonce", value: request.nonce)
                }
                .accessibilityIdentifier(WalletAccessibilityID.verifierTechnicalDetails)
            }
        }
        .accessibilityIdentifier(WalletAccessibilityID.presentationVerifier)
    }
}

private struct DetailLine: View {
    let label: String
    let value: String?

    var body: some View {
        if let value, !value.isEmpty {
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.caption)
            }
        }
    }
}
