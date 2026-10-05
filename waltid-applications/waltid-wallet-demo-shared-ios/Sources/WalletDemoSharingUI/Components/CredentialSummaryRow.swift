import SwiftUI

/// A compact identity summary. Its host supplies separate selection and navigation actions.
public struct CredentialSummaryRow: View {
    public let summary: CredentialCardSummary
    private let showsIssuer: Bool
    @Environment(\.sizeCategory) private var sizeCategory

    public init(summary: CredentialCardSummary, showsIssuer: Bool = true) {
        self.summary = summary
        self.showsIssuer = showsIssuer
    }

    public var body: some View {
        HStack(spacing: 12) {
            artwork.frame(width: sizeCategory.isAccessibilityCategory ? 48 : 64)
            labels
        }.frame(maxWidth: .infinity, alignment: .leading)
    }

    private var artwork: some View { CredentialCardArtView(summary: summary, compact: true).accessibilityHidden(true) }

    private var labels: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(summary.title).font(.body.weight(.semibold))
                .fixedSize(horizontal: false, vertical: true)
            if showsIssuer && !summary.issuer.isEmpty {
                Text(summary.issuer).font(.footnote).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
