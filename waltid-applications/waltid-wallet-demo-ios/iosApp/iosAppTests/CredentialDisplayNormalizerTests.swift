import Foundation
import WalletSDK
import XCTest
@testable import iosApp

final class CredentialDisplayNormalizerTests: XCTestCase {

    func testFlattensNamespacedMdocObjectClaimsIntoDisplayRows() {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "mso_mdoc",
            issuer: nil,
            subject: nil,
            format: "mso_mdoc",
            addedAt: nil,
            credentialDataJSON: """
            {
              "docType": "eu.europa.ec.eudi.pid.1",
              "eu.europa.ec.eudi.pid.1": {
                "resident_state": "Vienna",
                "birth_place": {
                  "locality": "Vienna",
                  "country": "Austria"
                }
              }
            }
            """
        )

        let credentialData = details.groups.first { $0.title == "Credential data" }
        XCTAssertEqual(
            credentialData?.items.map(\.path.id),
            [
                "docType",
                "eu.europa.ec.eudi.pid.1.birth_place.locality",
                "eu.europa.ec.eudi.pid.1.birth_place.country",
                "eu.europa.ec.eudi.pid.1.resident_state"
            ]
        )
        XCTAssertEqual(credentialData?.items.contains { item in
            if case .object = item.value { return true }
            return false
        }, false)
    }

    func testRendersMdocAgeAttestationsWithoutAbsentClaims() throws {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "Photo ID",
            issuer: nil,
            subject: nil,
            format: "mso_mdoc",
            addedAt: nil,
            credentialDataJSON: """
            {
              "org.iso.23220.1": {
                "given_name": "Erika",
                "age_over_62": null,
                "age_over_65": false,
                "age_over_18": true,
                "age_over_21": "yes"
              },
              "org.iso.23220.dtc.1": {
                "version": null,
                "sod": null,
                "dg1": null
              }
            }
            """
        )

        let age = try XCTUnwrap(details.groups.first { $0.title == "Age attestations" })
        XCTAssertFalse(age.initiallyExpanded)
        XCTAssertEqual(age.items.map(\.label), ["18 or older", "21 or older", "65 or older"])
        XCTAssertEqual(age.items.map(\.value), [.bool(true), .text("Unsupported value"), .bool(false)])
        XCTAssertFalse(details.groups.flatMap(\.items).contains { $0.path.id.contains("age_over_62") })
        XCTAssertFalse(details.groups.contains { $0.title == "Travel document data" })
        XCTAssertEqual(details.groups.first { $0.title == "Credential data" }?.initiallyExpanded, true)
    }

    func testSummarizesProvidedMdocTravelDocumentData() throws {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "Photo ID",
            issuer: nil,
            subject: nil,
            format: "mso_mdoc",
            addedAt: nil,
            credentialDataJSON: """
            {
              "org.iso.23220.dtc.1": {
                "dtc_dg2": [1, 2, 3, 4],
                "dtc_dg1": "machine-readable-zone",
                "dtc_sod": [1, 2, 3],
                "dg_content_info": [1, 2],
                "dtc_version": "1.0"
              }
            }
            """
        )

        let travel = try XCTUnwrap(details.groups.first { $0.title == "Travel document data" })
        XCTAssertFalse(travel.initiallyExpanded)
        XCTAssertEqual(
            travel.items.map(\.label),
            [
                "Specification version",
                "Document security object (SOD)",
                "DG1: Machine-readable zone",
                "DG2: Facial image",
                "Document content information"
            ]
        )
        XCTAssertEqual(
            travel.items.map(\.value),
            [
                .text("1.0"),
                .text("Available, 3 bytes"),
                .text("Available"),
                .text("Available, 4 bytes"),
                .text("Available, 2 bytes")
            ]
        )
    }

    func testKeepsNullClaimsForNonMdocCredentials() {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "PID",
            issuer: nil,
            subject: nil,
            format: "dc+sd-jwt",
            addedAt: nil,
            credentialDataJSON: #"{"middle_name": null}"#
        )

        XCTAssertEqual(details.groups.single?.items.single?.value, .null)
    }

    func testDoesNotApplyMdocSemanticsOutsideStandardNamespaces() {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "Example",
            issuer: nil,
            subject: nil,
            format: "mso_mdoc",
            addedAt: nil,
            credentialDataJSON: #"{"example.namespace":{"age_over_18":true,"dg1":[1,2]}}"#
        )

        XCTAssertFalse(details.groups.contains { $0.title == "Age attestations" || $0.title == "Travel document data" })
    }

    func testSortsKnownClaimRowsByCredentialVocabularyInsteadOfIssuerJSONOrder() throws {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "PID",
            issuer: nil,
            subject: nil,
            format: "dc+sd-jwt",
            addedAt: nil,
            credentialDataJSON: """
            {
              "portrait": "data:image/png;base64,\(Self.onePixelPNGBase64)",
              "nationalities": ["AT", "CH"],
              "place_of_birth": {
                "region": "Vienna",
                "country": "AT",
                "locality": "Vienna"
              },
              "family_name": "Tester",
              "birthdate": "1971-09-01",
              "given_name": "Alice"
            }
            """
        )

        let personal = try XCTUnwrap(details.groups.first { $0.title == "Personal details" })
        XCTAssertEqual(
            personal.items.map(\.path.id),
            [
                "given_name",
                "family_name",
                "birthdate",
                "place_of_birth.locality",
                "place_of_birth.country",
                "place_of_birth.region",
                "nationalities",
                "portrait"
            ]
        )
    }

    func testKeepsNestedMdocPortraitLabelWhenFlatteningNamespaceObject() throws {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "mso_mdoc",
            issuer: nil,
            subject: nil,
            format: "mso_mdoc",
            addedAt: nil,
            credentialDataJSON: """
            {
              "eu.europa.ec.eudi.pid.1": {
                "portrait": {
                  "elementValue": \(onePixelPNGByteArrayJSON())
                }
              }
            }
            """
        )

        let portrait = try XCTUnwrap(
            details.groups.flatMap(\.items).first { $0.path.id == "eu.europa.ec.eudi.pid.1.portrait.elementValue" }
        )
        XCTAssertEqual(portrait.label, "Portrait")
        guard case .image(_, let data, let mimeType, let byteCount) = portrait.value else {
            return XCTFail("Expected portrait to decode as image")
        }
        XCTAssertEqual(mimeType, "image/png")
        XCTAssertEqual(byteCount, onePixelPNGData.count)
        XCTAssertEqual(data, onePixelPNGData)
    }

    func testRendersSdJwtProtocolDataAsReadableMetadataAndKeepsClaimsGrouped() throws {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "PID (SD-JWT VC)",
            issuer: "https://issuer.example",
            subject: nil,
            format: "dc+sd-jwt",
            addedAt: nil,
            credentialDataJSON: """
            {
              "_sd": [
                "09vKrJMOlyTWM0sjpu_pdOBVBQ2M1y3KhpH515nXkpY",
                "2rsjGbaC0ky8mT0pJrPioWTq0_daw1sX76poUlgCwbI"
              ],
              "iss": "https://issuer.example",
              "iat": 1736899200,
              "exp": 1894699800,
              "vct": "eu.europa.ec.eudi.pid.1",
              "_sd_alg": "sha-256",
              "cnf": {
                "jwk": {
                  "kty": "EC",
                  "crv": "P-256",
                  "x": "very-long-x-coordinate",
                  "y": "very-long-y-coordinate"
                }
              },
              "given_name": "Alice",
              "family_name": "Tester",
              "birthdate": "1990-01-01",
              "place_of_birth": {
                "locality": "Berlin",
                "country": "DE"
              },
              "resident_country": "AT",
              "resident_state": "Vienna",
              "document_number": "A01234567"
            }
            """
        )

        let personal = try XCTUnwrap(details.groups.first { $0.title == "Personal details" })
        XCTAssertEqual(personal.items.first { $0.path.id == "given_name" }?.value, .text("Alice"))
        XCTAssertEqual(personal.items.first { $0.path.id == "birthdate" }?.label, "Date of birth")
        XCTAssertEqual(personal.items.first { $0.path.id == "place_of_birth.locality" }?.label, "Locality")

        let address = try XCTUnwrap(details.groups.first { $0.title == "Address" })
        XCTAssertEqual(address.items.first { $0.path.id == "resident_country" }?.label, "Resident country")
        XCTAssertEqual(address.items.first { $0.path.id == "resident_state" }?.value, .text("Vienna"))

        let credentialData = try XCTUnwrap(details.groups.first { $0.title == "Credential data" })
        XCTAssertEqual(credentialData.items.first { $0.path.id == "document_number" }?.label, "Document number")

        let technical = try XCTUnwrap(details.groups.first { $0.title == "Credential metadata" })
        XCTAssertEqual(technical.items.first { $0.path.id == "_sd" }?.value, .text("2 undisclosed claim values"))
        XCTAssertEqual(technical.items.first { $0.path.id == "cnf" }?.value, .text("Key-bound credential - EC - P-256"))
        XCTAssertEqual(technical.items.first { $0.path.id == "iat" }?.value, .text("2025-01-15"))
        XCTAssertEqual(technical.items.first { $0.path.id == "exp" }?.value, .text("2030-01-15"))
        XCTAssertFalse(technical.items.contains { item in
            item.value == .text("very-long-x-coordinate") ||
                item.value == .text("very-long-y-coordinate")
        })
    }

    func testRendersUndisclosedSdJwtCredentialAsReadableMetadata() throws {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "PID (SD-JWT VC)",
            issuer: "https://backend.issuer.eudiw.dev",
            subject: nil,
            format: "dc+sd-jwt",
            addedAt: nil,
            credentialDataJSON: """
            {
              "_sd": [
                "064HX7n-bAY4l5g0JpiA4wnrJ2ufmT1gYv9s5r5TaZI",
                "2HP56d9-TgEG8BERV6z9n0MKBaxeTLkcC-R_5SGiL_M",
                "71g4sTDmdx29r5RkywJ1gM4pvYA0UShWpHuIP4nwP2E"
              ],
              "iss": "https://backend.issuer.eudiw.dev",
              "iat": 1783897200,
              "exp": 1791673200,
              "vct": "urn:eudi:pid:1",
              "status": {
                "status_list": {
                  "idx": 8965,
                  "uri": "https://issuer.eudiw.dev/token_status_list/FC/urn:eudi:pid:1/15a4de9f-d535-4441-8564-02784d7b1d30"
                }
              },
              "_sd_alg": "sha-256",
              "cnf": {
                "jwk": {
                  "kty": "EC",
                  "crv": "P-256",
                  "x": "long-x-coordinate",
                  "y": "long-y-coordinate"
                }
              }
            }
            """
        )

        XCTAssertFalse(details.groups.contains { $0.title == "Personal details" })
        XCTAssertFalse(details.groups.contains { $0.title == "Address" })

        let metadata = try XCTUnwrap(details.groups.first { $0.title == "Credential metadata" })
        XCTAssertEqual(metadata.items.first { $0.path.id == "_sd" }?.label, "Undisclosed claims")
        XCTAssertEqual(metadata.items.first { $0.path.id == "_sd" }?.value, .text("3 undisclosed claim values"))
        XCTAssertEqual(metadata.items.first { $0.path.id == "vct" }?.value, .text("Pid 1 (urn:eudi:pid:1)"))
        XCTAssertEqual(
            metadata.items.first { $0.path.id == "status" }?.value,
            .text("Status list index 8965 - https://issuer.eudiw.dev/token_status_list/FC/urn:eudi:pid:1/15a4de9f-d535-4441-8564-02784d7b1d30")
        )
        XCTAssertEqual(metadata.items.first { $0.path.id == "cnf" }?.value, .text("Key-bound credential - EC - P-256"))
        XCTAssertFalse(metadata.items.contains { item in
            item.path.id.contains("status_list") ||
                item.path.id == "cnf.jwk.x" ||
                item.path.id == "cnf.jwk.y" ||
                item.value == .text("long-x-coordinate") ||
                item.value == .text("long-y-coordinate")
        })
    }

    func testRendersAllSupportedCredentialFormats() {
        let cases: [(format: String, title: String, credentialDataJSON: String, expectedHolderName: String, expectedCredentialType: String?, expectedClaimPath: String)] = [
            (
                format: "jwt_vc_json",
                title: "Person credential",
                credentialDataJSON: """
                {
                  "@context": ["https://www.w3.org/2018/credentials/v1"],
                  "type": ["VerifiableCredential", "PersonCredential"],
                  "issuer": "did:web:issuer.example",
                  "credentialSubject": {
                    "given_name": "Ada",
                    "family_name": "Lovelace",
                    "portrait": "data:image/png;base64,\(Self.onePixelPNGBase64)"
                  }
                }
                """,
                expectedHolderName: "Ada Lovelace",
                expectedCredentialType: "Person credential",
                expectedClaimPath: "credentialSubject.portrait"
            ),
            (
                format: "jwt_vc_json-ld",
                title: "JSON-LD credential",
                credentialDataJSON: """
                {
                  "@context": ["https://www.w3.org/2018/credentials/v1"],
                  "type": ["VerifiableCredential", "EmployeeCredential"],
                  "issuer": "did:web:issuer.example",
                  "credentialSubject": {
                    "given_name": "Jane",
                    "family_name": "Employee",
                    "role": "Engineer"
                  }
                }
                """,
                expectedHolderName: "Jane Employee",
                expectedCredentialType: "Employee credential",
                expectedClaimPath: "credentialSubject.role"
            ),
            (
                format: "ldp_vc",
                title: "Linked data credential",
                credentialDataJSON: """
                {
                  "@context": ["https://www.w3.org/2018/credentials/v1"],
                  "type": ["VerifiableCredential", "UniversityDegreeCredential"],
                  "issuer": "did:web:issuer.example",
                  "credentialSubject": {
                    "given_name": "Lin",
                    "family_name": "Graduate",
                    "degree": {
                      "type": "BachelorDegree",
                      "name": "Bachelor of Science"
                    }
                  },
                  "proof": {
                    "type": "DataIntegrityProof",
                    "cryptosuite": "eddsa-rdfc-2022"
                  }
                }
                """,
                expectedHolderName: "Lin Graduate",
                expectedCredentialType: "University degree credential",
                expectedClaimPath: "credentialSubject.degree.name"
            ),
            (
                format: "jwt_vc",
                title: "Legacy JWT VC",
                credentialDataJSON: """
                {
                  "vc": {
                    "type": ["VerifiableCredential", "LegacyPersonCredential"],
                    "credentialSubject": {
                      "given_name": "Legacy",
                      "family_name": "Holder",
                      "member_id": "123"
                    }
                  }
                }
                """,
                expectedHolderName: "Legacy Holder",
                expectedCredentialType: "Legacy person credential",
                expectedClaimPath: "vc.credentialSubject.member_id"
            ),
            (
                format: "dc+sd-jwt",
                title: "PID SD-JWT VC",
                credentialDataJSON: """
                {
                  "vct": "urn:eudi:pid:1",
                  "_sd": ["digest-1"],
                  "cnf": {"kid": "holder-key-1"},
                  "given_name": "Alice",
                  "family_name": "Tester",
                  "exp": 1894699800
                }
                """,
                expectedHolderName: "Alice Tester",
                expectedCredentialType: "Pid 1",
                expectedClaimPath: "cnf"
            ),
            (
                format: "vc+sd-jwt",
                title: "PID SD-JWT VC legacy alias",
                credentialDataJSON: """
                {
                  "vct": "eu.europa.ec.eudi.pid.1",
                  "_sd": [],
                  "given_name": "Ali",
                  "family_name": "Alias",
                  "iss": "https://issuer.example"
                }
                """,
                expectedHolderName: "Ali Alias",
                expectedCredentialType: "Pid 1",
                expectedClaimPath: "_sd"
            ),
            (
                format: "vc-sd_jwt",
                title: "Stored SD-JWT VC alias",
                credentialDataJSON: """
                {
                  "vct": "https://credentials.example/mobile-driving-licence",
                  "given_name": "Sam",
                  "family_name": "Stored",
                  "cnf": {"kid": "holder-key-2"}
                }
                """,
                expectedHolderName: "Sam Stored",
                expectedCredentialType: "Mobile driving licence",
                expectedClaimPath: "cnf"
            ),
            (
                format: "mso_mdoc",
                title: "EUDI PID mdoc",
                credentialDataJSON: """
                {
                  "docType": "eu.europa.ec.eudi.pid.1",
                  "eu.europa.ec.eudi.pid.1": {
                    "given_name": "Anna",
                    "family_name": "Musterfrau",
                    "resident_state": "Vienna"
                  }
                }
                """,
                expectedHolderName: "Anna Musterfrau",
                expectedCredentialType: nil,
                expectedClaimPath: "eu.europa.ec.eudi.pid.1.resident_state"
            ),
            (
                format: "mso_mdoc",
                title: "ISO mDL",
                credentialDataJSON: """
                {
                  "docType": "org.iso.18013.5.1.mDL",
                  "org.iso.18013.5.1": {
                    "given_name": "Max",
                    "family_name": "Driver",
                    "document_number": "D1234567"
                  }
                }
                """,
                expectedHolderName: "Max Driver",
                expectedCredentialType: nil,
                expectedClaimPath: "org.iso.18013.5.1.document_number"
            )
        ]

        for credential in cases {
            let details = CredentialDisplayNormalizer.details(
                id: "cred-\(credential.format)-\(credential.title)",
                title: credential.title,
                issuer: "Example Issuer",
                subject: "did:key:holder",
                format: credential.format,
                addedAt: nil,
                credentialDataJSON: credential.credentialDataJSON
            )
            let claims = details.groups.flatMap(\.items)

            XCTAssertEqual(details.cardSummary.holderName, credential.expectedHolderName, credential.title)
            XCTAssertEqual(details.cardSummary.credentialType, credential.expectedCredentialType, credential.title)
            XCTAssertTrue(claims.contains { $0.path.id == credential.expectedClaimPath }, credential.title)
            XCTAssertFalse(claims.contains { item in
                if case .object = item.value { return true }
                return false
            }, credential.title)
        }
    }

    func testClassifiesW3CSubjectClaimsByDisplayedClaimInsteadOfOuterWrapper() throws {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "Person credential",
            issuer: "did:web:issuer.example",
            subject: "did:key:holder",
            format: "jwt_vc_json",
            addedAt: nil,
            credentialDataJSON: """
            {
              "@context": ["https://www.w3.org/2018/credentials/v1"],
              "type": ["VerifiableCredential", "PersonCredential"],
              "issuer": "did:web:issuer.example",
              "credentialSubject": {
                "given_name": "Ada",
                "family_name": "Lovelace",
                "resident_country": "AT",
                "employee_id": "E-123"
              },
              "proof": {
                "type": "DataIntegrityProof",
                "cryptosuite": "eddsa-rdfc-2022"
              }
            }
            """
        )

        let personal = try XCTUnwrap(details.groups.first { $0.title == "Personal details" })
        XCTAssertEqual(personal.items.first { $0.path.id == "credentialSubject.given_name" }?.value, .text("Ada"))
        XCTAssertEqual(personal.items.first { $0.path.id == "credentialSubject.family_name" }?.value, .text("Lovelace"))

        let address = try XCTUnwrap(details.groups.first { $0.title == "Address" })
        XCTAssertEqual(address.items.first { $0.path.id == "credentialSubject.resident_country" }?.value, .text("AT"))

        let credentialData = try XCTUnwrap(details.groups.first { $0.title == "Credential data" })
        XCTAssertEqual(credentialData.items.first { $0.path.id == "credentialSubject.employee_id" }?.value, .text("E-123"))

        let technical = try XCTUnwrap(details.groups.first { $0.title == "Credential metadata" })
        XCTAssertTrue(technical.items.contains { $0.path.id == "@context" })
        XCTAssertTrue(technical.items.contains { $0.path.id == "type" })
        XCTAssertTrue(technical.items.contains { $0.path.id == "issuer" })
        XCTAssertTrue(technical.items.contains { $0.path.id == "proof.type" })
    }

    func testClassifiesJWTVCEnvelopeTechnicalContainersLikeUnwrappedW3CCredentials() throws {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "Person credential",
            issuer: "did:web:issuer.example",
            subject: "did:key:holder",
            format: "jwt_vc_json",
            addedAt: nil,
            credentialDataJSON: """
            {
              "iss": "did:web:issuer.example",
              "sub": "did:key:holder",
              "vc": {
                "@context": ["https://www.w3.org/2018/credentials/v1"],
                "type": ["VerifiableCredential", "PersonCredential"],
                "credentialSubject": {
                  "given_name": "Ada",
                  "family_name": "Lovelace",
                  "resident_country": "AT",
                  "employee_id": "E-123"
                },
                "credentialStatus": {
                  "id": "https://issuer.example/status/1",
                  "type": "StatusList2021Entry"
                }
              }
            }
            """
        )

        let personal = try XCTUnwrap(details.groups.first { $0.title == "Personal details" })
        XCTAssertEqual(personal.items.first { $0.path.id == "vc.credentialSubject.given_name" }?.value, .text("Ada"))
        XCTAssertEqual(personal.items.first { $0.path.id == "vc.credentialSubject.family_name" }?.value, .text("Lovelace"))

        let address = try XCTUnwrap(details.groups.first { $0.title == "Address" })
        XCTAssertEqual(address.items.first { $0.path.id == "vc.credentialSubject.resident_country" }?.value, .text("AT"))

        let credentialData = try XCTUnwrap(details.groups.first { $0.title == "Credential data" })
        XCTAssertEqual(credentialData.items.first { $0.path.id == "vc.credentialSubject.employee_id" }?.value, .text("E-123"))

        let technical = try XCTUnwrap(details.groups.first { $0.title == "Credential metadata" })
        XCTAssertTrue(technical.items.contains { $0.path.id == "iss" })
        XCTAssertTrue(technical.items.contains { $0.path.id == "sub" })
        XCTAssertTrue(technical.items.contains { $0.path.id == "vc.type" })
        XCTAssertTrue(technical.items.contains { $0.path.id == "vc.@context" })
        XCTAssertEqual(
            technical.items.first { $0.path.id == "vc.credentialStatus" }?.value,
            .text("StatusList2021Entry - https://issuer.example/status/1")
        )
    }

    func testUsesPortraitImageForCardSummary() {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "mso_mdoc",
            issuer: nil,
            subject: nil,
            format: "mso_mdoc",
            addedAt: nil,
            credentialDataJSON: #"{"portrait":{"elementValue":\#(onePixelPNGByteArrayJSON())}}"#
        )

        XCTAssertEqual(details.cardSummary.portraitData, onePixelPNGData)
        XCTAssertEqual(details.cardSummary.portraitMimeType, "image/png")
    }

    func testValidatesDataURIImageBytesBeforeUsingMimeHint() throws {
        let encodedJSON = try XCTUnwrap(#"{"purpose":"age proof"}"#.data(using: .utf8)?.base64EncodedString())
        let encodedText = try XCTUnwrap("Hello, wallet".data(using: .utf8)?.base64EncodedString())
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "vc+sd-jwt",
            issuer: nil,
            subject: nil,
            format: "vc+sd-jwt",
            addedAt: nil,
            credentialDataJSON: """
            {
              "json_note": "data:image/png;base64,\(encodedJSON)",
              "plain_note": "data:image/webp;base64,\(encodedText)",
              "portrait": "data:image/png;base64,\(Self.onePixelPNGBase64)"
            }
            """
        )

        let claims = details.groups.flatMap(\.items)
        XCTAssertEqual(claims.first { $0.path.id == "json_note.purpose" }?.value, .text("age proof"))
        XCTAssertEqual(claims.first { $0.path.id == "plain_note" }?.value, .decodedText("Hello, wallet"))
        guard case .image(_, _, let mimeType, _) = claims.first(where: { $0.path.id == "portrait" })?.value else {
            return XCTFail("Expected valid PNG data URI to render as an image")
        }
        XCTAssertEqual(mimeType, "image/png")
    }

    func testPresentationCredentialOptionPrependsRequestedDisclosures() throws {
        let option = PresentationCredentialOption(
            queryID: "pid",
            credentialID: "credential-1",
            format: "dc+sd-jwt",
            issuer: "https://issuer.example",
            subject: "did:key:holder",
            label: "PID",
            credentialDataJSON: """
            {
              "given_name": "Alice",
              "family_name": "Tester"
            }
            """,
            disclosures: [
                PresentationDisclosure(
                    path: #"["$","given_name"]"#,
                    name: "given_name",
                    valueJSON: #""Alice""#,
                    displayValue: "Alice",
                    selectivelyDisclosable: true,
                    required: true,
                    selectable: false
                ),
                PresentationDisclosure(
                    path: #"["eu.europa.ec.eudi.pid.1","portrait"]"#,
                    name: "portrait",
                    valueJSON: onePixelPNGByteArrayJSON(),
                    displayValue: nil,
                    selectivelyDisclosable: true,
                    required: false,
                    selectable: true
                )
            ]
        )

        let details = CredentialDisplayNormalizer.details(for: option)

        let requested = try XCTUnwrap(details.groups.first)
        XCTAssertEqual(requested.title, "Requested disclosures")
        XCTAssertEqual(requested.items.map(\.label), ["Given name", "Portrait"])
        XCTAssertEqual(requested.items.map(\.path.id), ["disclosures[0].given_name", "disclosures[1].portrait"])
        XCTAssertEqual(requested.items.first?.value, .text("Alice"))
        guard case .image(_, _, let mimeType, let byteCount) = requested.items.last?.value else {
            return XCTFail("Expected requested portrait disclosure to render as an image")
        }
        XCTAssertEqual(mimeType, "image/png")
        XCTAssertEqual(byteCount, onePixelPNGData.count)

        let personal = try XCTUnwrap(details.groups.first { $0.title == "Personal details" })
        XCTAssertEqual(personal.items.map(\.label), ["Given name", "Family name"])
        XCTAssertEqual(details.id, option.selection.id)
    }

    func testPresentationCredentialOptionOmitsRequestedDisclosuresWhenEmpty() {
        let option = PresentationCredentialOption(
            queryID: "pid",
            credentialID: "credential-1",
            format: "dc+sd-jwt",
            issuer: nil,
            subject: nil,
            label: "PID",
            credentialDataJSON: """
            {
              "given_name": "Alice"
            }
            """,
            disclosures: []
        )

        let details = CredentialDisplayNormalizer.details(for: option)

        XCTAssertFalse(details.groups.contains { $0.title == "Requested disclosures" })
        XCTAssertEqual(details.groups.first?.title, "Personal details")
    }

    func testBuildsCredentialInfoGroupFromWalletSummaryFields() throws {
        let addedAt = try XCTUnwrap(Self.isoDateFormatter.date(from: "2026-07-09T12:00:00Z"))
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "PID",
            issuer: "Example Issuer",
            subject: "did:key:holder",
            format: "vc+sd-jwt",
            addedAt: addedAt,
            credentialDataJSON: "{}"
        )

        let systemInfo = try XCTUnwrap(details.systemInfoGroup)
        XCTAssertEqual(systemInfo.title, "About this credential")
        XCTAssertFalse(systemInfo.initiallyExpanded)
        XCTAssertEqual(systemInfo.items.map(\.path.id), [
            "system.added",
            "system.id",
            "system.format",
            "system.issuer",
            "system.subject"
        ])
        XCTAssertEqual(systemInfo.items.map(\.label), [
            "Added",
            "Credential ID",
            "Format",
            "Issuer",
            "Subject"
        ])
        XCTAssertEqual(systemInfo.items.map(\.value), [
            .text("2026-07-09T12:00:00Z"),
            .text("cred-1"),
            .text("vc+sd-jwt"),
            .text("Example Issuer"),
            .text("did:key:holder")
        ])
    }

    func testLeavesMalformedCredentialJSONWithoutDisplayGroups() {
        let details = CredentialDisplayNormalizer.details(
            id: "cred-1",
            title: "Broken credential",
            issuer: nil,
            subject: nil,
            format: "jwt_vc_json",
            addedAt: nil,
            credentialDataJSON: "{not-json"
        )

        XCTAssertTrue(details.groups.isEmpty)
    }

    func testTransactionDataGroupsRenderProfileAndDetailsReadably() throws {
        let request = PresentationRequestInfo(
            clientID: "https://verifier.example",
            nonce: "nonce-1",
            responseEncryption: .notRequired,
            transactionData: [
                PresentationTransactionData(
                    type: "org.waltid.transaction-data.payment-authorization",
                    displayName: "Payment Authorization",
                    credentialQueryIDs: ["pid", "payment"],
                    supportedFields: ["amount", "currency", "payee"],
                    rawJSON: """
                    {
                      "type": "org.waltid.transaction-data.payment-authorization",
                      "credential_ids": ["pid", "payment"],
                      "amount": "42.00",
                      "currency": "EUR",
                      "payee": "ACME Corp"
                    }
                    """,
                    detailsJSON: """
                    {
                      "amount": "42.00",
                      "currency": "EUR",
                      "payee": "ACME Corp"
                    }
                    """
                )
            ]
        )

        let payment = try XCTUnwrap(CredentialDisplayNormalizer.transactionDataGroups(for: request).single)
        let valuesByLabel = Dictionary(uniqueKeysWithValues: payment.items.map { item in
            (item.label, item.textValue ?? item.rawValue ?? "")
        })

        XCTAssertEqual(payment.title, "Payment Authorization")
        XCTAssertEqual(Array(payment.items.prefix(3).map(\.label)), ["Amount", "Currency", "Payee"])
        XCTAssertEqual(valuesByLabel["Type"], "org.waltid.transaction-data.payment-authorization")
        XCTAssertEqual(valuesByLabel["Credential queries"], "pid, payment")
        XCTAssertEqual(valuesByLabel["Amount"], "42.00")
        XCTAssertEqual(valuesByLabel["Currency"], "EUR")
        XCTAssertEqual(valuesByLabel["Payee"], "ACME Corp")
    }

    private func onePixelPNGByteArrayJSON() -> String {
        "[" + onePixelPNGData.map { String($0) }.joined(separator: ",") + "]"
    }

    private var onePixelPNGData: Data {
        Data(base64Encoded: Self.onePixelPNGBase64)!
    }

    private static let onePixelPNGBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="

    private static let isoDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        return formatter
    }()
}

private extension ClaimItem {
    var textValue: String? {
        if case .text(let value) = self.value {
            return value
        }
        return nil
    }
}

private extension Collection {
    var single: Element? {
        count == 1 ? first : nil
    }
}
