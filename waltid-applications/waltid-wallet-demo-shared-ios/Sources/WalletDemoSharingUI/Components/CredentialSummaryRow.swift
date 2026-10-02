import SwiftUI

/// A compact identity summary. Its host supplies separate selection and navigation actions.
public struct CredentialSummaryRow: View {
    public let summary: CredentialCardSummary

    public init(summary: CredentialCardSummary) { self.summary = summary }

    public var body: some View {
        HStack(spacing: 12) {
            CredentialCardArtView(summary: summary, compact: true)
                .frame(width: 88)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(summary.title).font(.body.weight(.semibold))
                    .fixedSize(horizontal: false, vertical: true)
                if !summary.issuer.isEmpty {
                    Text(summary.issuer).font(.footnote).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }.frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
