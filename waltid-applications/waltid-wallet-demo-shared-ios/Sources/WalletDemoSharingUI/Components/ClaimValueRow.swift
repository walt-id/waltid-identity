import SwiftUI
import UIKit

public struct ClaimValueRow: View {
    public let item: ClaimItem

    public init(item: ClaimItem) {
        self.item = item
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(item.label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier(WalletAccessibilityID.claim(item.path.id))
            ClaimValueView(value: item.value, path: item.path)
                .frame(maxWidth: .infinity, alignment: .leading)
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
                .font(.caption)
        case .decodedText(let value), .text(let value), .number(let value):
            Text(value)
                .font(.caption)
        case .image(_, let data, let mimeType, let byteCount):
            ImageValue(data: data, mimeType: mimeType, byteCount: byteCount, path: path)
        case .list(let values):
            let preview = DisplayListPreview(values: values)
            VStack(alignment: .leading, spacing: 4) {
                ForEach(Array(preview.values.enumerated()), id: \.offset) { index, value in
                    HStack(alignment: .top, spacing: 4) {
                        Text("\(index + 1).")
                            .font(.caption)
                        ClaimValueView(value: value, path: path.indexedChild(index))
                    }
                }
                if let overflowLabel = preview.overflowLabel {
                    Text(overflowLabel)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        case .null:
            Text("Not provided")
                .font(.caption)
                .foregroundStyle(.secondary)
        case .object(let entries):
            VStack(alignment: .leading, spacing: 6) {
                ForEach(entries) { entry in
                    ClaimValueRow(item: entry)
                }
            }
        case .raw(let value):
            Text(value)
                .font(.caption.monospaced())
                .foregroundStyle(.secondary)
                .textSelection(.enabled)
        }
    }
}

struct DisplayListPreview {
    static let maxItems = 25

    let values: [DisplayValue]
    let overflowLabel: String?

    init(values: [DisplayValue]) {
        self.values = Array(values.prefix(Self.maxItems))
        self.overflowLabel = values.count > Self.maxItems
            ? "Showing first \(Self.maxItems) of \(values.count) items"
            : nil
    }
}

private struct ImageValue: View {
    let data: Data
    let mimeType: String
    let byteCount: Int
    let path: ClaimItemPath
    @State private var viewerOpen = false

    var body: some View {
        if let image {
            content(image: image)
        } else {
            content(image: nil)
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
            }
            Text(mimeType)
                .font(.caption.weight(.medium))
            Text(metadata)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .fullScreenCover(isPresented: $viewerOpen) {
            if let image {
                CredentialImageViewer(
                    image: image,
                    path: path,
                    onDismiss: { viewerOpen = false }
                )
                .transparentPresentationBackground()
            }
        }
    }

    private var image: UIImage? {
        return UIImage(data: data)
    }

    private var metadata: String {
        "\(byteCount) bytes"
    }
}

private struct CredentialImageViewer: View {
    let image: UIImage
    let path: ClaimItemPath
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.72)
                .ignoresSafeArea()
                .accessibilityLabel("Credential image viewer")
                .accessibilityIdentifier(WalletAccessibilityID.claimImageViewer(path.id))

            Image(uiImage: image)
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
            }
            .padding(16)
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
