import SwiftUI
import WalletSDK

struct ReviewMetadataSection<Content: View>: View {
    let title: String
    let content: Content

    init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tint)

            VStack(alignment: .leading, spacing: 8) {
                content
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 8))
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

struct MetadataDetailLine: View {
    let label: String
    let value: String?

    var body: some View {
        if let value, !value.isEmpty {
            VStack(alignment: .leading, spacing: 1) {
                Text(label).font(.caption2).foregroundStyle(.secondary)
                Text(value).font(.caption)
            }
        }
    }
}
