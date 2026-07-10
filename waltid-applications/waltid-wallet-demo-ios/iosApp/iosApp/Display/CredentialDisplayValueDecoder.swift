import Foundation

enum CredentialDisplayValueDecoder {
    static func decodedValue(
        for value: String,
        path: DisplayClaimPath,
        renderJSON: (Any, DisplayClaimPath) -> DisplayValue
    ) -> DisplayValue? {
        guard let payload = EncodedPayload.parse(value),
              let bytes = payload.base64.decode() else {
            return nil
        }
        if let mimeType = ImageBytes.mimeType(for: bytes, hint: payload.imageMimeTypeHint) {
            return imageValue(for: bytes, mimeType: mimeType, encoded: payload.base64.value)
        }
        guard let decoded = String(data: bytes, encoding: .utf8), decoded.isMostlyReadable else {
            return nil
        }
        if let data = decoded.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) {
            return renderJSON(json, path)
        }
        return .decodedText(decoded)
    }

    static func imageDisplayValue(for list: [Any], roles: Set<ClaimRole>) -> DisplayValue? {
        guard roles.contains(.image),
              let data = byteArrayData(from: list),
              let mimeType = ImageBytes.mimeType(for: data, hint: nil) else {
            return nil
        }
        return imageValue(for: data, mimeType: mimeType)
    }

    private static func byteArrayData(from list: [Any]) -> Data? {
        guard !list.isEmpty else { return nil }
        var bytes: [UInt8] = []
        bytes.reserveCapacity(list.count)
        for element in list {
            guard let number = element as? NSNumber,
                  CFGetTypeID(number) != CFBooleanGetTypeID() else {
                return nil
            }
            let value = number.intValue
            guard (-128...255).contains(value) else {
                return nil
            }
            bytes.append(UInt8(truncatingIfNeeded: value))
        }
        return Data(bytes)
    }

    private static func imageValue(for data: Data, mimeType: String, encoded: String? = nil) -> DisplayValue {
        return .image(
            encoded: encoded ?? data.base64EncodedString(),
            data: data,
            mimeType: mimeType,
            byteCount: data.count
        )
    }
}

private struct EncodedPayload {
    let imageMimeTypeHint: String?
    let base64: Base64Payload

    static func parse(_ rawValue: String) -> EncodedPayload? {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let prefixRange = value.range(of: schemePrefix, options: [.anchored, .caseInsensitive]),
              let markerRange = value.range(of: base64Marker, options: .caseInsensitive) else {
            return Base64Payload(value).map {
                EncodedPayload(imageMimeTypeHint: nil, base64: $0)
            }
        }

        let metadata = String(value[prefixRange.upperBound..<markerRange.lowerBound])
        guard let base64 = Base64Payload(String(value[markerRange.upperBound...])) else {
            return nil
        }
        return EncodedPayload(
            imageMimeTypeHint: MediaTypeHint.imageType(from: metadata),
            base64: base64
        )
    }

    private static let schemePrefix = "data:"
    private static let base64Marker = ";base64,"
}

private enum MediaTypeHint {
    static func imageType(from metadata: String) -> String? {
        guard let rawMediaType = metadata
            .split(separator: ";", maxSplits: 1, omittingEmptySubsequences: false)
            .first?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased(),
            rawMediaType.hasPrefix(ImageMime.prefix) else {
            return nil
        }
        return rawMediaType
    }
}

private struct Base64Payload {
    let value: String

    init?(_ rawValue: String) {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard Self.looksValid(value) else {
            return nil
        }
        self.value = value
    }

    func decode() -> Data? {
        let normalized = value
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let padded = normalized.padding(
            toLength: normalized.count + ((Self.base64BlockSize - normalized.count % Self.base64BlockSize) % Self.base64BlockSize),
            withPad: "=",
            startingAt: 0
        )
        return Data(base64Encoded: padded)
    }

    private static func looksValid(_ value: String) -> Bool {
        guard value.count >= minimumPayloadLength, value.count % base64BlockSize != invalidBase64Remainder else { return false }
        return value.allSatisfy { character in
            character.isLetter || character.isNumber || ["+", "/", "-", "_", "="].contains(character)
        }
    }

    private static let minimumPayloadLength = 12
    private static let base64BlockSize = 4
    private static let invalidBase64Remainder = 1
}

private enum ImageMime {
    static let prefix = "image/"
    static let png = "image/png"
    static let jpeg = "image/jpeg"
    static let gif = "image/gif"
    static let webp = "image/webp"
}

private enum ImageBytes {
    static func mimeType(for data: Data, hint: String?) -> String? {
        if let hint, hint.hasPrefix(ImageMime.prefix) {
            return hint
        }
        let bytes = [UInt8](data.prefix(12))
        if bytes.starts(with: [0x89, 0x50, 0x4E, 0x47]) { return ImageMime.png }
        if bytes.starts(with: [0xFF, 0xD8, 0xFF]) { return ImageMime.jpeg }
        if data.count >= 6, let prefix = String(data: data.prefix(6), encoding: .ascii), ["GIF87a", "GIF89a"].contains(prefix) { return ImageMime.gif }
        if data.count >= 12,
           let riff = String(data: data.prefix(4), encoding: .ascii),
           let webp = String(data: data.dropFirst(8).prefix(4), encoding: .ascii),
           riff == "RIFF",
           webp == "WEBP" {
            return ImageMime.webp
        }
        return nil
    }
}

private extension String {
    var isMostlyReadable: Bool {
        let allowedControls = Set(["\n", "\r", "\t"].compactMap { $0.unicodeScalars.first })
        return !isEmpty &&
            unicodeScalars.filter {
                !CharacterSet.controlCharacters.contains($0) || allowedControls.contains($0)
            }.count >= Int(Double(unicodeScalars.count) * Self.readableCharacterRatio)
    }

    private static let readableCharacterRatio = 0.9
}
