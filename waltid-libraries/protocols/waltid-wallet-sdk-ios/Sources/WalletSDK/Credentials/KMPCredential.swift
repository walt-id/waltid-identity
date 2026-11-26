import Foundation
#if canImport(WalletCore) && os(iOS)
@preconcurrency import WalletCore

extension WalletBridgeCredentialRead {
    func toSwiftCredential() throws -> Credential {
        // Advisory decoding cannot remove an otherwise readable credential. The status snapshot /
        // stream still reports invalid status data explicitly; nil is never a clear assessment.
        try credential.toSwiftCredential(status: try? decodeCredentialStatus(CredentialStatus.self, json: statusJson))
    }
}

extension MobileWalletCredential {
    func toSwiftCredential(status: CredentialStatus? = nil) throws -> Credential {
        Credential(
            id: id,
            format: format,
            credentialDataJSON: try requiredCredentialDataJSON(credentialDataJson),
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
