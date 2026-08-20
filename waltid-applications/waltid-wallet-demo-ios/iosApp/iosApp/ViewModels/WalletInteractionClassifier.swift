import Foundation

enum WalletInteractionKind: Equatable {
    case credentialOffer
    case presentationRequest
}

enum WalletInteractionClassification: Equatable {
    case supported(kind: WalletInteractionKind, normalizedInput: String)
    case invalid(message: String)
    case unsupported(message: String)
}

func classifyWalletInteraction(_ rawInput: String) -> WalletInteractionClassification {
    let normalizedInput = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !normalizedInput.isEmpty else {
        return .invalid(message: "Enter or scan a QR code.")
    }
    guard let separator = normalizedInput.firstIndex(of: ":"), separator != normalizedInput.startIndex else {
        return .invalid(message: "This QR code does not contain a valid wallet link.")
    }

    switch normalizedInput[..<separator].lowercased() {
    case "openid-credential-offer":
        return .supported(kind: .credentialOffer, normalizedInput: normalizedInput)
    case "openid4vp":
        return .supported(kind: .presentationRequest, normalizedInput: normalizedInput)
    default:
        return .unsupported(message: "This code is not a supported credential offer or presentation request.")
    }
}
