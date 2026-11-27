import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let bytes = [UInt8](self)
        let array = KotlinByteArray(size: Int32(bytes.count))

        for (index, byte) in bytes.enumerated() {
            array.set(index: Int32(index), value: Int8(bitPattern: byte))
        }

        return array
    }
}

func swiftArray<T>(_ value: Any, of type: T.Type) -> [T] {
    if let values = value as? [T] {
        return values
    }
    if let values = value as? NSArray {
        return values.compactMap { $0 as? T }
    }
    return []
}

/// Authoritative protocol and result collections must retain every element supplied by the core.
func requiredBridgeArray<T>(_ value: Any, of type: T.Type) throws -> [T] {
    guard let values = value as? [T] else {
        throw WalletError.internalFailure("Wallet core returned an incompatible collection of \(type).")
    }
    return values
}

extension KotlinInstant {
    func toDate() -> Date {
        Date(timeIntervalSince1970: TimeInterval(epochSeconds) + TimeInterval(nanosecondsOfSecond) / 1_000_000_000)
    }
}

extension Data {
    init?(base64URLEncoded value: String) {
        var base64 = value.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        base64.append(String(repeating: "=", count: (4 - base64.count % 4) % 4))
        self.init(base64Encoded: base64)
    }

    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

func decodedBase64URL(_ value: String, context: String) throws -> Data {
    guard let data = Data(base64URLEncoded: value) else {
        throw WalletError.internalFailure("Wallet core returned an invalid \(context)")
    }
    return data
}

func requiredBridgeSet<T: Hashable>(_ value: Any, of type: T.Type) throws -> Set<T> {
    guard let values = value as? Set<T> else {
        throw WalletError.internalFailure("Wallet core returned an incompatible set of \(type).")
    }
    return values
}
#endif
