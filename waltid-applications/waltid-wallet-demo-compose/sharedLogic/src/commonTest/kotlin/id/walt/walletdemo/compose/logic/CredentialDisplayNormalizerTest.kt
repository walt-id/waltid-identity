package id.walt.walletdemo.compose.logic

import kotlin.io.encoding.Base64
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialDisplayNormalizerTest {

    @Test
    fun consentPreservesNormalizedClaimsAcrossCredentialFormats() {
        for (format in listOf("dc+sd-jwt", "vc+sd-jwt", "jwt_vc", "jwt_vc_json", "jwt_vc_json-ld", "ldp_vc", "mso_mdoc")) {
            val option = WalletDemoPresentationCredentialOption(
                queryId = "identity", credentialId = "one", format = format,
                issuer = null, subject = null, label = "Identity",
                credentialDataJson = """{"iat":1793318400,"exp":1801094400,"nbf":1793318400.25}""",
                disclosures = listOf("iat" to "1793318400", "exp" to "1801094400", "nbf" to "1793318400.25").map { (name, value) ->
                    WalletDemoPresentationDisclosure(path = name, label = name, valueJson = value,
                        displayValue = value, selectivelyDisclosable = false)
                },
            )
            val details = option.toCredentialDetails()
            val expected = listOf("2026-10-30T00:00:00Z", "2027-01-28T00:00:00Z", "2026-10-30T00:00:00.25Z")
            assertEquals(expected.map { DisplayValue.InstantValue(Instant.parse(it)) }, details.groups.first().items.map { it.value }, format)
            val stored = details.groups.drop(1).flatMap { it.items }.associateBy { it.path.id }
            option.disclosures.forEachIndexed { index, disclosure ->
                assertEquals(DisplayValue.InstantValue(Instant.parse(expected[index])), stored.getValue(disclosure.path).value, format)
                assertEquals(disclosure.valueJson, details.groups.first().items[index].rawValue)
            }
        }
    }

    @Test
    fun temporalFormattingPreservesSecondsAndRejectsUnitGuessing() {
        val cases = listOf(
            "-62135596800" to "0001-01-01T00:00:00Z",
            "0" to "1970-01-01T00:00:00Z",
            "-1" to "1969-12-31T23:59:59Z",
            "-0.25" to "1969-12-31T23:59:59.75Z",
            "1.793318400125e9" to "2026-10-30T00:00:00.125Z",
            "1793318400.123456789" to "2026-10-30T00:00:00.123456789Z",
            "10000000000" to "2286-11-20T17:46:40Z",
            "253402300799" to "9999-12-31T23:59:59Z",
        )
        for ((raw, expected) in cases) {
            assertEquals(DisplayValue.InstantValue(Instant.parse(expected)), disclosureValue("iat", raw), raw)
            assertEquals(DisplayValue.Text(raw), disclosureValue("validFrom", "\"$raw\""), raw)
        }
        for (raw in listOf("1793318400000", "253402300800", "1e100", "1793318400.1234567891")) {
            assertEquals(DisplayValue.NumberValue(raw), disclosureValue("exp", raw), raw)
        }
    }

    @Test
    fun typedClaimFallbackDoesNotChangeSourceMeaning() {
        assertEquals(DisplayValue.BooleanValue(true), disclosureValue("age_over_18", "true"))
        assertEquals(DisplayValue.Text("true"), disclosureValue("custom", "\"true\""))
        assertEquals(DisplayValue.Text("false"), disclosureValue("custom", "\"false\""))
        assertEquals(DisplayValue.NumberValue("1793318400"), disclosureValue("idx", "1793318400"))
        assertEquals(DisplayValue.Text("00123"), disclosureValue("document_number", "\"00123\""))
        assertEquals(DisplayValue.NullValue, disclosureValue("custom", "null"))
        for (date in listOf("1990-01-01", "2026-10-30T23:30:00", "2026-02-30T12:00:00Z", "2026-10-30T23:30:60Z", "2026-10-30T23:30:00.1234567891Z", "not a date", "SGVsbG8gd29ybGQ=")) {
            assertEquals(DisplayValue.Text(date), disclosureValue("expiry_date", "\"$date\""))
        }
        assertEquals(DisplayValue.Text("Fallback"), disclosureValue("custom", "{"))
    }

    @Test
    fun nestedAndArrayTemporalValuesUseTheSameFormatting() {
        val value = disclosureValue("history", """[{"issued_at":1793318400,"flags":[true,"true"]}]""")
        val item = assertIs<DisplayValue.ObjectValue>(assertIs<DisplayValue.ListValue>(value).values.single())
        assertEquals(DisplayValue.InstantValue(Instant.parse("2026-10-30T00:00:00Z")), item.entries.first().value)
        assertEquals(DisplayValue.ListValue(listOf(DisplayValue.BooleanValue(true), DisplayValue.Text("true"))), item.entries.last().value)
        assertEquals(DisplayValue.ListValue(listOf(DisplayValue.InstantValue(Instant.parse("2026-10-30T00:00:00Z")))),
            disclosureValue("exp", "[1793318400]"))
    }

    private fun disclosureValue(name: String, json: String): DisplayValue =
        CredentialDisplayNormalizer.toDisclosureValue(json, "Fallback", ClaimPath.topLevel(name), "dc+sd-jwt")

    @Test
    fun claimIdentityPreservesNamespacesPunctuationAndArrayIndices() {
        val literal = ClaimItemPath.topLevel("person.name")
        val nested = ClaimItemPath.topLevel("person").child("name")
        assertFalse(literal == nested)
        assertEquals("[\"person.name\"]", literal.id)
        assertFalse(ClaimItemPath.topLevel("list").child("0") == ClaimItemPath.topLevel("list").indexedChild(0))
        assertEquals(listOf(ClaimPathExpression.Segment.Key("0"), ClaimPathExpression.Segment.Key("*")),
            ClaimPathExpression.parse("['0']['*']").segments)
        val group = ClaimGroup("personal", "Personal details", emptyList())
        assertEquals(group.id, group.copy(title = "Persönliche Angaben").id)
    }

    @Test
    fun deferredByteArrayStillValidatesValuesAfterTheImageHeader() {
        val details = CredentialDisplayNormalizer.toDetails(CredentialSummary(
            id = "bad-image", format = "mso_mdoc", issuer = null, label = "Portrait",
            credentialDataJson = """{"portrait":[137,80,78,71,13,10,26,10,0,0,0,13,999]}""",
        ))
        val deferred = assertIs<DisplayValue.DeferredImage>(details.groups.single().items.single().value)
        val fallback = assertIs<DisplayValue.ListValue>(deferred.resolve())
        assertEquals(DisplayValue.NumberValue("999"), fallback.values.last())
    }

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
        val addressRows = allHierarchyItems(address.items)
        assertIs<DisplayValue.ObjectValue>(address.items.single().value)
        assertEquals("Street address", addressRows.first { it.path.id == "resident_address.street_address" }.label)
        assertEquals(DisplayValue.Text("1 Infinite Loop"), addressRows.first { it.path.id == "resident_address.street_address" }.value)
        assertEquals("Locality", addressRows.first { it.path.id == "resident_address.locality" }.label)

        val systemInfo = assertNotNull(details.toSystemInfoGroup())
        assertEquals("About this credential", systemInfo.title)
        assertFalse(systemInfo.initiallyExpanded)
        assertEquals(DisplayValue.InstantValue(Instant.parse("2026-07-09T12:00:00Z")), systemInfo.items.first { it.path.id == "system.added" }.value)
        assertEquals(DisplayValue.Text("cred-1"), systemInfo.items.first { it.path.id == "system.id" }.value)
        assertEquals(DisplayValue.Text("vc+sd-jwt"), systemInfo.items.first { it.path.id == "system.format" }.value)
        assertEquals(DisplayValue.Text("did:web:issuer.example"), systemInfo.items.first { it.path.id == "system.issuer" }.value)
        assertEquals(DisplayValue.Text("did:key:holder"), systemInfo.items.first { it.path.id == "system.subject" }.value)
    }

    @Test
    fun appliesUnknownIssuerOnlyToCardDisplay() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                label = "Photo ID",
                credentialDataJson = "{}",
            )
        )

        assertEquals(CredentialDisplayText.Unknown, details.toCardDisplayData().issuer)
        assertFalse(details.toSystemInfoGroup()?.items.orEmpty().any { it.path.id == "system.issuer" })
    }

    @Test
    fun retainsNamespacedMdocObjectsWithTheirSourcePaths() {
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
                "[\"eu.europa.ec.eudi.pid.1\"].birth_place",
                "[\"eu.europa.ec.eudi.pid.1\"].resident_state",
            ),
            credentialData.items.map { it.path.id },
        )
        val birthplace = assertIs<DisplayValue.ObjectValue>(credentialData.items.single { it.path.id.endsWith(".birth_place") }.value)
        assertEquals(listOf("Locality", "Country"), birthplace.entries.map { it.label })
        assertEquals(listOf(DisplayValue.Text("Vienna"), DisplayValue.Text("Austria")), birthplace.entries.map { it.value })
    }

    @Test
    fun retainsMdocNamespacesExplicitNullsAndTypedAgeValues() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                label = "Photo ID",
                credentialDataJson = """
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
                """.trimIndent(),
            )
        )

        val namespaces = details.groups.flatMap { it.items }
        val person = assertIs<DisplayValue.ObjectValue>(namespaces.single { it.path.id == "[\"org.iso.23220.1\"]" }.value)
        val ages = person.entries.associate { it.label to it.value }
        assertEquals(DisplayValue.NullValue, ages["62 or older"])
        assertEquals(DisplayValue.BooleanValue(false), ages["65 or older"])
        assertEquals(DisplayValue.BooleanValue(true), ages["18 or older"])
        assertEquals(DisplayValue.Text("Unsupported value"), ages["21 or older"])
        val travel = assertIs<DisplayValue.ObjectValue>(namespaces.single { it.path.id == "[\"org.iso.23220.dtc.1\"]" }.value)
        assertEquals(3, travel.entries.size)
        assertTrue(travel.entries.all { it.value == DisplayValue.NullValue })
        assertFalse(details.groups.single { it.title == "Travel document data" }.initiallyExpanded)
    }

    @Test
    fun summarizesProvidedMdocTravelDocumentData() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                label = "Photo ID",
                credentialDataJson = """
                    {
                      "org.iso.23220.dtc.1": {
                        "dtc_dg2": [1, 2, 3, 4],
                        "dtc_dg1": "machine-readable-zone",
                        "dtc_sod": [1, 2, 3],
                        "dg_content_info": [1, 2],
                        "dtc_version": "1.0"
                      }
                    }
                """.trimIndent(),
            )
        )

        val travel = assertNotNull(details.groups.firstOrNull { it.title == "Travel document data" })
        assertFalse(travel.initiallyExpanded)
        assertEquals(
            listOf(
                "Specification version",
                "Document security object (SOD)",
                "DG1: Machine-readable zone",
                "DG2: Facial image",
                "Document content information",
            ),
            travel.items.map { it.label },
        )
        assertEquals(
            listOf(
                DisplayValue.Text("1.0"),
                DisplayValue.Text("Available, 3 bytes"),
                DisplayValue.Text("Available"),
                DisplayValue.Text("Available, 4 bytes"),
                DisplayValue.Text("Available, 2 bytes"),
            ),
            travel.items.map { it.value },
        )
    }

    @Test
    fun keepsNullClaimsForNonMdocCredentials() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "dc+sd-jwt",
                issuer = null,
                label = "PID",
                credentialDataJson = """{"middle_name": null}""",
            )
        )

        assertEquals(DisplayValue.NullValue, details.groups.single().items.single().value)
    }

    @Test
    fun doesNotApplyMdocSemanticsOutsideStandardNamespaces() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                label = "Example",
                credentialDataJson = """{"example.namespace":{"age_over_18":true,"dg1":[1,2]}}""",
            )
        )

        assertTrue(details.groups.none { it.title == "Age attestations" || it.title == "Travel document data" })
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
                      "portrait": "data:image/png;base64,$syntheticPngBase64",
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
                "place_of_birth",
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
        assertEquals("Locality", allHierarchyItems(personal.items).first { it.path.id == "place_of_birth.locality" }.label)

        val address = assertNotNull(details.groups.firstOrNull { it.title == "Address" })
        assertEquals("Resident country", address.items.first { it.path.id == "resident_country" }.label)
        assertEquals("Vienna", (address.items.first { it.path.id == "resident_state" }.value as DisplayValue.Text).value)

        val credentialData = assertNotNull(details.groups.firstOrNull { it.title == "Credential data" })
        assertEquals("Document number", credentialData.items.first { it.path.id == "document_number" }.label)

        val technical = assertNotNull(details.groups.firstOrNull { it.title == "Credential metadata" })
        assertEquals(DisplayValue.Text("2 disclosure digests"), technical.items.first { it.path.id == "_sd" }.value)
        assertEquals(DisplayValue.Text("Key-bound credential - EC - P-256"), technical.items.first { it.path.id == "cnf" }.value)
        assertEquals(DisplayValue.InstantValue(Instant.parse("2025-01-15T00:00:00Z")), technical.items.first { it.path.id == "iat" }.value)
        assertEquals(DisplayValue.InstantValue(Instant.parse("2030-01-15T09:30:00Z")), technical.items.first { it.path.id == "exp" }.value)
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
        assertEquals("Disclosure digests", metadata.items.first { it.path.id == "_sd" }.label)
        assertEquals(DisplayValue.Text("3 disclosure digests"), metadata.items.first { it.path.id == "_sd" }.value)
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
                        "portrait": "data:image/png;base64,$syntheticPngBase64"
                      }
                    }
                """.trimIndent(),
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
                expectedClaimPath = "[\"eu.europa.ec.eudi.pid.1\"].resident_state",
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
                expectedClaimPath = "[\"org.iso.18013.5.1\"].document_number",
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
            val claims = allHierarchyItems(details.groups.flatMap { it.items })

            assertEquals(details.toCardDisplayData(), details.summary.toCardDisplayData(), case.label)
            assertTrue(claims.any { it.path.id == case.expectedClaimPath }, case.label)
            if (case.format in setOf("jwt_vc", "jwt_vc_json", "jwt_vc_json-ld", "ldp_vc")) {
                assertTrue(details.groups.flatMap { it.items }.none { it.path.id in setOf("vc", "credentialSubject", "vc.credentialSubject") }, case.label)
            }
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
        assertTrue(technical.items.any { it.path.id == "[\"@context\"]" })
        assertTrue(technical.items.any { it.path.id == "type" })
        assertTrue(technical.items.any { it.path.id == "issuer" })
        assertTrue(allHierarchyItems(technical.items).any { it.path.id == "proof.type" })
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
        assertTrue(technical.items.any { it.path.id == "vc[\"@context\"]" })
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

        val claims = allHierarchyItems(details.groups.flatMap { it.items })
        assertEquals(DisplayValue.DecodedText("Hello, wallet"), claims.first { it.path.id == "plain_note" }.value)
        assertEquals(DisplayValue.Text("age proof"), claims.first { it.path.id == "json_note.purpose" }.value)
    }

    @Test
    fun usesDetectedImageTypeInsteadOfDeclaredDataUrlType() {
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
                      "portrait": "data:image/png;base64,$syntheticPngBase64"
                    }
                """.trimIndent(),
            )
        )

        val claims = allHierarchyItems(details.groups.flatMap { it.items })
        assertEquals(DisplayValue.Text("age proof"), claims.first { it.path.id == "json_note.purpose" }.value)
        assertEquals(DisplayValue.DecodedText("Hello, wallet"), claims.first { it.path.id == "plain_note" }.value)
        assertIs<DisplayValue.Image>((claims.first { it.path.id == "portrait" }.value as DisplayValue.DeferredImage).resolve())
    }

    @Test
    fun explicitNonImageSchemaAndUnsupportedPayloadsDoNotRenderAsImages() {
        val svg = Base64.Default.encode(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><circle cx="16" cy="16" r="12"/></svg>"""
                .encodeToByteArray()
        )
        val dataImage = "data:image/png;base64,$syntheticPngBase64"
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                label = "vc+sd-jwt",
                credentialDataJson = """
                    {
                      "given_name": "$dataImage",
                      "invalid_image": "data:image/png;base64,not-base64!",
                      "unsupported_image": "data:image/svg+xml;base64,$svg",
                      "non_image_data_url": "data:text/plain;base64,$syntheticPngBase64",
                      "plain_base64": "$syntheticPngBase64"
                    }
                """.trimIndent(),
            )
        )

        val claims = allHierarchyItems(details.groups.flatMap { it.items })
        listOf("given_name", "invalid_image", "unsupported_image", "non_image_data_url", "plain_base64")
            .forEach { path ->
                assertFalse(claims.first { it.path.id == path }.value is DisplayValue.Image)
                assertFalse(claims.first { it.path.id == path }.value is DisplayValue.DeferredImage)
            }
        val invalidImage = claims.first { it.path.id == "invalid_image" }
        assertEquals(DisplayValue.Text(CredentialDisplayText.ImageUnavailable), invalidImage.value)
        assertTrue(invalidImage.rawValue?.contains("not-base64!") == true)
    }

    @Test
    fun defersPortraitByteArrayUntilResolved() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                label = "mso_mdoc",
                credentialDataJson = """{"portrait":{"elementValue":${syntheticPngByteArrayJson()}}}""",
            )
        )

        val portrait = details.groups
            .flatMap { it.items }
            .first { it.path.id == "portrait.elementValue" }
        assertEquals("Portrait", portrait.label)
        val image = assertIs<DisplayValue.Image>((portrait.value as DisplayValue.DeferredImage).resolve())

        assertEquals("image/png", image.mimeType)
        assertTrue(image.bytes.contentEquals(Base64.Default.decode(syntheticPngBase64)))
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
                          "elementValue": ${syntheticPngByteArrayJson()}
                        }
                      }
                    }
                """.trimIndent(),
            )
        )

        val portrait = details.groups
            .flatMap { it.items }
            .first { it.path.id == "[\"eu.europa.ec.eudi.pid.1\"].portrait.elementValue" }
        assertEquals("Portrait", portrait.label)
        assertIs<DisplayValue.Image>((portrait.value as DisplayValue.DeferredImage).resolve())
    }

    @Test
    fun classifiesMdocSignatureUsualMarkByteArrayAsImage() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                label = "Mobile driving licence",
                credentialDataJson = """
                    {
                      "org.iso.18013.5.1": {
                        "signature_usual_mark": {
                          "elementValue": ${syntheticPngByteArrayJson()}
                        }
                      }
                    }
                """.trimIndent(),
            )
        )

        val signature = details.groups
            .flatMap { it.items }
            .first { it.path.id == "[\"org.iso.18013.5.1\"].signature_usual_mark.elementValue" }
        assertEquals("Signature or usual mark", signature.label)
        assertIs<DisplayValue.Image>((signature.value as DisplayValue.DeferredImage).resolve())
    }

    @Test
    fun classifiesStandardMdlBiometricTemplateByteArraysAsImages() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                label = "mso_mdoc",
                credentialDataJson = """
                    {
                      "org.iso.18013.5.1": {
                        "biometric_template_face": ${syntheticPngByteArrayJson()},
                        "biometric_template_finger": ${syntheticPngByteArrayJson()},
                        "biometric_template_signature_sign": ${syntheticPngByteArrayJson()},
                        "biometric_template_iris": ${syntheticPngByteArrayJson()}
                      }
                    }
                """.trimIndent(),
            )
        )

        val claimsByPath = details.groups
            .flatMap { it.items }
            .associateBy { it.path.id }
        listOf(
            "biometric_template_face",
            "biometric_template_finger",
            "biometric_template_signature_sign",
            "biometric_template_iris",
        ).forEach { elementIdentifier ->
            val claim = assertNotNull(claimsByPath["[\"org.iso.18013.5.1\"].$elementIdentifier"])
            assertIs<DisplayValue.Image>((claim.value as DisplayValue.DeferredImage).resolve())
        }
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
                    valueJson = syntheticPngByteArrayJson(),
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
        assertIs<DisplayValue.Image>((requested.items.last().value as DisplayValue.DeferredImage).resolve())

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
    fun presentationCredentialOptionSurfacesStoredCardArt() {
        val option = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "credential-1",
            label = "PID",
            issuer = "https://issuer.example",
            subject = "did:key:holder",
            format = "dc+sd-jwt",
            credentialDataJson = """{"given_name":"Ada"}""",
            disclosures = emptyList(),
            metadataJson = """
                {
                  "credentialDisplay": [
                    {
                      "name": "Personal ID",
                      "background_image": { "uri": "https://issuer.example/pid-bg.png" }
                    }
                  ]
                }
            """.trimIndent(),
        )

        val card = option.toCredentialDetails().toCardDisplayData()

        assertEquals("https://issuer.example/pid-bg.png", card.backgroundImageUri)
        assertEquals("Personal ID", card.title)
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
    fun derivesCardTitleFromCredentialType() {
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
        assertEquals("Mobile Driving Licence", details.title)
    }

    @Test
    fun transactionDataGroupsRenderProfileAndDetailsReadably() {
        val groups = CredentialDisplayNormalizer.transactionDataGroups(
            listOf(
                WalletDemoTransactionDataItem(
                    type = "org.waltid.transaction-data.payment-authorization",
                    displayName = "Payment Authorization",
                    credentialQueryIds = listOf("pid", "payment"),
                    supportedFields = listOf("merchant_name", "amount", "currency"),
                    detailsJson = """
                        {
                          "merchant_name": "ACME Corp",
                          "amount": "42.00",
                          "currency": "EUR"
                        }
                    """.trimIndent(),
                    rawJson = """
                        {
                          "type": "org.waltid.transaction-data.payment-authorization",
                          "credential_ids": ["pid", "payment"],
                          "merchant_name": "ACME Corp",
                          "amount": "42.00",
                          "currency": "EUR"
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
            listOf("Merchant name", "Amount", "Currency"),
            payment.items.take(3).map { it.label },
        )
        assertEquals("org.waltid.transaction-data.payment-authorization", labelsToValues["Type"])
        assertEquals("pid, payment", labelsToValues["Credential queries"])
        assertEquals("ACME Corp", labelsToValues["Merchant name"])
        assertEquals("42.00", labelsToValues["Amount"])
        assertEquals("EUR", labelsToValues["Currency"])
    }

    @Test
    fun transactionDataGroupsQualifyNestedScaPayloadLabelsWithTheirParent() {
        val groups = CredentialDisplayNormalizer.transactionDataGroups(
            listOf(
                WalletDemoTransactionDataItem(
                    type = "urn:eudi:sca:payment:1",
                    displayName = "SCA Payment",
                    credentialQueryIds = listOf("sca_payment_card"),
                    supportedFields = listOf("payload"),
                    detailsJson = """
                        {
                          "payload": {
                            "transaction_id": "8D8AC610-566D-4EF0-9C22-186B2A5ED793",
                            "payee": {
                              "name": "Super Store",
                              "id": "merchant-001"
                            },
                            "currency": "EUR",
                            "amount": 11.56
                          }
                        }
                    """.trimIndent(),
                    rawJson = """
                        {
                          "type": "urn:eudi:sca:payment:1",
                          "credential_ids": ["sca_payment_card"],
                          "payload": {
                            "transaction_id": "8D8AC610-566D-4EF0-9C22-186B2A5ED793",
                            "payee": {
                              "name": "Super Store",
                              "id": "merchant-001"
                            },
                            "currency": "EUR",
                            "amount": 11.56
                          }
                        }
                    """.trimIndent(),
                )
            )
        )

        val payment = groups.single()
        assertEquals("SCA Payment", payment.title)
        assertTrue(payment.items.none { it.value is DisplayValue.ObjectValue })

        val labelsByPath = payment.items.associate { it.path.id to it.label }
        // The `payload` wrapper carries no meaning of its own, so its direct leaves stay unprefixed...
        assertEquals("Amount", labelsByPath["transactionData[0].details.payload.amount"])
        assertEquals("Currency", labelsByPath["transactionData[0].details.payload.currency"])
        assertEquals("Transaction id", labelsByPath["transactionData[0].details.payload.transaction_id"])
        // ...while a bare "Name" row would not say whose name it is.
        assertEquals("Payee name", labelsByPath["transactionData[0].details.payload.payee.name"])
        assertEquals("Payee id", labelsByPath["transactionData[0].details.payload.payee.id"])

        val valuesByPath = payment.items.associate { it.path.id to it.value }
        assertEquals(DisplayValue.NumberValue("11.56"), valuesByPath["transactionData[0].details.payload.amount"])
        assertEquals(DisplayValue.Text("EUR"), valuesByPath["transactionData[0].details.payload.currency"])
        assertEquals(DisplayValue.Text("Super Store"), valuesByPath["transactionData[0].details.payload.payee.name"])
    }

    @Test
    fun credentialClaimsKeepUnqualifiedNestedLabels() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "dc+sd-jwt",
                issuer = null,
                label = "PID",
                credentialDataJson = """{"place_of_birth":{"locality":"Vienna","country":"AT"}}""",
            )
        )

        val claims = allHierarchyItems(details.groups.flatMap { it.items })
        assertEquals("Locality", claims.first { it.path.id == "place_of_birth.locality" }.label)
        assertEquals("Country", claims.first { it.path.id == "place_of_birth.country" }.label)
    }

    private fun syntheticPngByteArrayJson(): String =
        syntheticPngBytes.joinToString(prefix = "[", postfix = "]") { byte ->
            (byte.toInt() and 0xFF).toString()
        }

    private companion object {
        val syntheticPngBytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val syntheticJpegBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46,
        )
        val syntheticPngBase64 = Base64.Default.encode(syntheticPngBytes)
        val syntheticJpegBase64 = Base64.Default.encode(syntheticJpegBytes)
    }

    @Test
    fun wildcardAndIndexLabelsMatchWithoutAliasingStringKeysOrParents() {
        val metadata = StoredCredentialMetadataParser.claims("""{"credentialClaims":[
          {"path":["people",null,"name"],"display":[{"name":"Person name"}]},
          {"path":["address",0,"city"],"display":[{"name":"Primary city"}]},
          {"path":["object_address","0","city"],"display":[{"name":"Object city"}]}
        ]}""")
        for ((path, label) in listOf(
            "people[0].name" to "Person name",
            "people[2].name" to "Person name",
            "people.name" to "Fallback",
            "people[0]" to "Fallback",
            """people["0"].name""" to "Fallback",
            "address[0].city" to "Primary city",
            "address[1].city" to "Fallback",
            """object_address["0"].city""" to "Object city",
            "object_address[0].city" to "Fallback",
            """address["0"].city""" to "Fallback",
        )) {
            val item = metadataClaimItem().withClaimMetadata(metadata, "dc+sd-jwt", ClaimPathExpression.parse(path))
            assertEquals(label, item.label, path)
            assertEquals(DisplayValue.Text("Value"), item.value)
        }
    }

    @Test
    fun invalidPathsNeverLoseSegmentsAndAccidentallyMatch() {
        val metadata = StoredCredentialMetadataParser.claims("""{"credentialClaims":[{"path":["people","name"],"display":[{"name":"Wrong match"}]}]}""")
        for (path in listOf("""["people",true,"name"]""", """["people",1.5,"name"]""", """["people",1e2,"name"]""", """["people",18446744073709551615,"name"]""")) {
            assertEquals("Fallback", metadataClaimItem().withClaimMetadata(metadata, "dc+sd-jwt", ClaimPathExpression.parse(path)).label, path)
        }
    }

    @Test
    fun labelsReachNestedArrayValuesWithoutChangingCredentialData() {
        val details = CredentialDisplayNormalizer.toDetails(CredentialSummary(
            id = "typed-metadata", format = "dc+sd-jwt", issuer = "Issuer", label = "Credential",
            credentialDataJson = """{"people":[{"name":"Alice"},{"name":"Bob"}]}""",
            metadataJson = """{"credentialClaims":[{"path":["people",null,"name"],"display":[{"name":"Person name"}]}]}""",
        ))
        val people = details.groups.flatMap { it.items }.single { it.path.id == "people" }
        val values = assertIs<DisplayValue.ListValue>(people.value).values
        val rows = values.map { assertIs<DisplayValue.ObjectValue>(it).entries.single() }
        assertEquals(listOf("Person name", "Person name"), rows.map { it.label })
        assertEquals(listOf(DisplayValue.Text("Alice"), DisplayValue.Text("Bob")), rows.map { it.value })
        assertEquals(listOf("people[0].name", "people[1].name"), rows.map { it.path.id })
    }

    @Test
    fun literalKeysRemainDistinctFromPathsAndWildcards() {
        val metadata = StoredCredentialMetadataParser.claims("""{"credentialClaims":[
          {"path":[""],"display":[{"name":"Empty key"}]},
          {"path":[" "],"display":[{"name":"Space key"}]},
          {"path":["a.b"],"display":[{"name":"Dotted key"}]},
          {"path":["0"],"display":[{"name":"Zero key"}]},
          {"path":["*"],"display":[{"name":"Star key"}]}
        ]}""")
        assertEquals(5, metadata.size)
        for ((path, label) in listOf(
            """[""]""" to "Empty key", """[" "]""" to "Space key",
            """["a.b"]""" to "Dotted key", """["0"]""" to "Zero key",
            """["*"]""" to "Star key", "a.b" to "Fallback", "[0]" to "Fallback", "[*]" to "Fallback",
        )) assertEquals(label, metadataClaimItem().withClaimMetadata(metadata, "dc+sd-jwt", ClaimPathExpression.parse(path)).label, path)
    }

    private fun metadataClaimItem() = ClaimItem(path = ClaimItemPath.topLevel("claim"), label = "Fallback", value = DisplayValue.Text("Value"))
    private fun hierarchyDetails(data: String, format: String = "dc+sd-jwt", metadata: String? = null) =
        CredentialDisplayNormalizer.toDetails(CredentialSummary(
            id = "hierarchy", format = format, issuer = null, label = "Identity",
            credentialDataJson = data, metadataJson = metadata,
        ))

    @Test fun addressAndBirthplaceKeepTheirOwnChildren() {
        val items = hierarchyDetails("""{"address":{"country":"NL","locality":"Utrecht"},"place_of_birth":{"country":"DE","locality":"Berlin"}}""")
            .groups.flatMap { it.items }
        val address = assertIs<DisplayValue.ObjectValue>(items.single { it.path.id == "address" }.value)
        val birthplace = assertIs<DisplayValue.ObjectValue>(items.single { it.path.id == "place_of_birth" }.value)
        assertEquals(listOf("address.country", "address.locality"), address.entries.map { it.path.id })
        assertEquals(DisplayValue.Text("NL"), address.entries.first().value)
        assertEquals(DisplayValue.Text("DE"), birthplace.entries.first().value)
    }

    @Test fun emptyContainersAndScalarValuesRemainDistinct() {
        val items = hierarchyDetails("""{"object":{},"array":[],"zero":0,"false":false,"null":null,"empty":""}""")
            .groups.flatMap { it.items }.associateBy { it.path.id }
        assertEquals(DisplayValue.ObjectValue(emptyList()), items.getValue("object").value)
        assertEquals(DisplayValue.ListValue(emptyList()), items.getValue("array").value)
        assertEquals(DisplayValue.NumberValue("0"), items.getValue("zero").value)
        assertEquals(DisplayValue.BooleanValue(false), items.getValue("false").value)
        assertEquals(DisplayValue.NullValue, items.getValue("null").value)
        assertEquals(DisplayValue.Text(""), items.getValue("empty").value)
    }

    @Test fun metadataOrderStaysInsideItsParent() {
        val metadata = """{"credentialClaims":[
            {"path":["address","postal_code"],"display":[{"name":"Postcode"}]},
            {"path":["place_of_birth","country"],"display":[{"name":"Birth country"}]},
            {"path":["address","country"],"display":[{"name":"Residence country"}]}]}"""
        val items = hierarchyDetails(
            """{"address":{"country":"NL","postal_code":"00123"},"place_of_birth":{"country":"DE"}}""",
            metadata = metadata,
        ).groups.flatMap { it.items }
        val address = assertIs<DisplayValue.ObjectValue>(items.single { it.path.id == "address" }.value)
        assertEquals(listOf("Postcode", "Residence country"), address.entries.map { it.label })
        assertEquals(DisplayValue.Text("00123"), address.entries.first().value)
        assertEquals(setOf("address", "place_of_birth"), items.map { it.path.id }.toSet())
    }

    @Test fun objectArraysKeepOrderPathsAndDuplicateValues() {
        val details = hierarchyDetails("""{"jobs":[{"name":"A","address":{"country":"NL"}},{"name":"A"}],"matrix":[[0,false],[]]}""")
        val items = details.groups.flatMap { it.items }
        val jobs = assertIs<DisplayValue.ListValue>(items.single { it.path.id == "jobs" }.value)
        assertEquals(2, jobs.values.size)
        val first = assertIs<DisplayValue.ObjectValue>(jobs.values[0])
        val second = assertIs<DisplayValue.ObjectValue>(jobs.values[1])
        assertEquals("jobs[0].name", first.entries[0].path.id)
        assertEquals("jobs[1].name", second.entries[0].path.id)
        assertIs<DisplayValue.ObjectValue>(first.entries[1].value)
        assertEquals(DisplayValue.Text("A"), first.entries[0].value)
        assertEquals(first.entries[0].value, second.entries[0].value)
        val matrix = assertIs<DisplayValue.ListValue>(items.single { it.path.id == "matrix" }.value)
        assertEquals(DisplayValue.ListValue(listOf(DisplayValue.NumberValue("0"), DisplayValue.BooleanValue(false))), matrix.values[0])
        assertEquals(DisplayValue.ListValue(emptyList()), matrix.values[1])
    }

    @Test fun w3cEnvelopeDoesNotBecomeAnExtraVisibleParent() {
        val items = hierarchyDetails(
            """{"vc":{"type":["VerifiableCredential"],"credentialSubject":{"given_name":"Ada","address":{"country":"NL"}}}}""",
            format = "jwt_vc_json",
        ).groups.flatMap { it.items }
        assertEquals(DisplayValue.Text("Ada"), items.single { it.path.id == "vc.credentialSubject.given_name" }.value)
        assertIs<DisplayValue.ObjectValue>(items.single { it.path.id == "vc.credentialSubject.address" }.value)
    }

    @Test fun multipleMdocNamespacesRemainSeparate() {
        val items = hierarchyDetails("""{"docType":"custom","namespace.one":{"name":"A"},"namespace.two":{"name":"B"}}""", "mso_mdoc")
            .groups.flatMap { it.items }
        val namespaces = items.filter { it.value is DisplayValue.ObjectValue }
        assertEquals(2, namespaces.size)
        assertNotEquals(namespaces[0].path, namespaces[1].path)
    }

    private fun allHierarchyItems(items: List<ClaimItem>): List<ClaimItem> = items.flatMap { item ->
        listOf(item) + hierarchyValueItems(item.value)
    }

    private fun hierarchyValueItems(value: DisplayValue): List<ClaimItem> = when (value) {
        is DisplayValue.ObjectValue -> allHierarchyItems(value.entries)
        is DisplayValue.ListValue -> value.values.flatMap(::hierarchyValueItems)
        else -> emptyList()
    }

    @Test fun contradictoryArrayMetadataUsesWalletFallbackWithoutReorderingEntries() {
        val details = hierarchyDetails("""{"people":[{"name":"A"},{"name":"B"}]}""", metadata = """{"credentialClaims":[
            {"path":["people",null,"name"],"display":[{"name":"Person"}]},
            {"path":["people",0,"name"],"display":[{"name":"Primary person"}]}]}""")
        val people = assertIs<DisplayValue.ListValue>(details.groups.flatMap { it.items }.single().value)
        assertEquals(listOf("Name", "Name"), people.values.map { assertIs<DisplayValue.ObjectValue>(it).entries.single().label })
    }

    @Test fun mixedArraysAndLiteralParentsNeverChangeTheirSourceIdentity() {
        val details = hierarchyDetails("""{"a.b":{"0":{"country":"EX"}},"mix":[null,false,0,"",{},[],{"name":"A"}]}""")
        val roots = details.groups.flatMap { it.items }
        val literal = assertIs<DisplayValue.ObjectValue>(roots.single { it.path.id == "[\"a.b\"]" }.value)
        val zero = assertIs<DisplayValue.ObjectValue>(literal.entries.single().value)
        assertEquals("[\"a.b\"][\"0\"].country", zero.entries.single().path.id)
        val mix = assertIs<DisplayValue.ListValue>(roots.single { it.path.id == "mix" }.value)
        assertEquals(listOf(DisplayValue.NullValue, DisplayValue.BooleanValue(false), DisplayValue.NumberValue("0"), DisplayValue.Text(""),
            DisplayValue.ObjectValue(emptyList()), DisplayValue.ListValue(emptyList())), mix.values.take(6))
    }

    @Test fun nestedProtocolLikeNamesRemainOrdinaryClaimObjects() {
        val details = hierarchyDetails("""{"profile":{"status":{"kind":"resident"},"cnf":{"key":"value"}}}""")
        val profile = assertIs<DisplayValue.ObjectValue>(details.groups.flatMap { it.items }.single().value)
        assertEquals(listOf("profile.status", "profile.cnf"), profile.entries.map { it.path.id })
        profile.entries.forEach { assertIs<DisplayValue.ObjectValue>(it.value) }
    }

    @Test fun scalarArrayMetadataLabelsPreserveValuesAndConcreteIndexes() {
        val details = hierarchyDetails("""{"nationalities":["EX","YY"],"other_nationalities":["AA","BB"],"matrix":[[0,false],[0,false]]}""", metadata = """{"credentialClaims":[
            {"path":["other_nationalities",null],"display":[{"name":"Nationality"}]},
            {"path":["nationalities",0],"display":[{"name":"Primary nationality"}]},
            {"path":["matrix",null,0],"display":[{"name":"First cell"}]}]}""")
        val roots = details.groups.flatMap { it.items }
        val nationalities = assertIs<DisplayValue.ListValue>(roots.single { it.path.id == "nationalities" }.value)
        assertEquals(listOf(DisplayValue.Text("EX"), DisplayValue.Text("YY")), nationalities.values)
        assertEquals(mapOf(0 to "Primary nationality"), nationalities.labels)
        val others = assertIs<DisplayValue.ListValue>(roots.single { it.path.id == "other_nationalities" }.value)
        assertEquals(mapOf(0 to "Nationality", 1 to "Nationality"), others.labels)
        val matrix = assertIs<DisplayValue.ListValue>(roots.single { it.path.id == "matrix" }.value)
        assertTrue(matrix.labels.isEmpty())
        matrix.values.forEach { row ->
            val cells = assertIs<DisplayValue.ListValue>(row)
            assertEquals(listOf(DisplayValue.NumberValue("0"), DisplayValue.BooleanValue(false)), cells.values)
            assertEquals(mapOf(0 to "First cell"), cells.labels)
        }
    }

    @Test fun blankLiteralKeysKeepIdentityAndHaveVisibleFallbackLabels() {
        val roots = hierarchyDetails("""{"":"empty key"," ":"space key"}""").groups.flatMap { it.items }
        assertEquals(setOf("[\"\"]", "[\" \"]"), roots.map { it.path.id }.toSet())
        assertTrue(roots.all { it.label.isNotBlank() })
        assertEquals(setOf(DisplayValue.Text("empty key"), DisplayValue.Text("space key")), roots.map { it.value }.toSet())
    }

}
