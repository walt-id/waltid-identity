import SwiftUI

/// A named expansion target; an optional leading control acts independently of expansion.
public struct MetadataDisclosure<Content: View>: View {
    public let title: String
    public let accessibilityIdentifier: String?
    public let supportingText: String?
    public let indicatorColor: Color
    public let content: Content
    private let leadingContent: AnyView?
    @State private var isExpanded: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection

    public init(
        title: String,
        initiallyExpanded: Bool,
        accessibilityIdentifier: String? = nil,
        supportingText: String? = nil,
        indicatorColor: Color = .accentColor,
        leadingContent: AnyView? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.indicatorColor = indicatorColor
        self.leadingContent = leadingContent
        self.supportingText = supportingText
        self.title = title
        self.accessibilityIdentifier = accessibilityIdentifier
        self.content = content()
        _isExpanded = State(initialValue: initiallyExpanded)
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                if let leadingContent { leadingContent }
                Button { isExpanded.toggle() } label: {
                    HStack(spacing: 8) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(title).font(.body.weight(.semibold)).foregroundStyle(Color.primary)
                            if let supportingText { Text(supportingText).font(.footnote).foregroundStyle(.secondary) }
                        }.frame(maxWidth: .infinity, alignment: .leading)
                        Image(systemName: "chevron.forward")
                            .font(.body.weight(.semibold)).foregroundStyle(indicatorColor)
                            .rotationEffect(.degrees(isExpanded ? (layoutDirection == .rightToLeft ? -90 : 90) : 0))
                            .accessibilityHidden(true)
                    }
                    .frame(minHeight: 44).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(.isHeader)
                .accessibilityValue(isExpanded ? String(localized: "Expanded", bundle: .module) : String(localized: "Collapsed", bundle: .module))
                .accessibilityIdentifier(accessibilityIdentifier ?? "")
            }
            if isExpanded { content }
        }
        .animation(WalletMotion.disclosure(reduceMotion: reduceMotion), value: isExpanded)
    }
}
