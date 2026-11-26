import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension Waltid_openid4vc_walletWalletIssuanceBatchSession {
    func toSwiftIssuanceSession() throws -> IssuanceSession {
        IssuanceSession(
            id: id,
            offer: try offer.toSwiftIssuanceOfferPreview(batchSize: batchSize?.intValue, displayMetadata: credentialDisplayMetadata)
        )
    }
}

extension Waltid_openid4vc_walletWalletIssuanceAuthorization {
    func toSwiftIssuanceAuthorization() throws -> IssuanceAuthorization {
        guard let authorizationURL = URL(string: url), let callbackURL = URL(string: redirectUri) else {
            throw WalletError.internalFailure("Wallet core returned an invalid issuance URL.")
        }
        return IssuanceAuthorization(
            url: authorizationURL,
            state: state,
            redirectURI: callbackURL,
            pkce: IssuancePKCEState(
                codeChallenge: pkce.codeChallenge,
                codeChallengeMethod: pkce.codeChallengeMethod
            ),
            pushedAuthorizationRequestUsed: pushedAuthorizationRequestUsed,
            requestURIExpiresAt: requestUriExpiresAtEpochMilliseconds.map {
                Date(timeIntervalSince1970: TimeInterval($0.int64Value) / 1000.0)
            }
        )
    }
}

extension Waltid_openid4vc_walletWalletIssuanceOutcome {
    func toSwiftIssuanceOutcome() throws -> IssuanceOutcome {
        switch onEnum(of: self) {
        case let .stored(value):
            return .stored(
                sessionID: value.sessionId,
                credentialIDs: swiftArray(value.credentialIds, of: String.self)
            )
        case let .deferred(value):
            return .deferred(
                sessionID: value.sessionId,
                storedCredentialIDs: swiftArray(value.storedCredentialIds, of: String.self),
                credentials: swiftArray(
                    value.credentials,
                    of: Waltid_openid4vc_walletWalletDeferredCredential.self
                ).map { $0.toSwiftDeferredCredential() }
            )
        case let .cancelled(value):
            return .cancelled(sessionID: value.sessionId)
        case let .failed(value):
            return .failed(
                sessionID: value.sessionId,
                error: IssuanceFailure(
                    code: value.error.issuerTrustFailed ? .issuerTrust : value.error.code.toSwiftIssuanceErrorCode(),
                    message: value.error.message,
                    canRetryOffer: value.error.canRetryOffer,
                    targetFailure: value.failure?.toSwiftTargetFailure()
                ),
                storedCredentialIDs: swiftArray(value.storedCredentialIds, of: String.self),
                deferredCredentials: swiftArray(value.deferredCredentials, of: Waltid_openid4vc_walletWalletIssuanceContinuation.self)
                    .map { $0.toSwiftDeferredCredential() }
            )
        }
    }
}

extension IssuanceCredentialSelection {
    func toKMPSelection() throws -> MobileWalletCredentialSelection {
        let selection: any MobileWalletCredentialHolders
        switch holders {
        case .existing(let bindings):
            selection = MobileWalletCredentialHoldersExisting(bindings: bindings.map { MobileWalletHolderBinding(keyId: $0.keyID, did: $0.did) })
        case .newKeys(let count):
            selection = MobileWalletCredentialHoldersNewKeys(count: Int32(count))
        }
        return MobileWalletCredentialSelection(credentialConfigurationId: configurationID,
            holders: selection, credentialIdentifier: credentialIdentifier)
    }
}

extension Waltid_openid4vc_walletWalletDeferredCredential {
    func toSwiftDeferredCredential() -> DeferredCredential {
        DeferredCredential(id: id, credentialConfigurationID: credentialConfigurationId,
            intervalSeconds: intervalSeconds?.int64Value, credentialIdentifier: credentialIdentifier)
    }
}

extension Waltid_openid4vc_walletWalletIssuanceContinuation {
    func toSwiftDeferredCredential() -> DeferredCredential {
        DeferredCredential(id: id, credentialConfigurationID: credentialConfigurationId,
            intervalSeconds: intervalSeconds?.int64Value, credentialIdentifier: credentialIdentifier,
            status: status.toSwift(), displayMetadataJSON: displayMetadataJson)
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceOfferPreview {
    func toSwiftIssuanceOfferPreview(batchSize: Int?, displayMetadata: [String: String]) throws -> IssuanceOfferPreview {
        IssuanceOfferPreview(
            grant: grant == .authorizationCode ? .authorizationCode : .preAuthorizedCode,
            issuer: issuer.toSwiftIssuanceIssuerPreview(),
            credentials: swiftArray(credentials, of: Waltid_openid4vc_walletWalletIssuanceCredentialPreview.self)
                .map { $0.toSwiftIssuanceCredentialPreview(metadataJSON: displayMetadata[$0.configurationId]) },
            transactionCode: transactionCode?.toSwiftIssuanceTransactionCode(),
            batchSize: batchSize
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceIssuerPreview {
    func toSwiftIssuanceIssuerPreview() -> IssuanceIssuerPreview {
        IssuanceIssuerPreview(
            identifier: identifier,
            metadataProvenance: metadataProvenance.toSwiftMetadataProvenance(),
            display: .init(
                name: name,
                locale: locale,
                logoURI: logoUri.flatMap(URL.init(string:))?.absoluteString,
                logoAltText: logoAltText
            )
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceMetadataProvenance {
    func toSwiftMetadataProvenance() -> MetadataProvenance {
        switch self {
        case is Waltid_openid4vc_walletWalletIssuanceMetadataProvenanceUnsigned:
            return .unsigned
        case let signed as Waltid_openid4vc_walletWalletIssuanceMetadataProvenanceSigned:
            return .signed(
                SignedMetadataProvenance(
                    compactJWT: signed.compactJwt,
                    algorithm: signed.algorithm,
                    keyID: signed.keyId,
                    trustType: signed.trustType == .trustedIssuer ? .trustedIssuer : .trustedDelegate
                )
            )
        default:
            preconditionFailure("Unsupported issuer metadata provenance: \(type(of: self))")
        }
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceCredentialPreview {
    func toSwiftIssuanceCredentialPreview(metadataJSON: String?) -> IssuanceCredentialPreview {
        IssuanceCredentialPreview(
            configurationID: configurationId,
            format: format,
            display: .init(
                name: name,
                logoURI: logoUri.flatMap(URL.init(string:))?.absoluteString,
                logoAltText: logoAltText,
                description: descriptionText,
                backgroundColor: backgroundColor,
                backgroundImageURI: backgroundImageUri.flatMap(URL.init(string:))?.absoluteString,
                textColor: textColor
            ),
            vct: vct,
            doctype: doctype,
            metadataJSON: metadataJSON
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceTransactionCode {
    func toSwiftIssuanceTransactionCode() -> IssuanceTransactionCode {
        IssuanceTransactionCode(
            inputMode: inputMode,
            length: length.map { Int($0.int32Value) },
            descriptionText: descriptionText
        )
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceContinuationStatus {
    func toSwift() -> IssuanceContinuationStatus {
        switch self {
        case .awaitingIssuer: return .awaitingIssuer
        case .awaitingLocalSave: return .awaitingLocalSave
        case .remoteOutcomeUncertain: return .remoteOutcomeUncertain
        case .storageOutcomeUncertain: return .storageOutcomeUncertain
        default: return .unresolved
        }
    }
}

private extension Waltid_openid4vc_walletCredentialIssuanceFailure {
    func toSwiftTargetFailure() -> IssuanceTargetFailure {
        let stage: IssuanceFailureStage
        switch self.stage {
        case .proof: stage = .proof
        case .request: stage = .request
        case .response: stage = .response
        case .storage: stage = .storage
        case .observer: stage = .observer
        }
        return IssuanceTargetFailure(
            target: IssuanceCredentialTarget(configurationID: target.credentialConfigurationId, credentialIdentifier: target.credentialIdentifier),
            stage: stage,
            notAttempted: swiftArray(notAttempted, of: Waltid_openid4vci_walletCredentialIssuanceTarget.self).map {
                IssuanceCredentialTarget(configurationID: $0.credentialConfigurationId, credentialIdentifier: $0.credentialIdentifier)
            })
    }
}

private extension Waltid_openid4vc_walletWalletIssuanceErrorCode {
    func toSwiftIssuanceErrorCode() -> IssuanceErrorCode {
        switch self {
        case .invalidSession: return .invalidSession
        case .invalidCallback: return .invalidCallback
        case .invalidInput: return .invalidInput
        case .authorizationFailed: return .authorizationFailed
        case .issuerMetadata: return .issuerMetadata
        case .issuerResponse: return .issuerResponse
        case .network: return .network
        case .remoteOutcomeUncertain: return .remoteOutcomeUncertain
        case .storageOutcomeUncertain: return .storageOutcomeUncertain
        case .crypto: return .crypto
        case .storage: return .storage
        case .protocol: return .protocol
        }
    }
}
#endif
