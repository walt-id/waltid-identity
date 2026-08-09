import XCTest
import WalletDemoIdentityDocumentSupport
import WalletSDK

/// Pure provider mappings: Apple request to parsed request, response envelope to sealed bytes, and
/// reader trust to display text.
///
/// These are the transformations the extension would otherwise hide behind an iOS 26 request that no
/// test can construct.
final class IdentityDocumentProviderMappingTests: XCTestCase {
    private let mdl = "org.iso.18013.5.1.mDL"
    private let pid = "eu.europa.ec.eudi.pid.1"

    func testEveryPresentmentContributesItsRequestedElements() throws {
        let snapshot = MobileDocumentRequestSnapshot(presentments: [
            MobileDocumentPresentmentSnapshot(documentRequestSets: [
                MobileDocumentRequestSetSnapshot(requests: [
                    AnnexCDocumentRequest(
                        documentType: mdl,
                        namespaces: ["org.iso.18013.5.1": ["family_name", "portrait"]]
                    ),
                ]),
            ]),
            MobileDocumentPresentmentSnapshot(documentRequestSets: [
                MobileDocumentRequestSetSnapshot(requests: [
                    AnnexCDocumentRequest(
                        documentType: pid,
                        namespaces: ["eu.europa.ec.eudi.pid.1": ["birth_date"]]
                    ),
                ]),
            ]),
        ])

        let parsed = try parsedRequest(from: snapshot)

        XCTAssertEqual(parsed.documents.map(\.documentType), [mdl, pid])
        XCTAssertEqual(parsed.documents[0].namespaces["org.iso.18013.5.1"], ["family_name", "portrait"])
    }

    func testAlternativeRequestSetsAreRejectedRatherThanSilentlyNarrowed() {
        let snapshot = MobileDocumentRequestSnapshot(presentments: [
            MobileDocumentPresentmentSnapshot(documentRequestSets: [
                MobileDocumentRequestSetSnapshot(requests: [
                    AnnexCDocumentRequest(documentType: mdl, namespaces: [:]),
                ]),
                MobileDocumentRequestSetSnapshot(requests: [
                    AnnexCDocumentRequest(documentType: pid, namespaces: [:]),
                ]),
            ]),
        ])

        XCTAssertThrowsError(try parsedRequest(from: snapshot)) { error in
            XCTAssertEqual(
                error as? IdentityDocumentSupportFailure,
                .alternativeRequestSetsUnsupported,
                "Choosing a branch for the user would disclose more than the consent screen showed"
            )
        }
    }

    func testEmptyRequestIsRejected() {
        XCTAssertThrowsError(
            try parsedRequest(from: MobileDocumentRequestSnapshot(presentments: []))
        ) { error in
            XCTAssertEqual(error as? IdentityDocumentSupportFailure, .emptyRequest)
        }
    }

    func testSealedResponseBytesAreDecodedFromTheAnnexCEnvelope() throws {
        let sealed = Data([0xDE, 0xAD, 0xBE, 0xEF, 0xFA])
        let encoded = sealed.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")

        XCTAssertEqual(try encryptedResponseData(fromResponseJSON: #"{"response":"\#(encoded)"}"#), sealed)
    }

    func testResponseWithoutTheEnvelopeMemberIsRejected() {
        XCTAssertThrowsError(try encryptedResponseData(fromResponseJSON: #"{"vp_token":"x"}"#)) { error in
            XCTAssertEqual(error as? IdentityDocumentSupportFailure, .invalidResponseEncoding)
        }
    }

    func testNonBase64URLResponseIsRejected() {
        XCTAssertThrowsError(try encryptedResponseData(fromResponseJSON: #"{"response":"not base64!"}"#)) { error in
            XCTAssertEqual(error as? IdentityDocumentSupportFailure, .invalidResponseEncoding)
        }
    }

    func testReaderTrustStatesAreDistinguishableToTheUser() {
        let descriptions = [
            readerTrustDescription(.notApplicable),
            readerTrustDescription(.notAuthenticated),
            readerTrustDescription(.pendingRawRequest),
            readerTrustDescription(.untrusted(reason: "no anchor")),
            readerTrustDescription(.trusted(certificateSubject: "CN=Verifier")),
        ]

        XCTAssertEqual(Set(descriptions).count, descriptions.count, "Trust states must not read alike")
        XCTAssertTrue(readerTrustDescription(.trusted(certificateSubject: "CN=Verifier")).contains("CN=Verifier"))
        XCTAssertTrue(readerTrustDescription(.untrusted(reason: "no anchor")).contains("no anchor"))
    }

    func testTheTwoDemosDoNotShareACrossProcessNamespace() {
        let native = IdentityDocumentNamespace.nativeDemo
        let compose = IdentityDocumentNamespace.composeDemo

        XCTAssertNotEqual(native.appGroupIdentifier, compose.appGroupIdentifier)
        XCTAssertNotEqual(native.keychainAccessGroupSuffix, compose.keychainAccessGroupSuffix)
        XCTAssertEqual(
            native.supportedDocumentTypes,
            compose.supportedDocumentTypes,
            "Both demos issue the same credentials, so they must advertise the same doctypes"
        )
        XCTAssertFalse(
            native.supportedDocumentTypes.contains("org.iso.23220.photoid.1"),
            "Photo ID is not issued by the demo and must not be advertised"
        )
    }

    func testTheHostAppResolvesItsSharedKeychainGroupFromTheBuild() throws {
        // Proves INFOPLIST_KEY_WALTKeychainAccessGroup reached the built app: without it, the wallet
        // silently writes its signing key to a group the extension cannot read.
        let resolved = try XCTUnwrap(
            IdentityDocumentNamespace.nativeDemo.keychainAccessGroup,
            "The app bundle is missing \(IdentityDocumentNamespace.keychainAccessGroupInfoKey)"
        )

        XCTAssertTrue(
            resolved.hasSuffix(IdentityDocumentNamespace.nativeDemo.keychainAccessGroupSuffix),
            "Expected the build to expand AppIdentifierPrefix ahead of the shared suffix, got \(resolved)"
        )
        XCTAssertNotEqual(
            resolved,
            IdentityDocumentNamespace.nativeDemo.keychainAccessGroupSuffix,
            "AppIdentifierPrefix was not expanded, so the group is not the one the entitlement grants"
        )
    }
}
