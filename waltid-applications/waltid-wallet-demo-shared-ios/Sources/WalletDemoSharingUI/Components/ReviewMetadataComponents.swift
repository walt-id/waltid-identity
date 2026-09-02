import SwiftUI
import WalletSDK

public struct ExpandableMetadataCard<Summary: View, Details: View>: View {
    public let title: String
    public let titleAccessibilityIdentifier: String?
    public let toggleAccessibilityIdentifier: String?
    @Binding public var isExpanded: Bool
    public let summary: Summary
    public let details: Details

    public init(
        title: String,
        titleAccessibilityIdentifier: String? = nil,
        toggleAccessibilityIdentifier: String? = nil,
        isExpanded: Binding<Bool>,
        @ViewBuilder summary: () -> Summary,
        @ViewBuilder details: () -> Details
    ) {
        self.title = title
        self.titleAccessibilityIdentifier = titleAccessibilityIdentifier
        self.toggleAccessibilityIdentifier = toggleAccessibilityIdentifier
        _isExpanded = isExpanded
        self.summary = summary()
        self.details = details()
    }

    public var body: some View {
        ReviewMetadataSection(
            title: title,
            titleAccessibilityIdentifier: titleAccessibilityIdentifier
        ) {
            Button {
                isExpanded.toggle()
            } label: {
                HStack(alignment: .center, spacing: 8) {
                    summary
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .foregroundStyle(.secondary)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier(toggleAccessibilityIdentifier ?? "")

            if isExpanded {
                Divider()
                details
            }
        }
    }
}

public struct ReviewMetadataSection<Content: View>: View {
    public let title: String
    public let titleAccessibilityIdentifier: String?
    public let contentInsets: EdgeInsets
    public let content: Content

    public init(
        title: String,
        titleAccessibilityIdentifier: String? = nil,
        contentInsets: EdgeInsets = EdgeInsets(top: 16, leading: 16, bottom: 16, trailing: 16),
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.titleAccessibilityIdentifier = titleAccessibilityIdentifier
        self.contentInsets = contentInsets
        self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            titleView

            VStack(alignment: .leading, spacing: 8) {
                content
            }
            .padding(contentInsets)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    @ViewBuilder
    private var titleView: some View {
        let styledTitle = Text(title)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.tint)
        if let titleAccessibilityIdentifier {
            styledTitle.accessibilityIdentifier(titleAccessibilityIdentifier)
        } else {
            styledTitle
        }
    }
}

public struct MetadataDisclosure<Content: View>: View {
    public let title: String
    public let accessibilityIdentifier: String?
    public let content: Content
    @State private var isExpanded: Bool

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
            content.padding(.top, 4)
        } label: {
            disclosureLabel
        }
    }

    @ViewBuilder
    private var disclosureLabel: some View {
        let label = Text(title).font(.caption.weight(.medium))
        if let accessibilityIdentifier {
            label.accessibilityIdentifier(accessibilityIdentifier)
        } else {
            label
        }
    }
}

public struct MetadataIdentityView: View {
    public let display: MetadataDisplay?
    public let fallbackName: String
    public let supportingText: String?

    public init(display: MetadataDisplay?, fallbackName: String, supportingText: String?) {
        self.display = display
        self.fallbackName = fallbackName
        self.supportingText = supportingText
    }

    private var name: String {
        guard let displayName = display?.name?.trimmingCharacters(in: .whitespacesAndNewlines),
              !displayName.isEmpty else {
            return fallbackName
        }
        return displayName
    }

    private var logoURL: URL? {
        guard let value = display?.logoURI,
              let url = URL(string: value),
              url.scheme?.lowercased() == "https" else {
            return nil
        }
        return url
    }

    public var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(.systemGray6))
                if let logoURL {
                    AsyncImage(url: logoURL) { phase in
                        switch phase {
                        case let .success(image):
                            image.resizable().scaledToFit()
                        case .empty, .failure:
                            EmptyView()
                        @unknown default:
                            EmptyView()
                        }
                    }
                    .accessibilityLabel(display?.logoAltText ?? "\(name) logo")
                } else {
                    MetadataLogoFallback(name: name)
                }
            }
            .frame(width: 48, height: 48)

            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.body.weight(.semibold))
                    .lineLimit(2)
                if let supportingText, !supportingText.isEmpty {
                    Text(supportingText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
        }
    }
}

private struct MetadataLogoFallback: View {
    let name: String

    var body: some View {
        Text(name.first.map { String($0).uppercased() } ?? "?")
            .font(.headline)
    }
}

public struct MetadataDetailItem {
    public let label: String
    public let value: String?
    public let linkURI: String?

    public init(label: String, value: String?, linkURI: String? = nil) {
        self.label = label
        self.value = value
        self.linkURI = linkURI
    }

    public var isVisible: Bool {
        guard let value else { return false }
        return !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

public struct MetadataDetailList: View {
    public let items: [MetadataDetailItem]

    public init(items: [MetadataDetailItem]) {
        self.items = items
    }

    private var visibleItems: [MetadataDetailItem] {
        items.filter(\.isVisible)
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(visibleItems.enumerated()), id: \.offset) { index, item in
                if index > 0 {
                    Divider()
                }
                MetadataDetailLine(item: item)
            }
        }
    }
}

private struct MetadataDetailLine: View {
    let item: MetadataDetailItem

    private var linkURL: URL? {
        guard let linkURI = item.linkURI,
              let url = URL(string: linkURI),
              url.scheme?.lowercased() == "https" else {
            return nil
        }
        return url
    }

    var body: some View {
        if let value = item.value, !value.isEmpty {
            VStack(alignment: .leading, spacing: 1) {
                Text(item.label).font(.caption2).foregroundStyle(.secondary)
                if let linkURL {
                    Link(value, destination: linkURL)
                        .font(.caption)
                        .accessibilityIdentifier(value)
                } else {
                    Text(value).font(.caption)
                }
            }
        }
    }
}
