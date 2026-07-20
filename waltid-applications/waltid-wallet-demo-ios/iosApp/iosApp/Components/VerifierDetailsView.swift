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
            MetadataIdentityView(
                display: request.verifierMetadata?.display,
                fallbackName: VerifierDisplayName.value(
                    verifierName: request.verifierMetadata?.display?.name,
                    clientID: request.clientID,
                    responseURI: request.responseURI
                ),
                supportingText: "Verifier-provided identity"
            )
            MetadataDetailLine(label: "Trust", value: CredentialDisplayText.unknown)
            MetadataDetailLine(
                label: "Response encryption",
                value: responseEncryptionStatus
            )
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
                    MetadataDetailLine(label: "Client ID", value: request.clientID)
                    MetadataDetailLine(label: "Response URI", value: request.responseURI?.absoluteString)
                    MetadataDetailLine(label: "Client URI", value: request.verifierMetadata?.clientURI)
                    MetadataDetailLine(label: "Privacy policy", value: request.verifierMetadata?.policyURI)
                    MetadataDetailLine(label: "Terms of service", value: request.verifierMetadata?.termsOfServiceURI)
                    MetadataDetailLine(label: "State", value: request.state)
                    MetadataDetailLine(label: "Nonce", value: request.nonce)
                    if case let .required(details) = request.responseEncryption {
                        MetadataDetailLine(label: "JWE algorithm", value: details.keyManagementAlgorithm)
                        MetadataDetailLine(label: "Content encryption", value: details.contentEncryptionAlgorithm)
                        MetadataDetailLine(label: "Verifier key ID", value: details.verifierKeyID)
                        MetadataDetailLine(label: "Verifier key thumbprint", value: details.verifierKeyThumbprint)
                    }
                }
                .accessibilityIdentifier(WalletAccessibilityID.verifierTechnicalDetails)
            }
        }
        .accessibilityIdentifier(WalletAccessibilityID.presentationVerifier)
    }

    private var responseEncryptionStatus: String {
        switch request.responseEncryption {
        case .notRequired: "Not encrypted"
        case .required: "Encrypted"
        }
    }
}
