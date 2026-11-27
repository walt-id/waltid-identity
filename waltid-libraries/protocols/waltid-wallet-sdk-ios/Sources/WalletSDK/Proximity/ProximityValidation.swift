import Foundation
import Security

// Match Kotlin Char.isWhitespace for request identifiers accepted by the shared SDK.
// Foundation's whitespace set differs (for example, U+200B and U+0085).
func isKotlinNonBlank(_ value: String) -> Bool {
    value.unicodeScalars.contains { scalar in
        switch scalar.properties.generalCategory {
        case .spaceSeparator, .lineSeparator, .paragraphSeparator:
            return false
        default:
            return !(0x0009...0x000D).contains(scalar.value) && !(0x001C...0x001F).contains(scalar.value)
        }
    }
}

func isProximityNonBlank(_ value: String) -> Bool {
    !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
}

func isProximityX509Certificate(_ data: Data) -> Bool {
    !data.isEmpty && SecCertificateCreateWithData(nil, data as CFData) != nil
}

func requireProximityInput(_ condition: Bool, _ message: String = "Invalid proximity input") throws {
    guard condition else { throw WalletError.invalidInput(message) }
}
