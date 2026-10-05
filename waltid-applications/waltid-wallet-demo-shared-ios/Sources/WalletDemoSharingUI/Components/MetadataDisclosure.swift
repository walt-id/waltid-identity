import SwiftUI

/// Native disclosure semantics with the same compact heading hierarchy as the Compose renderer.
public struct MetadataDisclosure<Content: View>: View {
    public let title: String
    public let accessibilityIdentifier: String?
    public let content: Content
    @State private var isExpanded: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(
        title: String,
        initiallyExpanded: Bool,
        accessibilityIdentifier: String? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.accessibilityIdentifier = accessibilityIdentifier
        self.content = content()
        _isExpanded = State(initialValue: initiallyExpanded)
    }

    public var body: some View {
        DisclosureGroup(isExpanded: $isExpanded) {
            if isExpanded { content.padding(.top, 4) }
        } label: {
            Text(title)
                .font(.body.weight(.semibold))
                .foregroundStyle(Color.primary)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .contentShape(Rectangle())
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier(accessibilityIdentifier ?? "")
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: isExpanded)
    }
}
