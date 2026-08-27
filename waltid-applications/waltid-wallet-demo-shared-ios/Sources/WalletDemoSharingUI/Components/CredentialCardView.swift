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

    @State private var stackWidth: CGFloat = 0

    public var body: some View {
        let width = stackWidth
        let stackHeight = width > 0 ? displayedHeight(forWidth: width) : 0

        ZStack(alignment: .topLeading) {
            ForEach(Array(details.enumerated()), id: \.element.id) { index, item in
                let isSelected = item.id == expandedID
                CredentialCardButton(details: item) {
                    onOpenDetails(item.id)
                }
                .frame(width: width > 0 ? width : nil)
                .offset(y: isSelected && selectedAtTop ? 0 : cardOffsets(
                    count: details.count,
                    peek: credentialCardPeek,
                    cardHeight: width > 0 ? width / id1AspectRatio : 0
                )[index])
                .opacity(isSelected || !othersHidden ? 1 : 0)
                .zIndex(isSelected ? Double(details.count) : Double(index))
                .allowsHitTesting(isSelected || !othersHidden)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: stackHeight > 0 ? stackHeight : nil, alignment: .top)
        .background(
            GeometryReader { proxy in
                Color.clear.preference(key: CredentialCardStackWidthKey.self, value: proxy.size.width)
            }
        )
        .onPreferenceChange(CredentialCardStackWidthKey.self) { stackWidth = $0 }
        .animation(.spring(response: 0.42, dampingFraction: 0.86), value: selectedAtTop)
        .animation(.easeInOut(duration: 0.22), value: othersHidden)
    }

    private func displayedHeight(forWidth width: CGFloat) -> CGFloat {
        let cardHeight = width / id1AspectRatio
        if selectedAtTop && othersHidden {
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

private struct CredentialCardStackWidthKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
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

private let maxCredentialCardArtBytes = 2_000_000
private let maxCredentialCardArtPixels: CGFloat = 2_048 * 2_048
private let credentialCardArtTimeout: TimeInterval = 5

private func loadMetadataArt(from value: String?) async -> UIImage? {
    guard let url = httpsURL(value) else { return nil }
    var request = URLRequest(url: url)
    request.timeoutInterval = credentialCardArtTimeout
    do {
        let (bytes, response) = try await URLSession.shared.bytes(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            return nil
        }
        if http.expectedContentLength > maxCredentialCardArtBytes {
            return nil
        }
        var data = Data()
        data.reserveCapacity(min(Int(max(http.expectedContentLength, 0)), maxCredentialCardArtBytes))
        for try await byte in bytes {
            data.append(byte)
            if data.count > maxCredentialCardArtBytes {
                return nil
            }
        }
        guard let image = UIImage(data: data), image.size.height > 0 else { return nil }
        let pixelCount = image.size.width * image.size.height * image.scale * image.scale
        guard pixelCount <= maxCredentialCardArtPixels else { return nil }
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
        guard let parsed = CssColorParser.parse(value) else { return nil }
        self.init(
            red: parsed.red,
            green: parsed.green,
            blue: parsed.blue,
            opacity: parsed.alpha
        )
    }
}
