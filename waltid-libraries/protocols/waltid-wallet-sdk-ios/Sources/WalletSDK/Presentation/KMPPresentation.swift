import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension MobileWalletPresentationPreviewResult {
    func toSwiftPreviewResult() throws -> PresentationPreviewResult {
        switch self {
        case let result as MobileWalletPresentationPreviewResultReady:
            return .ready(result.preview.toSwiftPreview())
        case let result as MobileWalletPresentationPreviewResultInvalid:
            return .invalid(
                PresentationPreviewError(
                    previewHandle: PresentationPreviewHandle(value: result.previewHandle.value),
                    request: result.request.toSwiftRequestContext(),
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
    func toSwiftPreview() -> PresentationPreview {
        PresentationPreview(
            previewHandle: PresentationPreviewHandle(value: previewHandle.value),
            request: request.toSwiftRequestInfo(),
            credentialOptions: swiftArray(credentialOptions, of: MobileWalletPresentationCredentialOption.self)
                .map { $0.toSwiftCredentialOption() },
            credentialRequirements: swiftArray(credentialRequirements, of: MobileWalletPresentationCredentialRequirement.self)
                .map { $0.toSwiftCredentialRequirement() }
        )
    }
}

extension MobileWalletPresentationCredentialOption {
    func toSwiftCredentialOption() -> PresentationCredentialOption {
        PresentationCredentialOption(
            queryID: queryId,
            credentialID: credentialId,
            format: format,
            credentialDataJSON: requiredCredentialDataJSON(credentialDataJson),
            multiple: multiple,
            issuer: issuer,
            subject: subject,
            label: label,
            disclosures: swiftArray(disclosures, of: MobileWalletPresentationDisclosure.self)
                .map { $0.toSwiftDisclosure() },
            metadataJSON: metadataJson
        )
    }
}

func requiredCredentialDataJSON(_ value: String?) -> String {
    guard let value else {
        assertionFailure("KMP wallet returned nil credential data JSON for a non-null SDK field")
        return "{}"
    }
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
    func toSwiftRequestContext() -> PresentationRequestContext {
        PresentationRequestContext(
            clientID: clientId,
            requestAuthentication: requestAuthentication.toSwiftRequestAuthentication(),
            verifierMetadata: verifierMetadata?.toSwiftVerifierMetadata(),
            responseURI: responseUri.flatMap(URL.init(string:)),
            state: state,
            nonce: nonce,
            responseEncryption: responseEncryption.toSwiftResponseEncryption()
        )
    }
}

private extension MobileWalletPresentationRequestInfo {
    func toSwiftRequestInfo() -> PresentationRequestInfo {
        PresentationRequestInfo(
            clientID: clientId,
            requestAuthentication: requestAuthentication.toSwiftRequestAuthentication(),
            nonce: nonce,
            responseEncryption: responseEncryption.toSwiftResponseEncryption(),
            verifierMetadata: verifierMetadata?.toSwiftVerifierMetadata(),
            responseURI: responseUri.flatMap(URL.init(string:)),
            state: state,
            responseMode: responseMode,
            transactionData: swiftArray(transactionData, of: MobileWalletTransactionDataItem.self)
                .map { $0.toSwiftTransactionData() }
        )
    }
}

private extension MobileWalletRequestAuthentication {
    func toSwiftRequestAuthentication() -> PresentationRequestAuthentication {
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
            preconditionFailure("Unsupported request authentication: \(type(of: self))")
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
    func toSwiftResponseEncryption() -> PresentationResponseEncryption {
        guard isRequired else { return .notRequired }
        guard let keyManagementAlgorithm,
              let contentEncryptionAlgorithm,
              let verifierKeyThumbprint else {
            preconditionFailure("Required response encryption is missing selected metadata")
        }
        return .required(
            ResponseEncryptionDetails(
                keyManagementAlgorithm: keyManagementAlgorithm,
                contentEncryptionAlgorithm: contentEncryptionAlgorithm,
                verifierKeyID: verifierKeyId,
                verifierKeyThumbprint: verifierKeyThumbprint
            )
        )
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
    func toSwiftCredentialRequirement() -> PresentationCredentialRequirement {
        PresentationCredentialRequirement(options: swiftStringMatrix(options))
    }
}

private func swiftStringMatrix(_ value: Any) -> [[String]] {
    swiftArray(value, of: Any.self).map { option in
        swiftArray(option, of: String.self)
    }
}

private extension MobileWalletPresentationDisclosure {
    func toSwiftDisclosure() -> PresentationDisclosure {
        PresentationDisclosure(
            path: path,
            name: name,
            valueJSON: valueJson,
            displayValue: displayValue,
            selectivelyDisclosable: selectivelyDisclosable,
            required: required,
            selectable: selectable,
            requested: requested
        )
    }
}

private extension MobileWalletTransactionDataItem {
    func toSwiftTransactionData() -> PresentationTransactionData {
        PresentationTransactionData(
            type: type,
            displayName: displayName,
            credentialQueryIDs: swiftArray(credentialQueryIds, of: String.self),
            supportedFields: swiftArray(supportedFields, of: String.self),
            rawJSON: rawJson,
            detailsJSON: detailsJson
        )
    }
}
#endif
