package id.walt.walletdemo.compose.logic

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class CredentialDisplayNormalizerTest {

    @Test
    fun parsesCredentialJsonIntoReadableClaimGroups() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = "did:web:issuer.example",
                subject = "did:key:holder",
                label = "PID",
                addedAt = "2026-07-09T12:00:00Z",
                credentialDataJson = """
                    {
                      "given_name": "Ada",
                      "family_name": "Lovelace",
                      "age_over_18": true,
                      "age": 28,
                      "resident_address": {
                        "street_address": "1 Infinite Loop",
                        "locality": "Cupertino"
                      },
                      "nationalities": ["AT", "HU"]
                    }
                """.trimIndent(),
            )
        )

        assertEquals("PID", details.summary.label)
        val personal = assertNotNull(details.groups.firstOrNull { it.title == "Personal details" })
        assertEquals("Given name", personal.items.first { it.path.id == "given_name" }.label)
        assertEquals(DisplayValue.Text("Ada"), personal.items.first { it.path.id == "given_name" }.value)
        assertEquals(DisplayValue.BooleanValue(true), personal.items.first { it.path.id == "age_over_18" }.value)
        assertEquals(DisplayValue.NumberValue("28"), personal.items.first { it.path.id == "age" }.value)

        val address = assertNotNull(details.groups.firstOrNull { it.title == "Address" })
        assertEquals("Street address", address.items.first { it.path.id == "resident_address.street_address" }.label)
        assertEquals(DisplayValue.Text("1 Infinite Loop"), address.items.first { it.path.id == "resident_address.street_address" }.value)
        assertEquals("Locality", address.items.first { it.path.id == "resident_address.locality" }.label)

        val systemInfo = assertNotNull(details.toSystemInfoGroup())
        assertEquals("About this credential", systemInfo.title)
        assertEquals(DisplayValue.Text("2026-07-09T12:00:00Z"), systemInfo.items.first { it.path.id == "system.added" }.value)
        assertEquals(DisplayValue.Text("cred-1"), systemInfo.items.first { it.path.id == "system.id" }.value)
        assertEquals(DisplayValue.Text("vc+sd-jwt"), systemInfo.items.first { it.path.id == "system.format" }.value)
        assertEquals(DisplayValue.Text("did:web:issuer.example"), systemInfo.items.first { it.path.id == "system.issuer" }.value)
        assertEquals(DisplayValue.Text("did:key:holder"), systemInfo.items.first { it.path.id == "system.subject" }.value)
    }

    @Test
    fun flattensNamespacedMdocObjectClaimsIntoDisplayRows() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                label = "mso_mdoc",
                credentialDataJson = """
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
                """.trimIndent(),
            )
        )

        val credentialData = assertNotNull(details.groups.firstOrNull { it.title == "Credential data" })
        assertEquals(
            listOf(
                "docType",
                "eu.europa.ec.eudi.pid.1.birth_place.locality",
                "eu.europa.ec.eudi.pid.1.birth_place.country",
                "eu.europa.ec.eudi.pid.1.resident_state",
            ),
            credentialData.items.map { it.path.id },
        )
        assertTrue(credentialData.items.none { it.value is DisplayValue.ObjectValue })
    }

    @Test
    fun sortsKnownClaimRowsByCredentialVocabularyInsteadOfIssuerJsonOrder() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "dc+sd-jwt",
                issuer = null,
                label = "PID",
                credentialDataJson = """
                    {
                      "portrait": "data:image/png;base64,$onePixelPngBase64",
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
                """.trimIndent(),
            )
        )

        val personal = assertNotNull(details.groups.firstOrNull { it.title == "Personal details" })
        assertEquals(
            listOf(
                "given_name",
                "family_name",
                "birthdate",
                "place_of_birth.locality",
                "place_of_birth.country",
                "place_of_birth.region",
                "nationalities",
                "portrait",
            ),
            personal.items.map { it.path.id },
        )
    }

    @Test
    fun rendersSdJwtProtocolDataAsReadableMetadataAndKeepsClaimsGrouped() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "dc+sd-jwt",
                issuer = "https://issuer.example",
                label = "PID (SD-JWT VC)",
                credentialDataJson = """
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
                """.trimIndent(),
            )
        )

        val personal = assertNotNull(details.groups.firstOrNull { it.title == "Personal details" })
        assertEquals("Alice", (personal.items.first { it.path.id == "given_name" }.value as DisplayValue.Text).value)
        assertEquals("Date of birth", personal.items.first { it.path.id == "birthdate" }.label)
        assertEquals("Locality", personal.items.first { it.path.id == "place_of_birth.locality" }.label)

        val address = assertNotNull(details.groups.firstOrNull { it.title == "Address" })
        assertEquals("Resident country", address.items.first { it.path.id == "resident_country" }.label)
        assertEquals("Vienna", (address.items.first { it.path.id == "resident_state" }.value as DisplayValue.Text).value)

        val credentialData = assertNotNull(details.groups.firstOrNull { it.title == "Credential data" })
        assertEquals("Document number", credentialData.items.first { it.path.id == "document_number" }.label)

        val technical = assertNotNull(details.groups.firstOrNull { it.title == "Credential metadata" })
        assertEquals(DisplayValue.Text("2 undisclosed claim values"), technical.items.first { it.path.id == "_sd" }.value)
        assertEquals(DisplayValue.Text("Key-bound credential - EC - P-256"), technical.items.first { it.path.id == "cnf" }.value)
        assertEquals(DisplayValue.Text("2025-01-15"), technical.items.first { it.path.id == "iat" }.value)
        assertEquals(DisplayValue.Text("2030-01-15"), technical.items.first { it.path.id == "exp" }.value)
        assertTrue(technical.items.none { item ->
            item.value == DisplayValue.Text("very-long-x-coordinate") ||
                item.value == DisplayValue.Text("very-long-y-coordinate")
        })
    }

    @Test
    fun rendersUndisclosedSdJwtCredentialAsReadableMetadata() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "dc+sd-jwt",
                issuer = "https://backend.issuer.eudiw.dev",
                label = "PID (SD-JWT VC)",
                credentialDataJson = """
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
                """.trimIndent(),
            )
        )

        assertTrue(details.groups.none { it.title == "Personal details" })
        assertTrue(details.groups.none { it.title == "Address" })

        val metadata = assertNotNull(details.groups.firstOrNull { it.title == "Credential metadata" })
        assertEquals("Undisclosed claims", metadata.items.first { it.path.id == "_sd" }.label)
        assertEquals(DisplayValue.Text("3 undisclosed claim values"), metadata.items.first { it.path.id == "_sd" }.value)
        assertEquals(DisplayValue.Text("Pid 1 (urn:eudi:pid:1)"), metadata.items.first { it.path.id == "vct" }.value)
        assertEquals(
            DisplayValue.Text("Status list index 8965 - https://issuer.eudiw.dev/token_status_list/FC/urn:eudi:pid:1/15a4de9f-d535-4441-8564-02784d7b1d30"),
            metadata.items.first { it.path.id == "status" }.value,
        )
        assertEquals(DisplayValue.Text("Key-bound credential - EC - P-256"), metadata.items.first { it.path.id == "cnf" }.value)
        assertTrue(metadata.items.none { item ->
            item.path.id.contains("status_list") ||
                item.path.id == "cnf.jwk.x" ||
                item.path.id == "cnf.jwk.y" ||
                item.value == DisplayValue.Text("long-x-coordinate") ||
                item.value == DisplayValue.Text("long-y-coordinate")
        })
    }

    @Test
    fun rendersAllSupportedCredentialFormats() {
        data class FormatCase(
            val format: String,
            val label: String,
            val credentialDataJson: String,
            val expectedHolderName: String,
            val expectedCredentialType: String?,
            val expectedClaimPath: String,
        )

        val cases = listOf(
            FormatCase(
                format = "jwt_vc_json",
                label = "Person credential",
                credentialDataJson = """
                    {
                      "@context": ["https://www.w3.org/2018/credentials/v1"],
                      "type": ["VerifiableCredential", "PersonCredential"],
                      "issuer": "did:web:issuer.example",
                      "credentialSubject": {
                        "given_name": "Ada",
                        "family_name": "Lovelace",
                        "portrait": "data:image/png;base64,$onePixelPngBase64"
                      }
                    }
                """.trimIndent(),
                expectedHolderName = "Ada Lovelace",
                expectedCredentialType = "Person credential",
                expectedClaimPath = "credentialSubject.portrait",
            ),
            FormatCase(
                format = "jwt_vc_json-ld",
                label = "JSON-LD credential",
                credentialDataJson = """
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
                """.trimIndent(),
                expectedHolderName = "Jane Employee",
                expectedCredentialType = "Employee credential",
                expectedClaimPath = "credentialSubject.role",
            ),
            FormatCase(
                format = "ldp_vc",
                label = "Linked data credential",
                credentialDataJson = """
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
                """.trimIndent(),
                expectedHolderName = "Lin Graduate",
                expectedCredentialType = "University degree credential",
                expectedClaimPath = "credentialSubject.degree.name",
            ),
            FormatCase(
                format = "jwt_vc",
                label = "Legacy JWT VC",
                credentialDataJson = """
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
                """.trimIndent(),
                expectedHolderName = "Legacy Holder",
                expectedCredentialType = "Legacy person credential",
                expectedClaimPath = "vc.credentialSubject.member_id",
            ),
            FormatCase(
                format = "dc+sd-jwt",
                label = "PID SD-JWT VC",
                credentialDataJson = """
                    {
                      "vct": "urn:eudi:pid:1",
                      "_sd": ["digest-1"],
                      "cnf": {"kid": "holder-key-1"},
                      "given_name": "Alice",
                      "family_name": "Tester",
                      "exp": 1894699800
                    }
                """.trimIndent(),
                expectedHolderName = "Alice Tester",
                expectedCredentialType = "Pid 1",
                expectedClaimPath = "cnf",
            ),
            FormatCase(
                format = "vc+sd-jwt",
                label = "PID SD-JWT VC legacy alias",
                credentialDataJson = """
                    {
                      "vct": "eu.europa.ec.eudi.pid.1",
                      "_sd": [],
                      "given_name": "Ali",
                      "family_name": "Alias",
                      "iss": "https://issuer.example"
                    }
                """.trimIndent(),
                expectedHolderName = "Ali Alias",
                expectedCredentialType = "Pid 1",
                expectedClaimPath = "_sd",
            ),
            FormatCase(
                format = "vc-sd_jwt",
                label = "Stored SD-JWT VC alias",
                credentialDataJson = """
                    {
                      "vct": "https://credentials.example/mobile-driving-licence",
                      "given_name": "Sam",
                      "family_name": "Stored",
                      "cnf": {"kid": "holder-key-2"}
                    }
                """.trimIndent(),
                expectedHolderName = "Sam Stored",
                expectedCredentialType = "Mobile driving licence",
                expectedClaimPath = "cnf",
            ),
            FormatCase(
                format = "mso_mdoc",
                label = "EUDI PID mdoc",
                credentialDataJson = """
                    {
                      "docType": "eu.europa.ec.eudi.pid.1",
                      "eu.europa.ec.eudi.pid.1": {
                        "given_name": "Anna",
                        "family_name": "Musterfrau",
                        "resident_state": "Vienna"
                      }
                    }
                """.trimIndent(),
                expectedHolderName = "Anna Musterfrau",
                expectedCredentialType = null,
                expectedClaimPath = "eu.europa.ec.eudi.pid.1.resident_state",
            ),
            FormatCase(
                format = "mso_mdoc",
                label = "ISO mDL",
                credentialDataJson = """
                    {
                      "docType": "org.iso.18013.5.1.mDL",
                      "org.iso.18013.5.1": {
                        "given_name": "Max",
                        "family_name": "Driver",
                        "document_number": "D1234567"
                      }
                    }
                """.trimIndent(),
                expectedHolderName = "Max Driver",
                expectedCredentialType = null,
                expectedClaimPath = "org.iso.18013.5.1.document_number",
            ),
        )

        cases.forEach { case ->
            val details = CredentialDisplayNormalizer.toDetails(
                CredentialSummary(
                    id = "cred-${case.format}-${case.label}",
                    format = case.format,
                    issuer = "Example Issuer",
                    subject = "did:key:holder",
                    label = case.label,
                    credentialDataJson = case.credentialDataJson,
                )
            )
            val claims = details.groups.flatMap { it.items }
            val card = details.toCardDisplayData()

            assertEquals(case.expectedHolderName, card.holderName, case.label)
            assertEquals(case.expectedCredentialType, card.credentialType, case.label)
            assertTrue(claims.any { it.path.id == case.expectedClaimPath }, case.label)
            assertTrue(claims.none { it.value is DisplayValue.ObjectValue }, case.label)
        }
    }

    @Test
    fun classifiesW3cSubjectClaimsByDisplayedClaimInsteadOfOuterWrapper() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "jwt_vc_json",
                issuer = "did:web:issuer.example",
                subject = "did:key:holder",
                label = "Person credential",
                credentialDataJson = """
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
                """.trimIndent(),
            )
        )

        val personal = assertNotNull(details.groups.firstOrNull { it.title == "Personal details" })
        assertEquals("Ada", (personal.items.first { it.path.id == "credentialSubject.given_name" }.value as DisplayValue.Text).value)
        assertEquals("Lovelace", (personal.items.first { it.path.id == "credentialSubject.family_name" }.value as DisplayValue.Text).value)

        val address = assertNotNull(details.groups.firstOrNull { it.title == "Address" })
        assertEquals("AT", (address.items.first { it.path.id == "credentialSubject.resident_country" }.value as DisplayValue.Text).value)

        val credentialData = assertNotNull(details.groups.firstOrNull { it.title == "Credential data" })
        assertEquals("E-123", (credentialData.items.first { it.path.id == "credentialSubject.employee_id" }.value as DisplayValue.Text).value)

        val technical = assertNotNull(details.groups.firstOrNull { it.title == "Credential metadata" })
        assertTrue(technical.items.any { it.path.id == "@context" })
        assertTrue(technical.items.any { it.path.id == "type" })
        assertTrue(technical.items.any { it.path.id == "issuer" })
        assertTrue(technical.items.any { it.path.id == "proof.type" })
    }

    @Test
    fun classifiesJwtVcEnvelopeTechnicalContainersLikeUnwrappedW3cCredentials() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "jwt_vc_json",
                issuer = "did:web:issuer.example",
                subject = "did:key:holder",
                label = "Person credential",
                credentialDataJson = """
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
                """.trimIndent(),
            )
        )

        val personal = assertNotNull(details.groups.firstOrNull { it.title == "Personal details" })
        assertEquals("Ada", (personal.items.first { it.path.id == "vc.credentialSubject.given_name" }.value as DisplayValue.Text).value)
        assertEquals("Lovelace", (personal.items.first { it.path.id == "vc.credentialSubject.family_name" }.value as DisplayValue.Text).value)

        val address = assertNotNull(details.groups.firstOrNull { it.title == "Address" })
        assertEquals("AT", (address.items.first { it.path.id == "vc.credentialSubject.resident_country" }.value as DisplayValue.Text).value)

        val credentialData = assertNotNull(details.groups.firstOrNull { it.title == "Credential data" })
        assertEquals("E-123", (credentialData.items.first { it.path.id == "vc.credentialSubject.employee_id" }.value as DisplayValue.Text).value)

        val technical = assertNotNull(details.groups.firstOrNull { it.title == "Credential metadata" })
        assertTrue(technical.items.any { it.path.id == "iss" })
        assertTrue(technical.items.any { it.path.id == "sub" })
        assertTrue(technical.items.any { it.path.id == "vc.type" })
        assertTrue(technical.items.any { it.path.id == "vc.@context" })
        assertEquals(
            DisplayValue.Text("StatusList2021Entry - https://issuer.example/status/1"),
            technical.items.first { it.path.id == "vc.credentialStatus" }.value,
        )
    }

    @Test
    fun decodesBase64TextAndJsonValues() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                label = "vc+sd-jwt",
                credentialDataJson = """
                    {
                      "plain_note": "SGVsbG8sIHdhbGxldA==",
                      "json_note": "eyJwdXJwb3NlIjoiYWdlIHByb29mIn0"
                    }
                """.trimIndent(),
            )
        )

        val claims = details.groups.flatMap { it.items }
        assertEquals(DisplayValue.DecodedText("Hello, wallet"), claims.first { it.path.id == "plain_note" }.value)
        assertEquals(DisplayValue.Text("age proof"), claims.first { it.path.id == "json_note.purpose" }.value)
    }

    @Test
    fun validatesDataUriImageBytesBeforeUsingMimeHint() {
        val encodedJson = Base64.Default.encode("""{"purpose":"age proof"}""".encodeToByteArray())
        val encodedText = Base64.Default.encode("Hello, wallet".encodeToByteArray())
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                label = "vc+sd-jwt",
                credentialDataJson = """
                    {
                      "json_note": "data:image/png;base64,$encodedJson",
                      "plain_note": "data:image/webp;base64,$encodedText",
                      "portrait": "data:image/png;base64,$onePixelPngBase64"
                    }
                """.trimIndent(),
            )
        )

        val claims = details.groups.flatMap { it.items }
        assertEquals(DisplayValue.Text("age proof"), claims.first { it.path.id == "json_note.purpose" }.value)
        assertEquals(DisplayValue.DecodedText("Hello, wallet"), claims.first { it.path.id == "plain_note" }.value)
        assertIs<DisplayValue.Image>(claims.first { it.path.id == "portrait" }.value)
    }

    @Test
    fun classifiesPortraitByteArrayDataAsImageAndUsesItForCards() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                label = "mso_mdoc",
                credentialDataJson = """{"portrait":{"elementValue":${onePixelPngByteArrayJson()}}}""",
            )
        )

        val portrait = details.groups
            .flatMap { it.items }
            .first { it.path.id == "portrait.elementValue" }
        assertEquals("Portrait", portrait.label)
        val image = assertIs<DisplayValue.Image>(portrait.value)

        assertEquals("image/png", image.mimeType)
        assertTrue(image.bytes.contentEquals(Base64.Default.decode(onePixelPngBase64)))
        assertEquals(image, details.toCardDisplayData().portrait)
    }

    @Test
    fun keepsNestedMdocPortraitLabelWhenFlatteningNamespaceObject() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                label = "mso_mdoc",
                credentialDataJson = """
                    {
                      "eu.europa.ec.eudi.pid.1": {
                        "portrait": {
                          "elementValue": ${onePixelPngByteArrayJson()}
                        }
                      }
                    }
                """.trimIndent(),
            )
        )

        val portrait = details.groups
            .flatMap { it.items }
            .first { it.path.id == "eu.europa.ec.eudi.pid.1.portrait.elementValue" }
        assertEquals("Portrait", portrait.label)
        assertIs<DisplayValue.Image>(portrait.value)
    }

    @Test
    fun presentationCredentialOptionPrependsReadableRequestedDisclosures() {
        val option = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "credential-1",
            label = "PID",
            issuer = "https://issuer.example",
            subject = "did:key:holder",
            format = "dc+sd-jwt",
            credentialDataJson = """
                {
                  "given_name": "Ada",
                  "family_name": "Lovelace"
                }
            """.trimIndent(),
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = CredentialDisplayVocabulary.disclosureLabel("given_name", """["${'$'}","given_name"]"""),
                    path = """["${'$'}","given_name"]""",
                    valueJson = "\"Ada\"",
                    displayValue = "Ada",
                    selectivelyDisclosable = true,
                    required = true,
                    selectable = false,
                ),
                WalletDemoPresentationDisclosure(
                    label = CredentialDisplayVocabulary.disclosureLabel("portrait", """["eu.europa.ec.eudi.pid.1","portrait"]"""),
                    path = """["eu.europa.ec.eudi.pid.1","portrait"]""",
                    valueJson = onePixelPngByteArrayJson(),
                    displayValue = null,
                    selectivelyDisclosable = true,
                    required = false,
                    selectable = true,
                ),
            ),
        )

        val details = option.toCredentialDetails()

        val requested = assertNotNull(details.groups.firstOrNull())
        assertEquals("Requested disclosures", requested.title)
        assertEquals(listOf("Given name", "Portrait"), requested.items.map { it.label })
        assertEquals(listOf("disclosures[0].given_name", "disclosures[1].portrait"), requested.items.map { it.path.id })
        assertEquals(DisplayValue.Text("Ada"), requested.items.first().value)
        assertIs<DisplayValue.Image>(requested.items.last().value)

        val personal = assertNotNull(details.groups.firstOrNull { it.title == "Personal details" })
        assertEquals(listOf("Given name", "Family name"), personal.items.map { it.label })
        assertEquals(option.selection.id, details.summary.id)
    }

    @Test
    fun presentationCredentialOptionOmitsRequestedDisclosuresWhenEmpty() {
        val option = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "credential-1",
            label = "PID",
            issuer = "https://issuer.example",
            subject = "did:key:holder",
            format = "dc+sd-jwt",
            credentialDataJson = """
                {
                  "given_name": "Ada"
                }
            """.trimIndent(),
            disclosures = emptyList(),
        )

        val details = option.toCredentialDetails()

        assertFalse(details.groups.any { it.title == "Requested disclosures" })
        assertEquals("Personal details", details.groups.firstOrNull()?.title)
    }

    @Test
    fun leavesMalformedCredentialJsonWithoutDisplayGroups() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "jwt_vc_json",
                issuer = null,
                label = "Broken credential",
                credentialDataJson = "{not-json",
            )
        )

        assertEquals(emptyList(), details.groups)
    }

    @Test
    fun derivesCardSummaryFromClaims() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = "Example Issuer",
                subject = "did:key:holder",
                label = "PID",
                addedAt = "2026-06-17",
                credentialDataJson = """
                    {
                      "given_name": "Ada",
                      "family_name": "Lovelace",
                      "vct": "https://credentials.example/mobile-driving-licence",
                      "exp": 1781654400
                    }
                """.trimIndent(),
            )
        ).toCardDisplayData()

        assertEquals("cred-1", details.id)
        assertEquals("PID", details.title)
        assertEquals("Mobile driving licence", details.credentialType)
        assertEquals("Ada Lovelace", details.holderName)
        assertEquals("Expires 2026-06-17", details.validity)
    }

    @Test
    fun transactionDataGroupsRenderProfileAndDetailsReadably() {
        val groups = CredentialDisplayNormalizer.transactionDataGroups(
            listOf(
                WalletDemoTransactionDataItem(
                    type = "org.waltid.transaction-data.payment-authorization",
                    displayName = "Payment Authorization",
                    credentialQueryIds = listOf("pid", "payment"),
                    supportedFields = listOf("amount", "currency", "payee", "reference"),
                    detailsJson = """
                        {
                          "amount": "42.00",
                          "currency": "EUR",
                          "payee": "ACME Corp",
                          "reference": "INV-2026-042"
                        }
                    """.trimIndent(),
                    rawJson = """
                        {
                          "type": "org.waltid.transaction-data.payment-authorization",
                          "credential_ids": ["pid", "payment"],
                          "amount": "42.00",
                          "currency": "EUR",
                          "payee": "ACME Corp",
                          "reference": "INV-2026-042"
                        }
                    """.trimIndent(),
                )
            )
        )

        val payment = groups.single()
        val labelsToValues = payment.items.associate { item ->
            item.label to when (val value = item.value) {
                is DisplayValue.Text -> value.value
                else -> item.rawValue.orEmpty()
            }
        }

        assertEquals("Payment Authorization", payment.title)
        assertEquals(
            listOf("Amount", "Currency", "Payee", "Reference"),
            payment.items.take(4).map { it.label },
        )
        assertEquals("org.waltid.transaction-data.payment-authorization", labelsToValues["Type"])
        assertEquals("pid, payment", labelsToValues["Credential queries"])
        assertEquals("42.00", labelsToValues["Amount"])
        assertEquals("EUR", labelsToValues["Currency"])
        assertEquals("ACME Corp", labelsToValues["Payee"])
        assertEquals("INV-2026-042", labelsToValues["Reference"])
    }

    private fun onePixelPngByteArrayJson(): String =
        Base64.Default.decode(onePixelPngBase64).joinToString(prefix = "[", postfix = "]") { byte ->
            (byte.toInt() and 0xFF).toString()
        }

    private companion object {
        const val onePixelPngBase64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    }
}
