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
            MetadataIdentityView(display: MetadataDisplay(name: issuer.name, locale: issuer.locale,
                logoURI: issuer.logoURI?.absoluteString, logoAltText: issuer.logoAltText),
                fallbackName: issuer.identifier, supportingText: nil)
        } details: {
            if let name = issuer.name?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty, name != issuer.identifier {
                MetadataDetailList(items: [MetadataDetailItem(label: String(localized: "Credential Issuer", bundle: .module),
                    value: issuer.identifier, linkURI: issuer.identifier)])
                    .accessibilityIdentifier(WalletAccessibilityID.offerIssuerDetails)
            }
        }
    }
}
