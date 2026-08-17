import Foundation
import WalletSDK

/// Maps a normal OpenID4VP preview onto the shared review model.
///
/// The dependency direction matters more than the mapping does: the protocol preview is translated
/// into the review model, never the other way round. A transport that instead impersonated an
/// OpenID4VP preview would have to invent the fields it has no answer for.
public extension PresentationPreview {
    /// The review the user is shown for this OpenID4VP presentation request.
    func sharingReview() -> SharingReviewModel {
        SharingReviewModel(
            request: request.sharingRequest(),
            credentialOptions: credentialOptions,
            credentialRequirements: credentialRequirements
        )
    }
}

public extension PresentationRequestInfo {
    /// The request concepts of a normal OpenID4VP presentation request.
    func sharingRequest() -> SharingRequest {
        SharingRequest(
            requester: verifierMetadata?.sharingRequester(verifiedOrigin: nil),
            // Plain OpenID4VP has no reader authentication concept, so no reader-trust section is
            // offered rather than one reporting an absent reader.
            readerTrust: nil,
            responseProtection: responseEncryption.sharingResponseProtection(mechanism: .jwe),
            transactionData: CredentialDisplayNormalizer.transactionDataGroups(for: transactionData),
            technicalDetails: [
                SharingDetail(label: "Client ID", value: clientID),
                SharingDetail(label: "Response URI", value: responseURI?.absoluteString),
                SharingDetail(label: "State", value: state),
                SharingDetail(label: "Nonce", value: nonce),
            ]
        )
    }
}

public extension PresentationRequestContext {
    /// The request concepts of an OpenID4VP request the wallet refuses to satisfy.
    ///
    /// A rejected request still deserves a review surface: the user has to see who asked before
    /// deciding whether to notify them.
    func sharingRequest() -> SharingRequest {
        SharingRequest(
            requester: verifierMetadata?.sharingRequester(verifiedOrigin: nil),
            readerTrust: nil,
            responseProtection: responseEncryption.sharingResponseProtection(mechanism: .jwe),
            transactionData: [],
            technicalDetails: [
                SharingDetail(label: "Client ID", value: clientID),
                SharingDetail(label: "Response URI", value: responseURI?.absoluteString),
                SharingDetail(label: "State", value: state),
                SharingDetail(label: "Nonce", value: nonce),
            ]
        )
    }
}

/// Maps an ISO 18013-7 Annex C preview onto the shared review model.
///
/// Annex C carries no OpenID4VP request parameters, so the review gets no client ID, state or
/// response URI at all. The requested documents and elements are reviewed through the same credential
/// and disclosure components every other transport uses, which is what makes them comparable.
public extension AnnexCPresentationPreview {
    /// The review the user is shown for this Annex C presentation request.
    func sharingReview() -> SharingReviewModel {
        SharingReviewModel(
            request: SharingRequest(
                // Annex C proves an origin and nothing else about the caller, so the origin both
                // heads the section and stays labelled as the verified fact it is.
                requester: SharingRequester(
                    fallbackName: verifiedOrigin,
                    verifiedOrigin: verifiedOrigin
                ),
                readerTrust: readerTrust.sharingReaderTrust(),
                // Annex C always session-encrypts the device response; there is no unencrypted
                // variant to report, so this states the mechanism rather than asking whether
                // encryption was requested.
                responseProtection: .encrypted(
                    mechanism: .annexCHPKE,
                    keyManagementAlgorithm: Self.annexCKeyManagementAlgorithm,
                    contentEncryptionAlgorithm: Self.annexCContentEncryptionAlgorithm
                ),
                technicalDetails: [
                    SharingDetail(
                        label: "Requested documents",
                        value: parsedRequest.documents
                            .map(\.documentType)
                            .joined(separator: ", ")
                            .presentableValue
                    ),
                ]
            ),
            credentialOptions: credentialOptions,
            // Annex C requests every listed document, so each requested document is its own
            // requirement and Share stays disabled until one credential is chosen for each.
            credentialRequirements: credentialOptions
                .map(\.queryID)
                .distinctPreservingOrder()
                .map { PresentationCredentialRequirement(options: [[$0]]) }
        )
    }

    /// HPKE key encapsulation ISO 18013-7 Annex C mandates for the session key.
    private static var annexCKeyManagementAlgorithm: String { "DHKEM(P-256, HKDF-SHA256)" }
    /// AEAD ISO 18013-7 Annex C mandates for the sealed device response.
    private static var annexCContentEncryptionAlgorithm: String { "AES-128-GCM" }
}

public extension ReaderTrust {
    /// Translates SDK reader-trust states into review states.
    ///
    /// ``ReaderTrust/notApplicable`` becomes `nil` rather than a state, because "this protocol has no
    /// reader authentication" is answered by omitting the section, not by rendering one.
    func sharingReaderTrust() -> SharingReaderTrust? {
        switch self {
        case .notApplicable: return nil
        case .notAuthenticated: return .notAuthenticated
        case .pendingRawRequest: return .pendingVerification
        case .untrusted(let reason): return .untrusted(reason: reason)
        case .trusted(let certificateSubject): return .trusted(readerIdentity: certificateSubject)
        }
    }
}

private extension VerifierMetadata {
    /// The requester identity a verifier's self-asserted metadata supports.
    func sharingRequester(verifiedOrigin: String?) -> SharingRequester? {
        let requester = SharingRequester(
            display: display,
            fallbackName: verifiedOrigin,
            verifiedOrigin: verifiedOrigin,
            details: [
                SharingDetail(label: "Client URI", value: clientURI, linkURI: clientURI),
                SharingDetail(label: "Privacy policy", value: policyURI, linkURI: policyURI),
                SharingDetail(label: "Terms of service", value: termsOfServiceURI, linkURI: termsOfServiceURI),
            ]
        )
        return requester.hasContent ? requester : nil
    }
}

private extension PresentationResponseEncryption {
    /// The response protection this encryption requirement describes.
    func sharingResponseProtection(mechanism: SharingEncryptionMechanism) -> SharingResponseProtection {
        switch self {
        case .notRequired:
            return .none
        case .required(let details):
            return .encrypted(
                mechanism: mechanism,
                keyManagementAlgorithm: details.keyManagementAlgorithm,
                contentEncryptionAlgorithm: details.contentEncryptionAlgorithm,
                verifierKeyID: details.verifierKeyID,
                verifierKeyThumbprint: details.verifierKeyThumbprint
            )
        }
    }
}

private extension Array where Element: Hashable {
    /// Deduplicates while keeping request order, so requirements read in the order asked.
    func distinctPreservingOrder() -> [Element] {
        var seen: Set<Element> = []
        return filter { seen.insert($0).inserted }
    }
}
