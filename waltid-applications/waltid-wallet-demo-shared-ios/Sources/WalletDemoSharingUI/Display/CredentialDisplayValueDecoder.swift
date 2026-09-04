import Foundation
import ImageIO

enum CredentialDisplayValueDecoder {
    static func decodedValue(
        for value: String,
        path: DisplayClaimPath,
        imagePolicy: ImageDecodingPolicy,
        renderJSON: (CredentialDisplayJSONValue, DisplayClaimPath) -> DisplayValue
    ) -> DisplayValue? {
        let payload: EncodedPayload
        switch EncodedPayload.parse(
            value,
            maxImageBytes: imagePolicy.requiresDecodableContent ? maxFallbackImageBytes : nil
        ) {
        case .parsed(let parsedPayload):
            payload = parsedPayload
        case .rejectedImageDataURL:
            return imagePolicy.requiresDecodableContent ? unavailableImageValue : nil
        case .invalid:
            return nil
        }
        let isFallbackImage = imagePolicy.requiresDecodableContent && payload.kind == .imageDataURL
        guard let bytes = payload.base64.decode() else {
            return isFallbackImage ? unavailableImageValue : nil
        }
        if imagePolicy.accepts(payload.kind),
           let mimeType = ImageBytes.mimeType(for: bytes),
           (!imagePolicy.requiresDecodableContent || ImageBytes.isDecodable(bytes, maxPixelCount: maxFallbackImagePixels)) {
            return imageValue(for: bytes, mimeType: mimeType, encoded: payload.base64.value)
        }
        guard let decoded = String(data: bytes, encoding: .utf8), decoded.isMostlyReadable else {
            return isFallbackImage ? unavailableImageValue : nil
        }
        if let json = CredentialDisplayJSONParser.parse(decoded) {
            return renderJSON(json, path)
        }
        return .decodedText(decoded)
    }

    static func imageDisplayValue(for list: [CredentialDisplayJSONValue], roles: Set<ClaimRole>) -> DisplayValue? {
        guard roles.contains(.image),
              let data = byteArrayData(from: list),
              let mimeType = ImageBytes.mimeType(for: data) else {
            return nil
        }
        return imageValue(for: data, mimeType: mimeType)
    }

    private static func byteArrayData(from list: [CredentialDisplayJSONValue]) -> Data? {
        guard !list.isEmpty else { return nil }
        var bytes: [UInt8] = []
        bytes.reserveCapacity(list.count)
        for element in list {
            guard case .number(let number) = element,
                  let value = Int(number) else { return nil }
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

enum ImageDecodingPolicy {
    case schemaImage
    case dataURLFallback
    case disabled
}

private extension ImageDecodingPolicy {
    var requiresDecodableContent: Bool {
        self == .dataURLFallback
    }

    func accepts(_ payloadKind: EncodedPayloadKind) -> Bool {
        switch self {
        case .schemaImage:
            return true
        case .dataURLFallback:
            return payloadKind == .imageDataURL
        case .disabled:
            return false
        }
    }
}

private struct EncodedPayload {
    let kind: EncodedPayloadKind
    let base64: Base64Payload

    static func parse(_ rawValue: String, maxImageBytes: Int? = nil) -> EncodedPayloadParseResult {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let prefixRange = value.range(of: schemePrefix, options: [.anchored, .caseInsensitive]) else {
            guard let base64 = Base64Payload(value) else { return .invalid }
            return .parsed(EncodedPayload(kind: .plainBase64, base64: base64))
        }
        guard let markerRange = value.range(of: base64Marker, options: .caseInsensitive) else {
            guard let metadataEnd = value.firstIndex(of: ",") else { return .invalid }
            let metadata = String(value[prefixRange.upperBound..<metadataEnd])
            return MediaTypeHint.isImage(metadata) ? .rejectedImageDataURL : .invalid
        }

        let metadata = String(value[prefixRange.upperBound..<markerRange.lowerBound])
        let kind: EncodedPayloadKind = MediaTypeHint.isImage(metadata) ? .imageDataURL : .otherDataURL
        let encodedValue = value[markerRange.upperBound...]
        if kind == .imageDataURL,
           let maxImageBytes,
           !Base64Payload.fitsDecodedByteLimit(encodedValue, limit: maxImageBytes) {
            return .rejectedImageDataURL
        }
        guard let base64 = Base64Payload(String(encodedValue)) else {
            return kind == .imageDataURL ? .rejectedImageDataURL : .invalid
        }
        return .parsed(EncodedPayload(kind: kind, base64: base64))
    }

    private static let schemePrefix = "data:"
    private static let base64Marker = ";base64,"
}

private enum EncodedPayloadParseResult {
    case parsed(EncodedPayload)
    case rejectedImageDataURL
    case invalid
}

private enum EncodedPayloadKind {
    case plainBase64
    case imageDataURL
    case otherDataURL
}

private enum MediaTypeHint {
    static func isImage(_ metadata: String) -> Bool {
        mediaType(from: metadata).hasPrefix("image/")
    }

    private static func mediaType(from metadata: String) -> String {
        metadata
            .split(separator: ";", maxSplits: 1, omittingEmptySubsequences: false)
            .first?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased() ?? ""
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

    static func fitsDecodedByteLimit(_ value: Substring, limit: Int) -> Bool {
        let encodedLength = UInt64(value.utf8.count)
        let padding = min(UInt64(value.reversed().prefix { $0 == "=" }.count), 2)
        let decodedSizeUpperBound = ((encodedLength + UInt64(Self.base64BlockSize) - 1) / UInt64(Self.base64BlockSize)) * 3 - padding
        return decodedSizeUpperBound <= UInt64(limit)
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
    static let png = "image/png"
    static let jpeg = "image/jpeg"
    static let gif = "image/gif"
    static let webp = "image/webp"
}

private enum ImageBytes {
    static func isDecodable(_ data: Data, maxPixelCount: Int) -> Bool {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return false }
        guard let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.intValue,
              let height = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.intValue,
              width > 0,
              height > 0,
              width <= maxPixelCount / height else {
            return false
        }
        let options = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: validationImageSize,
        ] as CFDictionary
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options) != nil
    }

    static func mimeType(for data: Data) -> String? {
        let bytes = [UInt8](data.prefix(12))
        let detected: String?
        if bytes.starts(with: [0x89, 0x50, 0x4E, 0x47]) {
            detected = ImageMime.png
        } else if bytes.starts(with: [0xFF, 0xD8, 0xFF]) {
            detected = ImageMime.jpeg
        } else if data.count >= 6, let prefix = String(data: data.prefix(6), encoding: .ascii), ["GIF87a", "GIF89a"].contains(prefix) {
            detected = ImageMime.gif
        } else if data.count >= 12,
                  let riff = String(data: data.prefix(4), encoding: .ascii),
                  let webp = String(data: data.dropFirst(8).prefix(4), encoding: .ascii),
                  riff == "RIFF",
                  webp == "WEBP" {
            detected = ImageMime.webp
        } else {
            detected = nil
        }

        return detected
    }
}

private let maxFallbackImageBytes = 2_000_000
private let maxFallbackImagePixels = 2_048 * 2_048
private let validationImageSize = 64
private let unavailableImageValue = DisplayValue.text(CredentialDisplayText.imageUnavailable)

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
