import SwiftUI
import WalletSDK

/// Issuer definitions describe a prospective credential. Actual values appear only after receipt.
public struct OfferedCredentialDetails: View {
    private let credential: IssuanceCredentialPreview
    private let issuerName: String
    private let issuerIdentifier: String
    @Environment(\.locale) private var locale

    public init(credential: IssuanceCredentialPreview, issuerName: String, issuerIdentifier: String) {
        self.credential = credential
        self.issuerName = issuerName
        self.issuerIdentifier = issuerIdentifier
    }

    public var body: some View {
        let definitions = claims
        CredentialSummaryRow(summary: .offered(from: credential, issuer: issuerName))
        if let description = credential.descriptionText, !description.isEmpty { Text(description) }
        Text(String(localized: "Values have not been received yet.", bundle: .module))
            .foregroundStyle(.secondary)
        WalletSection(String(localized: "Claim definitions", bundle: .module)) {
            VStack(alignment: .leading, spacing: 0) {
                if definitions.isEmpty {
                    Text(String(localized: "The issuer has not supplied claim definitions.", bundle: .module)).padding(16)
                } else {
                    ForEach(definitions, id: \.path) { claim in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(claim.name ?? MdocClaimDisplaySemantics.describe(format: credential.format, path: claim.path)?.label
                                 ?? CredentialDisplayVocabulary.humanizedLabel(claim.path.last ?? ""))
                            Text(claim.mandatory == true
                                 ? String(localized: "Always included", bundle: .module)
                                 : String(localized: "May be included", bundle: .module))
                                .font(.footnote).foregroundStyle(.secondary)
                        }.frame(maxWidth: .infinity, alignment: .leading).padding(16)
                        if claim.path != definitions.last?.path { Divider().padding(.leading, 16) }
                    }
                }
            }
        }
        Text(String(localized: "These definitions describe the offer. Review the actual values after receiving.", bundle: .module))
            .font(.footnote).foregroundStyle(.secondary)
        WalletSection(String(localized: "Technical details", bundle: .module)) {
            MetadataDetailList(items: [
                MetadataDetailItem(label: String(localized: "Issuer", bundle: .module), value: issuerIdentifier),
                MetadataDetailItem(label: String(localized: "Format", bundle: .module), value: credential.format),
                MetadataDetailItem(label: String(localized: "Configuration", bundle: .module), value: credential.configurationID),
                MetadataDetailItem(label: String(localized: "Credential type", bundle: .module), value: credential.vct ?? credential.doctype),
            ]).padding(16)
        }
    }

    private var claims: [CredentialClaimMetadata] {
        StoredCredentialMetadataParser.claims(from: credential.metadataJSON, preferredLocales: [locale.identifier] + Locale.preferredLanguages)
    }
}
