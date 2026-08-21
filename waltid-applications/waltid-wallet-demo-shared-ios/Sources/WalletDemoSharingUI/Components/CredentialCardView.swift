import SwiftUI
import UIKit
import WalletSDK

public let id1AspectRatio: CGFloat = 1.586
public let credentialCardPeek: CGFloat = 56
public let defaultWaltCardBlue = Color(red: 27 / 255, green: 79 / 255, blue: 219 / 255)

public struct CredentialCardView: View {
    public let details: CredentialDetails
    public var compact: Bool = false

    public init(details: CredentialDetails, compact: Bool = false) {
        self.details = details
        self.compact = compact
    }

    public init(credential: Credential, compact: Bool = false) {
        self.details = CredentialDisplayNormalizer.details(for: credential)
        self.compact = compact
    }

    public var body: some View {
        CredentialCardArtView(summary: details.cardSummary, compact: compact)
            .accessibilityIdentifier(WalletAccessibilityID.credentialCard(details.id))
    }
}

public struct CredentialCardArtView: View {
    public let summary: CredentialCardSummary
    public var compact: Bool = false

    public init(summary: CredentialCardSummary, compact: Bool = false) {
        self.summary = summary
        self.compact = compact
    }

    public var body: some View {
        let corner: CGFloat = compact ? 10 : 14
        let padding: CGFloat = compact ? 10 : 16
        let nameSize: CGFloat = compact ? 13 : 18
        let background = Color(css: summary.backgroundColor) ?? defaultWaltCardBlue
        let label = Color(css: summary.textColor) ?? .white

        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                background
                backgroundImage(in: proxy.size)
                HStack(alignment: .top) {
                    Text(summary.title)
                        .font(.system(size: nameSize, weight: .semibold))
                        .foregroundStyle(label)
                        .lineLimit(2)
                    Spacer(minLength: 8)
                    logo(labelColor: label)
                }
                .padding(padding)
            }
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
            .shadow(color: .black.opacity(compact ? 0.12 : 0.22), radius: compact ? 2 : 6, y: 2)
        }
        .aspectRatio(id1AspectRatio, contentMode: .fit)
    }

    @ViewBuilder
    private func backgroundImage(in size: CGSize) -> some View {
        if let url = httpsURL(summary.backgroundImageURI) {
            AsyncImage(url: url) { phase in
                switch phase {
                case let .success(image):
                    image
                        .resizable()
                        .scaledToFill()
                        .frame(width: size.width, height: size.height)
                        .clipped()
                default:
                    EmptyView()
                }
            }
        }
    }

    @ViewBuilder
    private func logo(labelColor: Color) -> some View {
        let logoSize: CGFloat = compact ? 22 : 36
        if let url = httpsURL(summary.logoURI) {
            AsyncImage(url: url) { phase in
                switch phase {
                case let .success(image):
                    image.resizable().scaledToFit()
                default:
                    DefaultWaltLogo(color: labelColor)
                }
            }
            .frame(width: logoSize, height: logoSize)
        } else {
            DefaultWaltLogo(color: labelColor)
        }
    }
}

public struct CredentialCardButton: View {
    public let details: CredentialDetails
    public var compact: Bool = false
    public let action: () -> Void
    public var onLongPress: (() -> Void)?

    public init(
        details: CredentialDetails,
        compact: Bool = false,
        onLongPress: (() -> Void)? = nil,
        action: @escaping () -> Void
    ) {
        self.details = details
        self.compact = compact
        self.onLongPress = onLongPress
        self.action = action
    }

    public var body: some View {
        CredentialCardView(details: details, compact: compact)
            .contentShape(Rectangle())
            .onTapGesture(perform: action)
            .onLongPressGesture {
                onLongPress?()
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier(WalletAccessibilityID.credentialCard(details.id))
            .accessibilityAddTraits(.isButton)
    }
}

public struct CredentialCardStackView: View {
    public let details: [CredentialDetails]
    public let onOpenDetails: (String) -> Void

    @State private var expandedID: String?

    public init(details: [CredentialDetails], onOpenDetails: @escaping (String) -> Void) {
        self.details = details
        self.onOpenDetails = onOpenDetails
    }

    public var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let cardHeight = width / id1AspectRatio
            let offsets = cardOffsets(
                count: details.count,
                expandedIndex: details.firstIndex(where: { $0.id == expandedID }),
                peek: credentialCardPeek,
                cardHeight: cardHeight
            )
            let stackHeight = (offsets.last ?? 0) + cardHeight
            let frontID = expandedID ?? details.last?.id

            ZStack(alignment: .topLeading) {
                ForEach(Array(details.enumerated()), id: \.element.id) { index, item in
                    let isFront = item.id == frontID
                    CredentialCardButton(
                        details: item,
                        onLongPress: { onOpenDetails(item.id) }
                    ) {
                        if isFront {
                            onOpenDetails(item.id)
                        } else {
                            expandedID = item.id
                        }
                    }
                    .frame(width: width)
                    .offset(y: offsets[index])
                    .zIndex(isFront ? Double(details.count) : Double(index))
                }
            }
            .frame(width: width, height: stackHeight, alignment: .top)
        }
        .frame(maxWidth: .infinity)
        .frame(height: stackHeight(forWidth: UIScreen.main.bounds.width - 40))
    }

    private func stackHeight(forWidth width: CGFloat) -> CGFloat {
        let cardHeight = width / id1AspectRatio
        let offsets = cardOffsets(
            count: details.count,
            expandedIndex: details.firstIndex(where: { $0.id == expandedID }),
            peek: credentialCardPeek,
            cardHeight: cardHeight
        )
        return (offsets.last ?? 0) + cardHeight
    }
}

public func cardOffsets(
    count: Int,
    expandedIndex: Int?,
    peek: CGFloat,
    cardHeight: CGFloat
) -> [CGFloat] {
    var y: CGFloat = 0
    return (0..<count).map { index in
        let offset = y
        let isExpanded = expandedIndex == index
        let isLast = index == count - 1
        y += (isExpanded || (expandedIndex == nil && isLast)) ? cardHeight : peek
        return offset
    }
}

public struct CredentialPortraitView: View {
    public let summary: CredentialCardSummary
    public let size: CGFloat

    public init(summary: CredentialCardSummary, size: CGFloat) {
        self.summary = summary
        self.size = size
    }

    public var body: some View {
        Group {
            if let data = summary.portraitData, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
            } else {
                Image(systemName: "person.text.rectangle")
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(width: size, height: size)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color(.separator), lineWidth: 1)
        )
        .accessibilityLabel("Credential portrait")
    }
}

private struct DefaultWaltLogo: View {
    let color: Color

    var body: some View {
        Text("walt.id")
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(color)
            .lineLimit(1)
    }
}

private func httpsURL(_ value: String?) -> URL? {
    guard let value,
          let url = URL(string: value),
          url.scheme?.lowercased() == "https" else {
        return nil
    }
    return url
}

private extension Color {
    init?(css value: String?) {
        guard let hex = value?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: ""),
              !hex.isEmpty else {
            return nil
        }
        let normalized: String
        switch hex.count {
        case 3:
            normalized = hex.map { "\($0)\($0)" }.joined()
        case 6:
            normalized = hex
        case 8:
            normalized = String(hex.suffix(6))
        default:
            return nil
        }
        guard let rgb = UInt32(normalized, radix: 16) else { return nil }
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }
}
