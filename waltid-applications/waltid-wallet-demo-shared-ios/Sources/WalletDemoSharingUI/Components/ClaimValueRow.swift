import ImageIO
import SwiftUI
import UIKit

public struct ClaimValueRow: View {
    public let item: ClaimItem

    public init(item: ClaimItem) {
        self.item = item
    }

    public var body: some View {
        CredentialDataRow(item.label, labelIdentifier: WalletAccessibilityID.claim(item.path.id)) {
            ClaimValueView(value: item.value, path: item.path)
        }
    }
}

private struct ClaimValueView: View {
    let value: DisplayValue
    let path: ClaimItemPath

    var body: some View {
        switch value {
        case .bool(let value):
            Text(value ? "Yes" : "No")
                .font(.body)
        case .decodedText(let value), .text(let value), .number(let value):
            Text(value.isEmpty ? String(localized: "Empty text", bundle: .module) : value)
                .font(.body)
        case .deferredImage(let source):
            DeferredImageValue(source: source, path: path)
        case .image(_, let data, let mimeType, let byteCount):
            ImageValue(data: data, mimeType: mimeType, byteCount: byteCount, path: path)
        case .list(let values):
            ClaimListView(values: values, path: path)
        case .null:
            Text("No value supplied", bundle: .module)
                .font(.body)
                .foregroundStyle(.secondary)
        case .object(let entries):
            LazyVStack(alignment: .leading, spacing: 6) {
                if entries.isEmpty { Text("No fields", bundle: .module) }
                ForEach(entries) { entry in
                    ClaimValueRow(item: entry)
                }
            }
        case .raw(let value):
            Text(value)
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)
                .textSelection(.enabled)
        }
    }
}

struct DisplayListPreview {
    static let maxItems = 25

    let values: [DisplayValue]
    let overflowLabel: String?

    init(values: [DisplayValue], limit: Int = Self.maxItems) {
        self.values = Array(values.prefix(limit))
        self.overflowLabel = values.count > limit
            ? "Showing first \(limit) of \(values.count) items"
            : nil
    }
}

private struct ClaimListView: View {
    let values: [DisplayValue]
    let path: ClaimItemPath
    @State private var limit = DisplayListPreview.maxItems

    var body: some View {
        let preview = DisplayListPreview(values: values, limit: limit)
        LazyVStack(alignment: .leading, spacing: 4) {
            if values.isEmpty { Text("No items", bundle: .module) }
            ForEach(Array(preview.values.enumerated()), id: \.offset) { index, value in
                HStack(alignment: .top, spacing: 4) {
                    Text("\(index + 1).")
                    ClaimValueView(value: value, path: path.indexedChild(index))
                }
            }
            if let label = preview.overflowLabel {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Button("Show \(min(DisplayListPreview.maxItems, values.count - limit)) more") {
                    limit = min(limit + DisplayListPreview.maxItems, values.count)
                }.frame(minHeight: 44)
            }
        }.onChange(of: path) { _ in limit = DisplayListPreview.maxItems }
    }
}

private struct ClaimImagePlaceholder: View {
    let loading: Bool

    var body: some View {
        VStack(spacing: 8) {
            if loading { ProgressView() } else { Image(systemName: "photo.badge.exclamationmark") }
            Text(loading ? String(localized: "Loading image", bundle: .module) : String(localized: "Image unavailable", bundle: .module)).font(.caption)
        }
        .foregroundStyle(.secondary)
        .frame(width: 112, height: 112)
        .accessibilityElement(children: .combine)
    }
}

private struct DeferredImageValue: View {
    let source: DeferredCredentialImage
    let path: ClaimItemPath
    @State private var resolved: DisplayValue?

    var body: some View {
        Group {
            if let resolved {
                ClaimValueView(value: resolved, path: path)
            } else {
                ClaimImagePlaceholder(loading: true)
            }
        }
        .task(id: ObjectIdentifier(source)) {
            resolved = nil
            let value = await CredentialImageDecoder.shared.resolve(source)
            guard !Task.isCancelled else { return }
            resolved = value
        }
    }
}

// Serialize expensive decodes and skip work that was cancelled while waiting for the actor.
private actor CredentialImageDecoder {
    static let shared = CredentialImageDecoder()

    func resolve(_ source: DeferredCredentialImage) -> DisplayValue? {
        guard !Task.isCancelled else { return nil }
        return autoreleasepool { source.resolve() }
    }

    func thumbnail(_ data: Data, maxPixelSize: Int) -> UIImage? {
        guard !Task.isCancelled else { return nil }
        return autoreleasepool { credentialThumbnail(data, maxPixelSize: maxPixelSize) }
    }
}

private func credentialThumbnail(_ data: Data, maxPixelSize: Int) -> UIImage? {
    guard let source = CGImageSourceCreateWithData(data as CFData,
        [kCGImageSourceShouldCache: false] as CFDictionary) else { return nil }
    let options: [CFString: Any] = [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceShouldCacheImmediately: true,
        kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
    ]
    guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
    return UIImage(cgImage: image)
}

private struct ImageValue: View {
    let data: Data
    let mimeType: String
    let byteCount: Int
    let path: ClaimItemPath
    @State private var viewerOpen = false
    private enum LoadState { case loading, loaded(UIImage), failed }
    @State private var state = LoadState.loading

    private var image: UIImage? {
        if case .loaded(let image) = state { return image }
        return nil
    }

    var body: some View {
        content(image: image)
            .task(id: data) {
                state = .loading
                let thumbnail = await CredentialImageDecoder.shared.thumbnail(data, maxPixelSize: 336)
                guard !Task.isCancelled else { return }
                state = thumbnail.map(LoadState.loaded) ?? .failed
            }
    }

    private func content(image: UIImage?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if let image {
                Button {
                    viewerOpen = true
                } label: {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 112, height: 112)
                        .background(Color(.systemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color(.separator), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Credential image")
                .accessibilityHint("Opens the image full screen")
                .accessibilityIdentifier(WalletAccessibilityID.claimImage(path.id))
                .preference(key: CredentialImageReadinessKey.self, value: [path.id])
            } else {
                ClaimImagePlaceholder(loading: { if case .loading = state { return true }; return false }())
            }

        }
        .fullScreenCover(isPresented: $viewerOpen) {
            if let image {
                CredentialImageViewer(
                    data: data,
                    preview: image,
                    mimeType: mimeType,
                    byteCount: byteCount,
                    path: path,
                    onDismiss: { viewerOpen = false }
                )
                .transparentPresentationBackground()
            }
        }
    }

}

/// Propagates decoded thumbnail readiness through the actual mounted view hierarchy.
struct CredentialImageReadinessKey: PreferenceKey {
    static let defaultValue: Set<String> = []
    static func reduce(value: inout Set<String>, nextValue: () -> Set<String>) {
        value.formUnion(nextValue())
    }
}

private struct CredentialImageViewer: View {
    let data: Data
    let preview: UIImage
    let mimeType: String
    let byteCount: Int
    let path: ClaimItemPath
    let onDismiss: () -> Void
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color.black.opacity(0.72)
                .ignoresSafeArea()
                .accessibilityLabel("Credential image viewer")
                .accessibilityIdentifier(WalletAccessibilityID.claimImageViewer(path.id))

            Image(uiImage: image ?? preview)
                .resizable()
                .scaledToFit()
                .padding(.horizontal, 24)
                .padding(.vertical, 64)
                .accessibilityLabel("Full-screen credential image")

            VStack {
                HStack {
                    Spacer()
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .background(Color.black.opacity(0.48), in: Circle())
                    }
                    .accessibilityLabel("Close full-screen credential image")
                    .accessibilityIdentifier(WalletAccessibilityID.claimImageViewerClose(path.id))
                }
                Spacer()
                Text("\(mimeType) · \(byteCount) bytes").font(.footnote).foregroundStyle(.white)
            }
            .padding(16)
        }
        .task(id: data) {
            let fullScreenImage = await CredentialImageDecoder.shared.thumbnail(data, maxPixelSize: 2048)
            guard !Task.isCancelled else { return }
            image = fullScreenImage
        }
    }
}

private extension View {
    @ViewBuilder
    func transparentPresentationBackground() -> some View {
        if #available(iOS 16.4, *) {
            presentationBackground(.clear)
        } else {
            background(TransparentPresentationBackground())
        }
    }
}

private struct TransparentPresentationBackground: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        DispatchQueue.main.async {
            view.superview?.superview?.backgroundColor = .clear
        }
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}
