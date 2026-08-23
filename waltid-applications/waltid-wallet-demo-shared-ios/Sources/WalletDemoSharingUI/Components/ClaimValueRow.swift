import SwiftUI
import UIKit

public struct ClaimValueRow: View {
    public let item: ClaimItem

    public init(item: ClaimItem) {
        self.item = item
    }

    private var inlineValue: String? {
        switch item.value {
        case .bool(let value): return value ? "Yes" : "No"
        case .decodedText(let value), .text(let value), .number(let value): return value
        case .null: return "Not provided"
        default: return nil
        }
    }

    @ViewBuilder
    public var body: some View {
        if let inlineValue, item.label.count <= 28, inlineValue.count <= 38 {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                label.frame(maxWidth: .infinity, alignment: .leading)
                Text(inlineValue)
                    .font(.caption)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .multilineTextAlignment(.trailing)
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                label.frame(maxWidth: .infinity, alignment: .leading)
                ClaimValueView(value: item.value, path: item.path)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var label: some View {
        Text(item.label)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.secondary)
            .accessibilityIdentifier(WalletAccessibilityID.claim(item.path.id))
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
            VStack(alignment: .leading, spacing: 4) {
                ForEach(Array(values.enumerated()), id: \.offset) { index, value in
                    HStack(alignment: .top, spacing: 4) {
                        Text("\(index + 1).")
                            .font(.caption)
                        ClaimValueView(value: value, path: path.indexedChild(index))
                    }
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

private struct ImageValue: View {
    let data: Data
    let mimeType: String
    let byteCount: Int
    let path: ClaimItemPath

    var body: some View {
        if let image {
            content(image: image)
                .accessibilityIdentifier(WalletAccessibilityID.claimImage(path.id))
        } else {
            content(image: nil)
        }
    }

    private func content(image: UIImage?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if let image {
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
                    .accessibilityLabel("Credential image")
            }
            Text(mimeType)
                .font(.caption.weight(.medium))
            Text(metadata)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
    }

    private var image: UIImage? {
        return UIImage(data: data)
    }

    private var metadata: String {
        "\(byteCount) bytes"
    }
}
