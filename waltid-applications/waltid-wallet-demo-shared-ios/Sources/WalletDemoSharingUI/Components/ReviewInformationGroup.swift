import SwiftUI

/// Selected-credential source and field rows shared by online and nearby reviews.
public struct ReviewInformationGroup<Content: View>: View {
    private let title: String
    private let issuer: String?
    private let details: CredentialDetails?
    private let detailsIdentifier: String
    private let claimStatus: (ClaimItem) -> String?
    private let content: Content
    @State private var detailsOpen = false

    public init(title: String, issuer: String? = nil, details: CredentialDetails? = nil,
                detailsIdentifier: String = "", claimStatus: @escaping (ClaimItem) -> String? = { _ in nil },
                @ViewBuilder content: () -> Content) {
        self.title = title; self.issuer = issuer; self.details = details
        self.detailsIdentifier = detailsIdentifier; self.claimStatus = claimStatus; self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.subheadline.weight(.semibold))
                    if let issuer, !issuer.isEmpty { Text(issuer).font(.footnote).foregroundStyle(.secondary) }
                }.frame(maxWidth: .infinity, alignment: .leading)
                if details != nil {
                    Button(String(localized: "Details", bundle: .module)) { detailsOpen = true }
                        .buttonStyle(.plain).foregroundStyle(Color.accentColor)
                        .frame(minHeight: 44).accessibilityIdentifier(detailsIdentifier)
                }
            }
            content
        }.padding(16)
        .walletReviewDestination(isPresented: $detailsOpen) {
            if let details {
                ReviewCredentialInformation(details: details, claimStatus: claimStatus, onDismiss: { detailsOpen = false })
            }
        }
    }
}
