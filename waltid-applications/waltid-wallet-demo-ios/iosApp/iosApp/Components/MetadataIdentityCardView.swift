import SwiftUI

struct MetadataIdentityCardView: View {
    let identity: MetadataIdentityDisplay

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            MetadataLogoView(identity: identity, size: 56)

            VStack(alignment: .leading, spacing: 5) {
                Text(identity.title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(2)
                Text(identity.subtitle)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 0)
        }
        .padding()
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct MetadataLogoView: View {
    let identity: MetadataIdentityDisplay
    let size: CGFloat

    var body: some View {
        Group {
            if let logoURI = identity.logoURI, let url = URL(string: logoURI) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                    default:
                        InitialsView(identity: identity)
                    }
                }
            } else {
                InitialsView(identity: identity)
            }
        }
        .frame(width: size, height: size)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color(.separator), lineWidth: 1)
        )
        .accessibilityLabel("\(identity.title) logo")
    }
}

private struct InitialsView: View {
    let identity: MetadataIdentityDisplay

    var body: some View {
        Text(identity.title.initials)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.secondary)
    }
}

private extension String {
    var initials: String {
        let letters = split(whereSeparator: \.isWhitespace)
            .prefix(2)
            .compactMap(\.first)
            .map { String($0).uppercased() }
            .joined()
        return letters.isEmpty ? "ID" : letters
    }
}
