import SwiftUI
import WalletSDK

struct ReviewMetadataSection<Content: View>: View {
    let title: String
    let titleAccessibilityIdentifier: String?
    let contentInsets: EdgeInsets
    let content: Content

    init(
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

    var body: some View {
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

struct MetadataIdentityView: View {
    let display: MetadataDisplay?
    let fallbackName: String
    let supportingText: String?

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

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(.systemGray6))
                if let logoURL {
                    AsyncImage(url: logoURL) { phase in
                        switch phase {
                        case let .success(image):
                            image.resizable().scaledToFit()
                        case .empty:
                            ProgressView()
                        case .failure:
                            MetadataLogoFallback(name: name)
                        @unknown default:
                            MetadataLogoFallback(name: name)
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

struct MetadataDetailItem {
    let label: String
    let value: String?
    let linkURI: String?

    init(label: String, value: String?, linkURI: String? = nil) {
        self.label = label
        self.value = value
        self.linkURI = linkURI
    }

    var isVisible: Bool {
        guard let value else { return false }
        return !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

struct MetadataDetailList: View {
    let items: [MetadataDetailItem]

    private var visibleItems: [MetadataDetailItem] {
        items.filter(\.isVisible)
    }

    var body: some View {
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
