import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension WalletCore.PresentationPreviewResult {
    func toSwiftPreviewResult() throws -> PresentationPreviewResult {
        switch self {
        case let result as WalletCore.PresentationPreviewResultReady:
            return .ready(try result.preview.toSwiftPreview())
        case let result as WalletCore.PresentationPreviewResultInvalid:
            return .invalid(
                PresentationPreviewError(
                    previewHandle: try PresentationPreviewHandle(value: result.previewHandle.value),
                    request: try result.request.toSwiftRequestContext(),
                    code: result.errorCode.toSwiftErrorCode(),
                    message: result.message
                )
            )
        default:
            throw WalletError.internalFailure("Unsupported presentation preview result: \(type(of: self))")
        }
    }
}

extension WalletCore.PresentationPreview {
    func toSwiftPreview() throws -> PresentationPreview {
        PresentationPreview(
            previewHandle: try PresentationPreviewHandle(value: previewHandle.value),
            request: try request.toSwiftRequestInfo(),
            credentialOptions: try requiredBridgeArray(credentialOptions, of: WalletCore.PresentationCredentialOption.self)
                .map { try $0.toSwiftCredentialOption() },
            credentialRequirements: try requiredBridgeArray(credentialRequirements, of: WalletCore.PresentationCredentialRequirement.self)
                .map { try $0.toSwiftCredentialRequirement() }
        )
    }
}

extension WalletCore.PresentationCredentialOption {
    func toSwiftCredentialOption() throws -> PresentationCredentialOption {
        try PresentationCredentialOption(
            queryID: queryId,
            credential: credential.toSwiftCredential(),
            multiple: multiple,
            disclosures: requiredBridgeArray(disclosures, of: WalletCore.PresentationDisclosure.self)
                .map { $0.toSwiftDisclosure() }
        )
    }
}

func requiredCredentialDataJSON(_ value: String?) throws -> String {
    guard let value else { throw WalletError.internalFailure("Wallet core omitted credential data") }
    return value
}

extension PresentationErrorCode {
    func toKMPErrorCode() -> WalletCore.PresentationErrorCode {
        switch self {
        case .accessDenied:
            return .accessDenied
        case .invalidRequest:
            return .invalidRequest
        case .invalidClient:
            return .invalidClient
        case .invalidScope:
            return .invalidScope
        case .unauthorizedClient:
            return .unauthorizedClient
        case .unsupportedResponseType:
            return .unsupportedResponseType
        case .serverError:
            return .serverError
        case .temporarilyUnavailable:
            return .temporarilyUnavailable
        case .vpFormatsNotSupported:
            return .vpFormatsNotSupported
        case .invalidRequestURIMethod:
            return .invalidRequestUriMethod
        case .invalidTransactionData:
            return .invalidTransactionData
        case .walletUnavailable:
            return .walletUnavailable
        }
    }
}

extension WalletCore.PresentationErrorCode {
    func toSwiftErrorCode() -> PresentationErrorCode {
        switch self {
        case .accessDenied:
            return .accessDenied
        case .invalidRequest:
            return .invalidRequest
        case .invalidClient:
            return .invalidClient
        case .invalidScope:
            return .invalidScope
        case .unauthorizedClient:
            return .unauthorizedClient
        case .unsupportedResponseType:
            return .unsupportedResponseType
        case .serverError:
            return .serverError
        case .temporarilyUnavailable:
            return .temporarilyUnavailable
        case .vpFormatsNotSupported:
            return .vpFormatsNotSupported
        case .invalidRequestUriMethod:
            return .invalidRequestURIMethod
        case .invalidTransactionData:
            return .invalidTransactionData
        case .walletUnavailable:
            return .walletUnavailable
        }
    }
}

extension WalletCore.PresentationResult {
    func toSwiftPresentationResult() throws -> PresentationResult {
        switch self {
        case let result as WalletCore.PresentationResultTransmittedSucceeded:
            let redirectURL: URL?
            if let value = result.redirectUrl {
                guard let url = URL(string: value) else {
                    throw WalletError.invalidInput("Invalid presentation redirect URL: \(value)")
                }
                redirectURL = url
            } else {
                redirectURL = nil
            }
            return .transmitted(
                .succeeded(
                    verifierResponseJSON: result.verifierResponseJson,
                    redirectURL: redirectURL
                )
            )
        case let result as WalletCore.PresentationResultPreparedOpenUrl:
            guard let url = URL(string: result.url) else {
                throw WalletError.invalidInput("Invalid presentation continuation URL: \(result.url)")
            }
            return .prepared(.openURL(url))
        case let result as WalletCore.PresentationResultPreparedSubmitForm:
            return .prepared(.submitForm(html: result.html))
        case let result as WalletCore.PresentationResultTransmittedFailed:
            return .transmitted(.failed(verifierResponseJSON: result.verifierResponseJson))
        default:
            throw WalletError.internalFailure("Unknown presentation result type: \(type(of: self))")
        }
    }
}

private extension WalletCore.PartialPresentationRequest {
    func toSwiftRequestContext() throws -> PartialPresentationRequest {
        try PartialPresentationRequest(
            clientID: clientId,
            requestAuthentication: try requestAuthentication.toSwiftRequestAuthentication(),
            responseEncryption: try responseEncryption.toSwiftResponseEncryption(),
            verifierMetadata: verifierMetadata?.toSwiftVerifierMetadata(),
            responseURI: responseUri.flatMap(URL.init(string:)),
            state: state,
            nonce: nonce
        )
    }
}

private extension WalletCore.PresentationRequest {
    func toSwiftRequestInfo() throws -> PresentationRequest {
        try PresentationRequest(
            clientID: clientId,
            requestAuthentication: try requestAuthentication.toSwiftRequestAuthentication(),
            nonce: nonce,
            responseEncryption: try responseEncryption.toSwiftResponseEncryption(),
            verifierMetadata: verifierMetadata?.toSwiftVerifierMetadata(),
            responseURI: responseUri.flatMap(URL.init(string:)),
            state: state,
            responseMode: responseMode,
            transactionData: try requiredBridgeArray(transactionData, of: WalletCore.PresentationTransactionData.self)
                .map { try $0.toSwiftTransactionData() }
        )
    }
}

private extension WalletCore.PresentationRequestAuthentication {
    func toSwiftRequestAuthentication() throws -> PresentationRequestAuthentication {
        switch self {
        case is WalletCore.PresentationRequestAuthenticationUnauthenticated:
            return .unauthenticated
        case let authenticated as WalletCore.PresentationRequestAuthenticationAuthenticated:
            return .authenticated(
                compactRequestObject: authenticated.compactRequestObject,
                algorithm: authenticated.algorithm,
                keyID: authenticated.keyId,
                clientIDScheme: authenticated.clientIdScheme.toSwiftClientIDScheme()
            )
        default:
            throw WalletError.internalFailure("Unsupported request authentication: \(type(of: self))")
        }
    }
}

private extension WalletCore.PresentationClientIdScheme {
    func toSwiftClientIDScheme() -> PresentationClientIDScheme {
        switch self {
        case .preRegistered:
            return .preRegistered
        case .redirectUri:
            return .redirectURI
        case .x509SanDns:
            return .x509SanDNS
        case .x509Hash:
            return .x509Hash
        case .decentralizedIdentifier:
            return .decentralizedIdentifier
        case .verifierAttestation:
            return .verifierAttestation
        case .openidFederation:
            return .openIDFederation
        }
    }
}

private extension WalletCore.PresentationResponseEncryption {
    func toSwiftResponseEncryption() throws -> PresentationResponseEncryption {
        switch self {
        case is WalletCore.PresentationResponseEncryptionNotRequired:
            return .notRequired
        case let encryption as WalletCore.PresentationResponseEncryptionRequired:
            return .required(ResponseEncryptionDetails(
                keyManagementAlgorithm: encryption.keyManagementAlgorithm,
                contentEncryptionAlgorithm: encryption.contentEncryptionAlgorithm,
                verifierKeyID: encryption.verifierKeyId,
                verifierKeyThumbprint: encryption.verifierKeyThumbprint
            ))
        default:
            throw WalletError.internalFailure("Unsupported presentation response encryption")
        }
    }
}

private extension WalletCore.MetadataDisplay {
    func toSwiftMetadataDisplay() -> MetadataDisplay {
        MetadataDisplay(
            name: name,
            locale: locale,
            logoURI: logoUri,
            logoAltText: logoAltText,
            description: descriptionText,
            backgroundColor: backgroundColor,
            backgroundImageURI: backgroundImageUri,
            textColor: textColor
        )
    }
}

private extension WalletCore.VerifierMetadata {
    func toSwiftVerifierMetadata() -> VerifierMetadata {
        VerifierMetadata(
            display: display?.toSwiftMetadataDisplay(),
            clientURI: clientUri,
            policyURI: policyUri,
            termsOfServiceURI: termsOfServiceUri
        )
    }
}

private extension WalletCore.PresentationCredentialRequirement {
    func toSwiftCredentialRequirement() throws -> PresentationCredentialRequirement {
        let options = try requiredBridgeArray(options, of: [String].self)
        guard PresentationCredentialRequirement.hasValidOptions(options) else { throw WalletError.internalFailure("Invalid credential requirement") }
        return try PresentationCredentialRequirement(options: options)
    }
}

private extension WalletCore.PresentationDisclosure {
    func toSwiftDisclosure() -> PresentationDisclosure {
        let inclusion: DisclosureDisposition
        switch disposition {
        case .additionalCleartext: inclusion = .additionalCleartext
        case .requiredCleartext: inclusion = .requiredCleartext
        case .optionalCleartext: inclusion = .optionalCleartext
        case .requiredSelective: inclusion = .requiredSelective
        case .fixedSelective: inclusion = .fixedSelective
        case .selectable: inclusion = .selectable
        }
        return PresentationDisclosure(
            path: path,
            valueJSON: valueJson,
            disposition: inclusion,
            name: name,
            displayValue: displayValue
        )
    }
}

private extension WalletCore.PresentationTransactionData {
    func toSwiftTransactionData() throws -> PresentationTransactionData {
        try PresentationTransactionData(
            type: type,
            displayName: displayName,
            credentialQueryIDs: try requiredBridgeArray(credentialQueryIds, of: String.self),
            supportedFields: try requiredBridgeArray(supportedFields, of: String.self),
            rawJSON: rawJson,
            detailsJSON: detailsJson
        )
    }
}
#endif
