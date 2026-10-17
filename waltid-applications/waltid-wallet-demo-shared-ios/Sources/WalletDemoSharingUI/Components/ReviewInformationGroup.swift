import SwiftUI

/// Selected-credential source and field rows shared by online and nearby reviews.
public struct ReviewInformationGroup<Content: View>: View {
    private let title: String
    private let content: Content

    public init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.vertical, 12)
                .background(Color(.tertiarySystemFill))
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: 12) { content }
                .frame(maxWidth: .infinity, alignment: .leading).padding(16)
        }
    }
}
