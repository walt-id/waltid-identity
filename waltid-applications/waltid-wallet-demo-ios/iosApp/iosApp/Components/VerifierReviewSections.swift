import SwiftUI
import WalletSDK

struct VerifierReviewSections: View {
    private enum Request {
        case ready(PresentationRequestInfo)
        case invalid(PresentationRequestContext)
    }

    private let request: Request
    @State private var technicalDetailsExpanded = false

    init(request: PresentationRequestInfo) {
        self.request = .ready(request)
    }

    init(request: PresentationRequestContext) {
        self.request = .invalid(request)
    }

    private var transactionDataGroups: [ClaimGroup] {
        guard case let .ready(request) = request else { return [] }
        return CredentialDisplayNormalizer.transactionDataGroups(for: request)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let verifierMetadata,
               verifierDisplayName != nil || !verifierDetails.isEmpty {
                ReviewMetadataSection(
                    title: "Verifier",
                    titleAccessibilityIdentifier: WalletAccessibilityID.presentationVerifierSection
                ) {
                    if let verifierDisplayName {
                        MetadataIdentityView(
                            display: verifierMetadata.display,
                            fallbackName: verifierDisplayName,
                            supportingText: nil
                        )
                        if !verifierDetails.isEmpty {
                            Divider()
                        }
                    }
                    MetadataDetailList(items: verifierDetails)
                }
            }

            ForEach(transactionDataGroups) { group in
                ClaimGroupView(group: group)
            }

            ReviewMetadataSection(
                title: "Response protection",
                titleAccessibilityIdentifier: WalletAccessibilityID.presentationResponseProtectionSection
            ) {
                MetadataDetailList(items: responseProtectionDetails)
            }

            ReviewMetadataSection(
                title: "Technical request details",
                titleAccessibilityIdentifier: WalletAccessibilityID.presentationTechnicalDetailsSection,
                contentInsets: technicalDetailsExpanded
                    ? EdgeInsets(top: 16, leading: 16, bottom: 16, trailing: 16)
                    : EdgeInsets(top: 2, leading: 16, bottom: 2, trailing: 16)
            ) {
                Button {
                    technicalDetailsExpanded.toggle()
                } label: {
                    HStack {
                        Text(technicalDetailsExpanded ? "Hide details" : "Show details")
                        Spacer()
                        Image(systemName: technicalDetailsExpanded ? "chevron.up" : "chevron.down")
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier(WalletAccessibilityID.verifierTechnicalDetailsToggle)

                if technicalDetailsExpanded {
                    Divider()
                    MetadataDetailList(items: technicalDetails)
                    .accessibilityIdentifier(WalletAccessibilityID.verifierTechnicalDetails)
                }
            }
        }
    }

    private var responseEncryptionStatus: String {
        switch responseEncryption {
        case .notRequired: "Not requested"
        case .required: "Required"
        }
    }

    private var verifierDisplayName: String? {
        guard let name = verifierMetadata?.display?.name?.trimmingCharacters(in: .whitespacesAndNewlines),
              !name.isEmpty else {
            return nil
        }
        return name
    }

    private var verifierDetails: [MetadataDetailItem] {
        guard let metadata = verifierMetadata else { return [] }
        return [
            MetadataDetailItem(label: "Client URI", value: metadata.clientURI, linkURI: metadata.clientURI),
            MetadataDetailItem(label: "Privacy policy", value: metadata.policyURI, linkURI: metadata.policyURI),
            MetadataDetailItem(label: "Terms of service", value: metadata.termsOfServiceURI, linkURI: metadata.termsOfServiceURI),
        ].filter(\.isVisible)
    }

    private var responseProtectionDetails: [MetadataDetailItem] {
        var items = [MetadataDetailItem(label: "Message-level encryption", value: responseEncryptionStatus)]
        if case let .required(details) = responseEncryption {
            items += [
                MetadataDetailItem(label: "Key management algorithm", value: details.keyManagementAlgorithm),
                MetadataDetailItem(label: "Content encryption algorithm", value: details.contentEncryptionAlgorithm),
                MetadataDetailItem(label: "Verifier key ID", value: details.verifierKeyID),
                MetadataDetailItem(label: "Verifier key thumbprint", value: details.verifierKeyThumbprint),
            ]
        }
        return items
    }

    private var technicalDetails: [MetadataDetailItem] {
        [
            MetadataDetailItem(label: "Client ID", value: clientID),
            MetadataDetailItem(label: "Response URI", value: responseURI?.absoluteString),
            MetadataDetailItem(label: "State", value: state),
            MetadataDetailItem(label: "Nonce", value: nonce),
        ]
    }

    private var clientID: String {
        switch request {
        case let .ready(request): request.clientID
        case let .invalid(request): request.clientID
        }
    }

    private var verifierMetadata: VerifierMetadata? {
        switch request {
        case let .ready(request): request.verifierMetadata
        case let .invalid(request): request.verifierMetadata
        }
    }

    private var responseURI: URL? {
        switch request {
        case let .ready(request): request.responseURI
        case let .invalid(request): request.responseURI
        }
    }

    private var state: String? {
        switch request {
        case let .ready(request): request.state
        case let .invalid(request): request.state
        }
    }

    private var nonce: String? {
        switch request {
        case let .ready(request): request.nonce
        case let .invalid(request): request.nonce
        }
    }

    private var responseEncryption: PresentationResponseEncryption {
        switch request {
        case let .ready(request): request.responseEncryption
        case let .invalid(request): request.responseEncryption
        }
    }
}
