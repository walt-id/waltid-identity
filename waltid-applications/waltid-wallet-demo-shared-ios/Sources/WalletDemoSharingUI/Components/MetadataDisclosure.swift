import SwiftUI

/// One full-row expansion target with the same compact hierarchy as the Compose renderer.
public struct MetadataDisclosure<Content: View>: View {
    public let title: String
    public let accessibilityIdentifier: String?
    public let content: Content
    @State private var isExpanded: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection

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
        VStack(alignment: .leading, spacing: 4) {
            Button { isExpanded.toggle() } label: {
                HStack(spacing: 8) {
                    Text(title).font(.body.weight(.semibold)).foregroundStyle(Color.primary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "chevron.forward")
                        .font(.body.weight(.semibold)).foregroundStyle(.tint)
                        .rotationEffect(.degrees(isExpanded ? (layoutDirection == .rightToLeft ? -90 : 90) : 0))
                        .accessibilityHidden(true)
                }
                .frame(minHeight: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(.isHeader)
            .accessibilityValue(isExpanded ? String(localized: "Expanded", bundle: .module) : String(localized: "Collapsed", bundle: .module))
            .accessibilityIdentifier(accessibilityIdentifier ?? "")
            if isExpanded { content }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: isExpanded)
    }
}
