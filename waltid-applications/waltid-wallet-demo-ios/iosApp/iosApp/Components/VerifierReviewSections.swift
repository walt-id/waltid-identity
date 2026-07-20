import SwiftUI
import WalletSDK

struct VerifierReviewSections: View {
    let request: PresentationRequestInfo
    @State private var technicalDetailsExpanded = false

    private var transactionDataGroups: [ClaimGroup] {
        CredentialDisplayNormalizer.transactionDataGroups(for: request)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let display = request.verifierMetadata?.display,
               let name = display.name?.trimmingCharacters(in: .whitespacesAndNewlines),
               !name.isEmpty {
                ReviewMetadataSection(title: "Verifier") {
                    MetadataIdentityView(
                        display: display,
                        fallbackName: name,
                        supportingText: nil
                    )
                }
                .accessibilityIdentifier(WalletAccessibilityID.presentationVerifierSection)
            }

            if let verifierMetadata = request.verifierMetadata, verifierMetadata.hasUserFacingInformation {
                ReviewMetadataSection(title: "Verifier information") {
                    MetadataDetailLine(label: "Client URI", value: verifierMetadata.clientURI)
                    MetadataDetailLine(label: "Privacy policy", value: verifierMetadata.policyURI)
                    MetadataDetailLine(label: "Terms of service", value: verifierMetadata.termsOfServiceURI)
                }
                .accessibilityIdentifier(WalletAccessibilityID.presentationVerifierInformationSection)
            }

            ForEach(transactionDataGroups) { group in
                ClaimGroupView(group: group)
            }

            ReviewMetadataSection(title: "Response protection") {
                MetadataDetailLine(
                    label: "Message-level encryption",
                    value: responseEncryptionStatus
                )
                if case let .required(details) = request.responseEncryption {
                    MetadataDetailLine(label: "Key management algorithm", value: details.keyManagementAlgorithm)
                    MetadataDetailLine(label: "Content encryption algorithm", value: details.contentEncryptionAlgorithm)
                    MetadataDetailLine(label: "Verifier key ID", value: details.verifierKeyID)
                    MetadataDetailLine(label: "Verifier key thumbprint", value: details.verifierKeyThumbprint)
                }
            }
            .accessibilityIdentifier(WalletAccessibilityID.presentationResponseProtectionSection)

            ReviewMetadataSection(title: "Technical request details") {
                Button(technicalDetailsExpanded ? "Hide details" : "Show details") {
                    technicalDetailsExpanded.toggle()
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier(WalletAccessibilityID.verifierTechnicalDetailsToggle)

                if technicalDetailsExpanded {
                    VStack(alignment: .leading, spacing: 6) {
                        MetadataDetailLine(label: "Client ID", value: request.clientID)
                        MetadataDetailLine(label: "Response URI", value: request.responseURI?.absoluteString)
                        MetadataDetailLine(label: "State", value: request.state)
                        MetadataDetailLine(label: "Nonce", value: request.nonce)
                    }
                    .accessibilityIdentifier(WalletAccessibilityID.verifierTechnicalDetails)
                }
            }
            .accessibilityIdentifier(WalletAccessibilityID.presentationTechnicalDetailsSection)
        }
    }

    private var responseEncryptionStatus: String {
        switch request.responseEncryption {
        case .notRequired: "Not requested"
        case .required: "Required"
        }
    }
}

private extension VerifierMetadata {
    var hasUserFacingInformation: Bool {
        [clientURI, policyURI, termsOfServiceURI].contains { value in
            guard let value else { return false }
            return !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
    }
}
