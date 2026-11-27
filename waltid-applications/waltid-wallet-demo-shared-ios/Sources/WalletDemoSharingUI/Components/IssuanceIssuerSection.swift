import SwiftUI
import WalletSDK

public struct IssuanceIssuerSection: View {
    private let issuer: IssuanceIssuerPreview
    @State private var expanded = false

    public init(issuer: IssuanceIssuerPreview) { self.issuer = issuer }

    public var body: some View {
        ExpandableMetadataCard(title: String(localized: "Issuer", bundle: .module),
            titleAccessibilityIdentifier: WalletAccessibilityID.offerIssuerSection,
            toggleAccessibilityIdentifier: WalletAccessibilityID.offerIssuerDetailsToggle, isExpanded: $expanded) {
            MetadataIdentityView(display: issuer.display,
                fallbackName: issuer.identifier, supportingText: nil)
        } details: {
            MetadataDetailList(items: [MetadataDetailItem(label: String(localized: "Credential Issuer", bundle: .module),
                value: issuer.identifier, linkURI: issuer.identifier)])
                .accessibilityIdentifier(WalletAccessibilityID.offerIssuerDetails)
        }
    }
}
