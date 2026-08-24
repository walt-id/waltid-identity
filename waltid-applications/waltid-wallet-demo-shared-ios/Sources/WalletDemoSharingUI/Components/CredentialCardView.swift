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
    @State private var loadedMetadataArt: UIImage?
    @State private var metadataArtFailed = false

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
        let logoSize: CGFloat = compact ? 22 : 36

        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                background
                if let loadedMetadataArt {
                    Image(uiImage: loadedMetadataArt)
                        .resizable()
                        .scaledToFill()
                        .frame(width: proxy.size.width, height: proxy.size.height)
                        .clipped()
                } else if showsConstructedCardArtOverlay(
                    backgroundImageURI: summary.backgroundImageURI,
                    hasLoadedMetadataArt: false,
                    metadataArtFailed: metadataArtFailed
                ) {
                    Text(summary.title)
                        .font(.system(size: nameSize, weight: .semibold))
                        .foregroundStyle(label)
                        .lineLimit(2)
                        .padding(padding)
                    DefaultWaltLogo()
                        .frame(width: logoSize, height: logoSize)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                        .padding(padding)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
            .shadow(color: .black.opacity(compact ? 0.22 : 0.34), radius: compact ? 10 : 16, y: 6)
        }
        .aspectRatio(id1AspectRatio, contentMode: .fit)
        .task(id: summary.backgroundImageURI) {
            metadataArtFailed = false
            loadedMetadataArt = await loadMetadataArt(from: summary.backgroundImageURI)
            metadataArtFailed = loadedMetadataArt == nil && httpsURL(summary.backgroundImageURI) != nil
        }
    }
}

public struct CredentialCardButton: View {
    public let details: CredentialDetails
    public var compact: Bool = false
    public let action: () -> Void

    public init(
        details: CredentialDetails,
        compact: Bool = false,
        action: @escaping () -> Void
    ) {
        self.details = details
        self.compact = compact
        self.action = action
    }

    public var body: some View {
        CredentialCardView(details: details, compact: compact)
            .contentShape(Rectangle())
            .onTapGesture(perform: action)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier(WalletAccessibilityID.credentialCard(details.id))
            .accessibilityAddTraits(.isButton)
    }
}

public struct CredentialCardStackView: View {
    public let details: [CredentialDetails]
    public let onOpenDetails: (String) -> Void
    public var expandedID: String? = nil
    public var othersHidden: Bool = false
    public var selectedAtTop: Bool = false

    public init(
        details: [CredentialDetails],
        expandedID: String? = nil,
        othersHidden: Bool = false,
        selectedAtTop: Bool = false,
        onOpenDetails: @escaping (String) -> Void
    ) {
        self.details = details
        self.expandedID = expandedID
        self.othersHidden = othersHidden
        self.selectedAtTop = selectedAtTop
        self.onOpenDetails = onOpenDetails
    }

    public var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let cardHeight = width / id1AspectRatio
            let offsets = cardOffsets(
                count: details.count,
                peek: credentialCardPeek,
                cardHeight: cardHeight
            )
            let restHeight = (offsets.last ?? 0) + cardHeight
            let stackHeight = selectedAtTop ? cardHeight : restHeight

            ZStack(alignment: .topLeading) {
                ForEach(Array(details.enumerated()), id: \.element.id) { index, item in
                    let isSelected = item.id == expandedID
                    CredentialCardButton(details: item) {
                        onOpenDetails(item.id)
                    }
                    .frame(width: width)
                    .offset(y: isSelected && selectedAtTop ? 0 : offsets[index])
                    .opacity(isSelected || !othersHidden ? 1 : 0)
                    .zIndex(isSelected ? Double(details.count) : Double(index))
                    .allowsHitTesting(isSelected || !othersHidden)
                }
            }
            .frame(width: width, height: stackHeight, alignment: .top)
            .clipped()
            .animation(.spring(response: 0.42, dampingFraction: 0.86), value: selectedAtTop)
            .animation(.easeInOut(duration: 0.22), value: othersHidden)
        }
        .frame(maxWidth: .infinity)
        .frame(height: displayedHeight(forWidth: UIScreen.main.bounds.width - 40))
        .animation(.spring(response: 0.42, dampingFraction: 0.86), value: selectedAtTop)
    }

    private func displayedHeight(forWidth width: CGFloat) -> CGFloat {
        let cardHeight = width / id1AspectRatio
        if selectedAtTop {
            return cardHeight
        }
        let offsets = cardOffsets(
            count: details.count,
            peek: credentialCardPeek,
            cardHeight: cardHeight
        )
        return (offsets.last ?? 0) + cardHeight
    }
}

public func cardOffsets(
    count: Int,
    peek: CGFloat,
    cardHeight: CGFloat
) -> [CGFloat] {
    var y: CGFloat = 0
    return (0..<count).map { index in
        let offset = y
        let isLast = index == count - 1
        y += isLast ? cardHeight : peek
        return offset
    }
}

private struct DefaultWaltLogo: View {
    var body: some View {
        if let image = bundledWaltLogo() {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .accessibilityLabel("walt.id")
        }
    }
}

private func bundledWaltLogo() -> UIImage? {
    guard let url = Bundle.module.url(forResource: "waltid_logo", withExtension: "png") else {
        return nil
    }
    return UIImage(contentsOfFile: url.path)
}

public func showsConstructedCardArtOverlay(
    backgroundImageURI: String?,
    hasLoadedMetadataArt: Bool,
    metadataArtFailed: Bool
) -> Bool {
    if hasLoadedMetadataArt { return false }
    if httpsURL(backgroundImageURI) == nil { return true }
    return metadataArtFailed
}

public func prefetchCredentialCardArt(uris: [String?]) async {
    await withTaskGroup(of: Void.self) { group in
        Set(uris.compactMap { $0 }).forEach { uri in
            group.addTask { _ = await loadMetadataArt(from: uri) }
        }
    }
}

private func loadMetadataArt(from value: String?) async -> UIImage? {
    guard let url = httpsURL(value) else { return nil }
    do {
        let (data, _) = try await URLSession.shared.data(from: url)
        guard let image = UIImage(data: data), image.size.height > 0 else { return nil }
        let aspect = image.size.width / image.size.height
        return (1.2...2.0).contains(aspect) ? image : nil
    } catch {
        return nil
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
