import SwiftUI

/// Grouped information uses the same surfaces and hierarchy as native settings.
public struct WalletSection<Content: View>: View {
    private let title: String?
    private let titleIdentifier: String?
    private let content: Content

    public init(_ title: String? = nil, titleIdentifier: String? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.titleIdentifier = titleIdentifier
        self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let title {
                Text(title)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 16)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier(titleIdentifier ?? "")
            }
            content
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemGroupedBackground))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }
}
