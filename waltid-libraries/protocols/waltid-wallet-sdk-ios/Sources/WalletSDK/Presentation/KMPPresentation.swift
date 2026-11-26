import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension MobileWalletPresentationPreviewResult {
    func toSwiftPreviewResult() throws -> PresentationPreviewResult {
        switch self {
        case let result as MobileWalletPresentationPreviewResultReady:
            return .ready(try result.preview.toSwiftPreview())
        case let result as MobileWalletPresentationPreviewResultInvalid:
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

extension MobileWalletPresentationPreview {
    func toSwiftPreview() throws -> PresentationPreview {
        PresentationPreview(
            previewHandle: try PresentationPreviewHandle(value: previewHandle.value),
            request: try request.toSwiftRequestInfo(),
            credentialOptions: try requiredBridgeArray(credentialOptions, of: MobileWalletPresentationCredentialOption.self)
                .map { try $0.toSwiftCredentialOption() },
            credentialRequirements: try requiredBridgeArray(credentialRequirements, of: MobileWalletPresentationCredentialRequirement.self)
                .map { try $0.toSwiftCredentialRequirement() }
        )
    }
}

extension MobileWalletPresentationCredentialOption {
    func toSwiftCredentialOption() throws -> PresentationCredentialOption {
        PresentationCredentialOption(
            queryID: queryId,
            credentialID: credentialId,
            format: format,
            credentialDataJSON: try requiredCredentialDataJSON(credentialDataJson),
            multiple: multiple,
            issuer: issuer,
            subject: subject,
            label: label,
            disclosures: try requiredBridgeArray(disclosures, of: MobileWalletPresentationDisclosure.self)
                .map { $0.toSwiftDisclosure() },
            metadataJSON: metadataJson
        )
    }
}

func requiredCredentialDataJSON(_ value: String?) throws -> String {
    guard let value else { throw WalletError.internalFailure("Wallet core omitted credential data") }
    return value
}

extension PresentationErrorCode {
    func toKMPErrorCode() -> MobileWalletPresentationErrorCode {
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

extension MobileWalletPresentationErrorCode {
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

extension MobileWalletPresentationResult {
    func toSwiftPresentationResult() throws -> PresentationResult {
        switch self {
        case let result as MobileWalletPresentationResultTransmittedSucceeded:
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
        case let result as MobileWalletPresentationResultPreparedOpenUrl:
            guard let url = URL(string: result.url) else {
                throw WalletError.invalidInput("Invalid presentation continuation URL: \(result.url)")
            }
            return .prepared(.openURL(url))
        case let result as MobileWalletPresentationResultPreparedSubmitForm:
            return .prepared(.submitForm(html: result.html))
        case let result as MobileWalletPresentationResultTransmittedFailed:
            return .transmitted(.failed(verifierResponseJSON: result.verifierResponseJson))
        default:
            throw WalletError.internalFailure("Unknown presentation result type: \(type(of: self))")
        }
    }
}

private extension MobileWalletPresentationRequestContext {
    func toSwiftRequestContext() throws -> PresentationRequestContext {
        PresentationRequestContext(
            clientID: clientId,
            requestAuthentication: try requestAuthentication.toSwiftRequestAuthentication(),
            verifierMetadata: verifierMetadata?.toSwiftVerifierMetadata(),
            responseURI: responseUri.flatMap(URL.init(string:)),
            state: state,
            nonce: nonce,
            responseEncryption: try responseEncryption.toSwiftResponseEncryption()
        )
    }
}

private extension MobileWalletPresentationRequestInfo {
    func toSwiftRequestInfo() throws -> PresentationRequestInfo {
        PresentationRequestInfo(
            clientID: clientId,
            requestAuthentication: try requestAuthentication.toSwiftRequestAuthentication(),
            nonce: nonce,
            responseEncryption: try responseEncryption.toSwiftResponseEncryption(),
            verifierMetadata: verifierMetadata?.toSwiftVerifierMetadata(),
            responseURI: responseUri.flatMap(URL.init(string:)),
            state: state,
            responseMode: responseMode,
            transactionData: try requiredBridgeArray(transactionData, of: MobileWalletTransactionDataItem.self)
                .map { try $0.toSwiftTransactionData() }
        )
    }
}

private extension MobileWalletRequestAuthentication {
    func toSwiftRequestAuthentication() throws -> PresentationRequestAuthentication {
        switch self {
        case is MobileWalletRequestAuthenticationUnauthenticated:
            return .unauthenticated
        case let authenticated as MobileWalletRequestAuthenticationAuthenticated:
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

private extension MobileWalletClientIdScheme {
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

private extension MobileWalletResponseEncryption {
    func toSwiftResponseEncryption() throws -> PresentationResponseEncryption {
        switch self {
        case is WalletCore.MobileWalletResponseEncryptionNotRequired:
            return .notRequired
        case let encryption as WalletCore.MobileWalletResponseEncryptionRequired:
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

private extension MobileWalletMetadataDisplay {
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

private extension MobileWalletVerifierMetadata {
    func toSwiftVerifierMetadata() -> VerifierMetadata {
        VerifierMetadata(
            display: display?.toSwiftMetadataDisplay(),
            clientURI: clientUri,
            policyURI: policyUri,
            termsOfServiceURI: termsOfServiceUri
        )
    }
}

private extension MobileWalletPresentationCredentialRequirement {
    func toSwiftCredentialRequirement() throws -> PresentationCredentialRequirement {
        let options = try requiredBridgeArray(options, of: [String].self)
        guard PresentationCredentialRequirement.hasValidOptions(options) else { throw WalletError.internalFailure("Invalid credential requirement") }
        return PresentationCredentialRequirement(options: options)
    }
}

private extension MobileWalletPresentationDisclosure {
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

private extension MobileWalletTransactionDataItem {
    func toSwiftTransactionData() throws -> PresentationTransactionData {
        PresentationTransactionData(
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
