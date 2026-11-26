import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension WalletBridgeCredentialRead {
    func toSwiftCredential() -> Credential {
        // Advisory decoding cannot remove an otherwise readable credential. The status snapshot /
        // stream still reports invalid status data explicitly; nil is never a clear assessment.
        credential.toSwiftCredential(status: try? decodeCredentialStatus(CredentialStatus.self, json: statusJson))
    }
}

extension MobileWalletCredential {
    func toSwiftCredential(status: CredentialStatus? = nil) -> Credential {
        Credential(
            id: id,
            format: format,
            credentialDataJSON: requiredCredentialDataJSON(credentialDataJson),
            issuer: issuer,
            subject: subject,
            label: label,
            addedAt: addedAt.flatMap(parseWalletISO8601Date),
            metadataJSON: metadataJson,
            status: status
        )
    }
}
#endif
