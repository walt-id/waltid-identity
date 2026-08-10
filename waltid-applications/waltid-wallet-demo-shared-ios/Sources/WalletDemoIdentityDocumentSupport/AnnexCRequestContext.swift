import Foundation
#if os(iOS)
import IdentityDocumentServices
import IdentityDocumentServicesUI
#endif

/// The three things a provider extension may do with Apple's request context.
///
/// Abstracted so the presentation orchestration can be driven without an iOS 26 request - Apple's
/// context is a struct the extension receives, not one a test can build - and so the ISO 18013-7
/// Annex C two-stage flow stays structurally enforced: the raw request is reachable only inside the
/// ``sendResponse(_:)`` closure, which the platform invokes after the user has consented.
public protocol AnnexCRequestContext: Sendable {
    /// Apple's pre-consent request, as values.
    var requestSnapshot: MobileDocumentRequestSnapshot { get }

    /// Origin the platform verified for the requesting website, if it asserted one.
    var verifiedOrigin: URL? { get }

    /// Dismisses the request without releasing any credential data.
    func cancelRequest()

    /// Asks the platform for the raw request and returns the sealed Annex C response bytes.
    func sendResponse(
        _ build: @escaping @Sendable (RawAnnexCRequest) async throws -> Data
    ) async throws
}

#if os(iOS)
@available(iOS 26.0, *)
extension ISO18013MobileDocumentRequestContext: AnnexCRequestContext {
    public var requestSnapshot: MobileDocumentRequestSnapshot {
        MobileDocumentRequestSnapshot(request)
    }

    public var verifiedOrigin: URL? {
        requestingWebsiteOrigin
    }

    public func cancelRequest() {
        cancel()
    }

    public func sendResponse(
        _ build: @escaping @Sendable (RawAnnexCRequest) async throws -> Data
    ) async throws {
        try await sendResponse { rawRequest in
            ISO18013MobileDocumentResponse(
                responseData: try await build(RawAnnexCRequest(data: rawRequest.requestData))
            )
        }
    }
}
#endif
