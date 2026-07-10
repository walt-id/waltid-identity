package id.walt.walletdemo.compose.logic

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val residentAddress = address.items.first { it.path.id == "resident_address" }
        assertEquals("Resident address", residentAddress.label)
        assertIs<DisplayValue.ObjectValue>(residentAddress.value)

        val technical = assertNotNull(details.technicalGroups.firstOrNull { it.title == "Raw credential data" })
        assertTrue(technical.items.any { it.path.id == "$" })
    }

    @Test
    fun decodesBase64TextAndJsonValues() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = "vc+sd-jwt",
                addedAt = null,
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
        val decodedJson = assertIs<DisplayValue.ObjectValue>(claims.first { it.path.id == "json_note" }.value)
        assertEquals(DisplayValue.Text("age proof"), decodedJson.entries.first { it.label == "Purpose" }.value)
    }

    @Test
    fun classifiesBase64ImageDataWithoutDumpingBinary() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = "vc+sd-jwt",
                addedAt = null,
                credentialDataJson = """{"portrait":"$onePixelPngBase64"}""",
            )
        )

        val portrait = details.groups.flatMap { it.items }.first { it.path.id == "portrait" }
        val image = assertIs<DisplayValue.Image>(portrait.value)
        assertEquals("image/png", image.mimeType)
        assertTrue(image.byteCount > 0)
        assertTrue(image.encoded.length < onePixelPngBase64.length + 10)
        assertTrue(image.bytes.contentEquals(Base64.Default.decode(onePixelPngBase64)))
    }

    @Test
    fun normalizesParameterizedDataUriImageMimeType() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = "vc+sd-jwt",
                addedAt = null,
                credentialDataJson = """{"portrait":"DATA:image/PNG;charset=utf-8;base64,  $onePixelPngBase64  "}""",
            )
        )

        val portrait = details.groups.flatMap { it.items }.first { it.path.id == "portrait" }
        val image = assertIs<DisplayValue.Image>(portrait.value)

        assertEquals("image/png", image.mimeType)
        assertTrue(image.bytes.contentEquals(Base64.Default.decode(onePixelPngBase64)))
    }

    @Test
    fun classifiesPortraitByteArrayDataAsImage() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                subject = null,
                label = "mso_mdoc",
                addedAt = null,
                credentialDataJson = """{"portrait":${onePixelPngByteArrayJson()}}""",
            )
        )

        val portrait = details.groups.flatMap { it.items }.first { it.path.id == "portrait" }
        val image = assertIs<DisplayValue.Image>(portrait.value)
        assertEquals("image/png", image.mimeType)
        assertTrue(image.bytes.contentEquals(Base64.Default.decode(onePixelPngBase64)))
    }

    @Test
    fun classifiesNestedPortraitByteArrayDataAsImage() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                subject = null,
                label = "mso_mdoc",
                addedAt = null,
                credentialDataJson = """{"portrait":{"elementValue":${onePixelPngByteArrayJson()}}}""",
            )
        )

        val portrait = details.groups
            .flatMap { it.items }
            .first { it.path.id == "portrait" }
        val portraitObject = assertIs<DisplayValue.ObjectValue>(portrait.value)
        val image = assertIs<DisplayValue.Image>(portraitObject.entries.first { it.path.id == "portrait.elementValue" }.value)

        assertEquals("image/png", image.mimeType)
        assertTrue(image.bytes.contentEquals(Base64.Default.decode(onePixelPngBase64)))
        assertEquals(image, details.toCardDisplayData().portrait)
    }

    @Test
    fun classifiesPresentationDisclosureByteArrayDataAsImage() {
        val details = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "cred-1",
            label = "PID",
            issuer = "Example Issuer",
            format = "mso_mdoc",
            credentialDataJson = null,
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = "Portrait",
                    path = "$.portrait",
                    valueJson = onePixelPngByteArrayJson(),
                    displayValue = null,
                    selectivelyDisclosable = true,
                )
            ),
        ).toCredentialDetails()

        val disclosure = details.groups
            .first { it.title == CredentialDisplayVocabulary.RequestedDisclosuresTitle }
            .items
            .single()
        val image = assertIs<DisplayValue.Image>(disclosure.value)

        assertEquals("disclosures[0].portrait", disclosure.path.id)
        assertEquals("$.portrait", disclosure.path.sourcePath)
        assertEquals("image/png", image.mimeType)
        assertTrue(image.bytes.contentEquals(Base64.Default.decode(onePixelPngBase64)))
        assertEquals(onePixelPngByteArrayJson(), disclosure.rawValue)
    }

    @Test
    fun classifiesDisclosureSemanticsFromSdkPathWhenNameIsMissing() {
        val disclosurePath = "$.portrait"
        val details = WalletDemoPresentationCredentialOption(
            queryId = "pid",
            credentialId = "cred-1",
            label = "PID",
            issuer = "Example Issuer",
            format = "mso_mdoc",
            credentialDataJson = null,
            disclosures = listOf(
                WalletDemoPresentationDisclosure(
                    label = CredentialDisplayVocabulary.disclosureLabel(name = null, path = disclosurePath),
                    path = disclosurePath,
                    valueJson = onePixelPngByteArrayJson(),
                    displayValue = null,
                    selectivelyDisclosable = true,
                )
            ),
        ).toCredentialDetails()

        val disclosure = details.groups
            .first { it.title == CredentialDisplayVocabulary.RequestedDisclosuresTitle }
            .items
            .single()

        assertEquals("Portrait", disclosure.label)
        assertIs<DisplayValue.Image>(disclosure.value)
    }

    @Test
    fun buildsReadableDisclosureLabelsFromSdkPath() {
        assertEquals("Portrait", CredentialDisplayVocabulary.disclosureLabel(name = null, path = "$.portrait"))
        assertEquals("Given name", CredentialDisplayVocabulary.disclosureLabel(name = null, path = "$.given_name"))
        assertEquals("Family name", CredentialDisplayVocabulary.disclosureLabel(name = null, path = "$.credentialSubject['family.name']"))
        assertEquals("Resident address", CredentialDisplayVocabulary.disclosureLabel(name = null, path = "$['credentialSubject']['resident.address']"))
        assertEquals("Visible name", CredentialDisplayVocabulary.disclosureLabel(name = "Visible name", path = "$.given_name"))
        assertEquals("Given name", CredentialDisplayVocabulary.disclosureLabel(name = "   ", path = "$.given_name"))
    }

    @Test
    fun classifiesHumanLabelledMdocClaimsForDetailsAndCards() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "mso_mdoc",
                issuer = null,
                subject = null,
                label = "mso_mdoc",
                addedAt = null,
                credentialDataJson = """
                    {
                      "Given name": "Ada",
                      "Family name": "Lovelace",
                      "Date of birth": "1815-12-10",
                      "Place of birth": "London",
                      "Valid to": 1781654400,
                      "Portrait": {
                        "elementValue": ${onePixelPngByteArrayJson()}
                      }
                    }
                """.trimIndent(),
            )
        )

        val personal = assertNotNull(details.groups.firstOrNull { it.title == "Personal details" })
        assertEquals(DisplayValue.Text("1815-12-10"), personal.items.first { it.path.id == "Date of birth" }.value)
        assertEquals(DisplayValue.Text("London"), personal.items.first { it.path.id == "Place of birth" }.value)
        val portrait = personal.items.first { it.path.id == "Portrait" }
        val portraitObject = assertIs<DisplayValue.ObjectValue>(portrait.value)
        val image = assertIs<DisplayValue.Image>(portraitObject.entries.first { it.path.id == "Portrait.elementValue" }.value)
        val card = details.toCardDisplayData()

        assertEquals("Ada Lovelace", card.holderName)
        assertEquals("2026-06-17", card.date)
        assertEquals("Expires 2026-06-17", card.validity)
        assertEquals(image, card.portrait)
    }

    @Test
    fun formatsNumericTemporalClaimsAsReadableDatesForDetailsAndCards() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = "PID",
                addedAt = null,
                credentialDataJson = """
                    {
                      "given_name": "Ada",
                      "family_name": "Lovelace",
                      "exp": 1781654400,
                      "nbf": 1781568000000
                    }
                """.trimIndent(),
            )
        )

        val claims = details.groups.flatMap { it.items }

        assertEquals(DisplayValue.Text("2026-06-17"), claims.first { it.path.id == "exp" }.value)
        assertEquals(DisplayValue.Text("2026-06-16"), claims.first { it.path.id == "nbf" }.value)
        assertEquals("2026-06-17", details.toCardDisplayData().date)
        assertEquals("Expires 2026-06-17", details.toCardDisplayData().validity)
    }

    @Test
    fun ignoresNonTemporalClaimsThatContainExpWhenBuildingCardDate() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = "PID",
                addedAt = null,
                credentialDataJson = """
                    {
                      "expected_value": "not an expiry date",
                      "exp": 1781654400
                    }
                """.trimIndent(),
            )
        )

        assertEquals("2026-06-17", details.toCardDisplayData().date)
    }

    @Test
    fun doesNotInferClaimRolesFromPunctuationInsideClaimNames() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = "PID",
                addedAt = null,
                credentialDataJson = """
                    {
                      "metadata.exp": 1781654400
                    }
                """.trimIndent(),
            )
        )

        val metadataExpiry = details.groups.flatMap { it.items }.first { it.path.id == "metadata.exp" }

        assertEquals(DisplayValue.NumberValue("1781654400"), metadataExpiry.value)
        assertEquals(null, details.toCardDisplayData().date)
    }

    @Test
    fun labelsAddedAtFallbackWhenCardHasNoExpiryClaim() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = "PID",
                addedAt = "2026-07-09",
                credentialDataJson = """{"given_name":"Ada"}""",
            )
        )

        assertEquals("2026-07-09", details.toCardDisplayData().date)
        assertEquals("Added 2026-07-09", details.toCardDisplayData().validity)
    }

    @Test
    fun buildsCardCredentialTypeFromReadableCredentialTypeClaim() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = "PID",
                addedAt = null,
                credentialDataJson = """
                    {
                      "vct": "https://issuer.example/credential-types/mobile-driving-licence",
                      "given_name": "Ada"
                    }
                """.trimIndent(),
            )
        )

        assertEquals("Mobile driving licence", details.toCardDisplayData().credentialType)
    }

    @Test
    fun buildsCardCredentialTypeFromUrlClaimWithoutQueryOrFragmentNoise() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = "PID",
                addedAt = null,
                credentialDataJson = """
                    {
                      "vct": "https://issuer.example/credential-types/mobile-driving-licence?version=1#current",
                      "given_name": "Ada"
                    }
                """.trimIndent(),
            )
        )

        assertEquals("Mobile driving licence", details.toCardDisplayData().credentialType)
    }

    @Test
    fun buildsCardCredentialTypeFromVcTypeArray() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "jwt_vc_json",
                issuer = null,
                subject = null,
                label = "Example Credential",
                addedAt = null,
                credentialDataJson = """
                    {
                      "type": ["VerifiableCredential", "UniversityDegreeCredential"],
                      "given_name": "Ada"
                    }
                """.trimIndent(),
            )
        )

        assertEquals("University degree credential", details.toCardDisplayData().credentialType)
    }

    @Test
    fun buildsCardCredentialTypeFromUrnTypeArray() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "jwt_vc_json",
                issuer = null,
                subject = null,
                label = "Example Credential",
                addedAt = null,
                credentialDataJson = """
                    {
                      "type": ["VerifiableCredential", "urn:example:ExampleCredential"],
                      "given_name": "Ada"
                    }
                """.trimIndent(),
            )
        )

        assertEquals("Example credential", details.toCardDisplayData().credentialType)
    }

    @Test
    fun ignoresNestedTypeClaimsWhenBuildingCardCredentialType() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "jwt_vc_json",
                issuer = null,
                subject = null,
                label = "Example Credential",
                addedAt = null,
                credentialDataJson = """
                    {
                      "document": {
                        "type": "metadata"
                      },
                      "given_name": "Ada"
                    }
                """.trimIndent(),
            )
        )

        assertEquals(null, details.toCardDisplayData().credentialType)
    }

    @Test
    fun keepsMalformedJsonAsReadableRawFallback() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = null,
                subject = null,
                label = "vc+sd-jwt",
                addedAt = null,
                credentialDataJson = "{not-json",
            )
        )

        assertTrue(details.groups.isEmpty())
        val technical = assertNotNull(details.technicalGroups.firstOrNull { it.title == "Raw credential data" })
        assertEquals(DisplayValue.Raw("{not-json"), technical.items.single().value)
    }

    @Test
    fun buildsHumanReadableVerifierNamesWithoutDumpingRawIdentifiers() {
        assertEquals(
            "Example Verifier",
            VerifierDetails(
                name = "Example Verifier",
                clientId = "decentralized_identifier:did:jwk:abc",
            ).displayName,
        )
        assertEquals(
            "DID verifier",
            VerifierDetails(
                name = null,
                clientId = "decentralized_identifier:did:jwk:abc",
            ).displayName,
        )
        assertEquals(
            "DID verifier",
            VerifierDetails(
                name = null,
                clientId = "did:jwk:legacy-abc",
            ).displayName,
        )
        assertEquals(
            "verifier.example",
            VerifierDetails(
                name = null,
                clientId = "https://verifier.example:8443/client?x=1#fragment",
            ).displayName,
        )
        assertEquals(
            "verifier.example",
            VerifierDetails(
                name = null,
                clientId = "HTTPS://verifier.example/client",
            ).displayName,
        )
        assertEquals(
            "verifier.example",
            VerifierDetails(
                name = null,
                clientId = "redirect_uri:https://verifier.example/callback",
            ).displayName,
        )
        assertEquals(
            "verifier.example",
            VerifierDetails(
                name = null,
                clientId = "x509_san_dns:verifier.example",
            ).displayName,
        )
    }

    private companion object {
        const val onePixelPngBase64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="

        fun onePixelPngByteArrayJson(): String =
            Base64.Default.decode(onePixelPngBase64).joinToString(prefix = "[", postfix = "]") { it.toString() }
    }
}
