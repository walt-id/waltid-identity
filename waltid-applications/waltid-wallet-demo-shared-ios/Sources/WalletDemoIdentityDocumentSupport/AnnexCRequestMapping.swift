import Foundation
import WalletSDK
#if os(iOS)
import IdentityDocumentServices
#endif

/// One alternative set of documents a verifier is willing to accept.
public struct MobileDocumentRequestSetSnapshot: Equatable, Sendable {
    /// Documents requested by this alternative.
    public let requests: [AnnexCDocumentRequest]

    /// Creates a request-set snapshot.
    public init(requests: [AnnexCDocumentRequest]) {
        self.requests = requests
    }
}

/// One presentment request within an Apple ISO 18013 request.
public struct MobileDocumentPresentmentSnapshot: Equatable, Sendable {
    /// Alternative document sets offered for this presentment.
    public let documentRequestSets: [MobileDocumentRequestSetSnapshot]

    /// Creates a presentment snapshot.
    public init(documentRequestSets: [MobileDocumentRequestSetSnapshot]) {
        self.documentRequestSets = documentRequestSets
    }
}

/// Value-typed mirror of Apple's pre-consent request.
///
/// The mapping to the wallet's `AnnexCParsedRequest` is the only interesting part of request
/// handling, and `ISO18013MobileDocumentRequest` cannot be constructed by a test. Snapshotting first
/// makes the mapping exercisable on any OS version, while the Apple-typed initializer stays a
/// mechanical field copy.
public struct MobileDocumentRequestSnapshot: Equatable, Sendable {
    /// Presentment requests carried by the request.
    public let presentments: [MobileDocumentPresentmentSnapshot]

    /// Creates a request snapshot.
    public init(presentments: [MobileDocumentPresentmentSnapshot]) {
        self.presentments = presentments
    }
}

/// Flattens Apple's presentment structure into the wallet's parsed request.
///
/// - Throws: ``IdentityDocumentSupportFailure/alternativeRequestSetsUnsupported`` when a presentment
///   offers a choice of document sets. Picking one is a policy decision the user would have to make,
///   and silently taking the first would disclose more than the verifier asked for in the branch the
///   user did not see. ``IdentityDocumentSupportFailure/emptyRequest`` when nothing is requested,
///   because a consent prompt over an empty request cannot be answered meaningfully.
public func parsedRequest(from snapshot: MobileDocumentRequestSnapshot) throws -> AnnexCParsedRequest {
    var documents: [AnnexCDocumentRequest] = []
    for presentment in snapshot.presentments {
        guard presentment.documentRequestSets.count == 1,
              let requestSet = presentment.documentRequestSets.first else {
            throw IdentityDocumentSupportFailure.alternativeRequestSetsUnsupported
        }
        documents.append(contentsOf: requestSet.requests)
    }
    guard !documents.isEmpty else { throw IdentityDocumentSupportFailure.emptyRequest }
    return AnnexCParsedRequest(documents: documents)
}

/// Extracts the encrypted ISO 18013-7 response bytes from the wallet's response JSON.
///
/// Apple wants raw sealed bytes, whereas the wallet core returns the Annex C JSON envelope whose
/// `response` member is base64url-encoded.
///
/// - Throws: ``IdentityDocumentSupportFailure/invalidResponseEncoding`` if the envelope is missing
///   the member or it is not base64url.
public func encryptedResponseData(fromResponseJSON json: String) throws -> Data {
    let envelope = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String: Any]
    guard let encoded = envelope?["response"] as? String,
          let sealed = Data(base64URLEncoded: encoded) else {
        throw IdentityDocumentSupportFailure.invalidResponseEncoding
    }
    return sealed
}

/// Raw post-consent Annex C request Apple releases once the user has agreed to share.
public struct RawAnnexCRequest: Decodable, Equatable, Sendable {
    /// Base64url-encoded ISO 18013-5 `DeviceRequest`.
    public let deviceRequest: String
    /// Base64url-encoded `DCAPIEncryptionInfo` naming the reader's HPKE recipient key.
    public let encryptionInfo: String

    /// Decodes a raw request from the bytes Apple hands to `sendResponse`.
    public init(data: Data) throws {
        self = try JSONDecoder().decode(Self.self, from: data)
    }
}

extension Data {
    /// Decodes unpadded base64url, the encoding every Annex C field uses.
    public init?(base64URLEncoded value: String) {
        var normalized = value.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        normalized.append(String(repeating: "=", count: (4 - normalized.count % 4) % 4))
        self.init(base64Encoded: normalized)
    }
}

#if os(iOS)
@available(iOS 26.0, *)
extension MobileDocumentRequestSnapshot {
    /// Snapshots Apple's parsed request without interpreting it.
    public init(_ request: ISO18013MobileDocumentRequest) {
        self.init(
            presentments: request.presentmentRequests.map { presentment in
                MobileDocumentPresentmentSnapshot(
                    documentRequestSets: presentment.documentRequestSets.map { requestSet in
                        MobileDocumentRequestSetSnapshot(
                            requests: requestSet.requests.map { document in
                                AnnexCDocumentRequest(
                                    documentType: document.documentType,
                                    namespaces: document.namespaces.mapValues { $0.keys.sorted() }
                                )
                            }
                        )
                    }
                )
            }
        )
    }
}
#endif
