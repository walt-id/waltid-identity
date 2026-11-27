import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension AnnexCParsedRequest {
    func toKMPParsedRequest() -> WalletCore.AnnexCParsedRequest {
        WalletCore.AnnexCParsedRequest(
            documents: documents.map {
                WalletCore.AnnexCDocumentRequest(
                    docType: $0.documentType,
                    namespaces: $0.namespaces
                )
            }
        )
    }
}

extension WalletCore.DigitalCredentialCapabilities {
    func toSwiftCapabilities() -> DigitalCredentialCapabilities {
        DigitalCredentialCapabilities(
            platform: platform,
            platformAvailable: platformAvailable,
            minimumOSVersion: minimumOsVersion,
            registrationAvailable: registrationAvailable,
            capabilities: swiftArray(capabilities, of: WalletCore.DigitalCredentialCapability.self).map {
                DigitalCredentialCapability(
                    protocolIdentifier: $0.protocol,
                    // `identifier`, not String(describing:): SKIE renders these as Swift enums, so
                    // describing them would publish compiler-derived case names as API.
                    credentialFormats: swiftArray($0.credentialFormats, of: WalletCore.DigitalCredentialFormat.self).map(\.identifier),
                    requestProtection: swiftArray($0.requestProtection, of: WalletCore.DigitalCredentialRequestProtection.self).map(\.identifier),
                    responseProtection: swiftArray($0.responseProtection, of: WalletCore.DigitalCredentialResponseProtection.self).map(\.identifier),
                    supported: $0.supported,
                    unsupportedReason: $0.unsupportedReason
                )
            }
        )
    }
}

extension WalletCore.AnnexCPreview {
    func toSwiftPreview() throws -> AnnexCPresentationPreview {
        AnnexCPresentationPreview(
            requestID: requestId,
            verifiedOrigin: verifiedOrigin,
            parsedRequest: AnnexCParsedRequest(
                documents: try requiredBridgeArray(parsedRequest.documents, of: WalletCore.AnnexCDocumentRequest.self).map {
                    AnnexCDocumentRequest(documentType: $0.docType, namespaces: $0.namespaces)
                }
            ),
            credentialOptions: try requiredBridgeArray(credentialOptions, of: WalletCore.PresentationCredentialOption.self).map { try $0.toSwiftCredentialOption() },
            readerTrust: readerAuthentication.toSwiftReaderTrust(decision: readerTrust)
        )
    }
}

extension AnnexCSubmission {
    func toKMPSubmission() -> WalletCore.AnnexCSubmission {
        WalletCore.AnnexCSubmission(
            requestId: requestID,
            verifiedOrigin: verifiedOrigin,
            deviceRequestBase64Url: deviceRequestBase64URL,
            encryptionInfoBase64Url: encryptionInfoBase64URL,
            selectedCredentialOptions: selectedCredentialOptions.map {
                WalletCore.PresentationCredentialSelection(queryId: $0.queryID, credentialId: $0.credentialID)
            }
        )
    }
}
private extension WalletCore.DigitalCredentialReaderAuthentication {
    func toSwiftReaderTrust(decision: Waltid_mdoc_reader_trustReaderTrustDecision?) -> ReaderTrust {
        switch self {
        case .notApplicable: return .notApplicable
        case .notAuthenticated: return .notAuthenticated
        case .pendingRawRequest: return .pendingRawRequest
        case .verified:
            guard let decision else { return .untrusted(reason: "No reader trust decision was made") }
            if decision.state == .trusted, let displayName = decision.displayName {
                return .trusted(certificateSubject: displayName)
            }
            return .untrusted(reason: decision.reason ?? "The reader is not trusted")
        }
    }
}
#endif
