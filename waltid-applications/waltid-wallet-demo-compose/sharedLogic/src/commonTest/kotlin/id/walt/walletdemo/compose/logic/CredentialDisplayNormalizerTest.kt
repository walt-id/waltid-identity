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
        assertEquals("Street address", address.items.first { it.path.id == "resident_address.street_address" }.label)
        assertEquals(DisplayValue.Text("1 Infinite Loop"), address.items.first { it.path.id == "resident_address.street_address" }.value)
        assertEquals("Locality", address.items.first { it.path.id == "resident_address.locality" }.label)

        val systemInfo = assertNotNull(details.toSystemInfoGroup())
        assertEquals("System info", systemInfo.title)
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
                "eu.europa.ec.eudi.pid.1.resident_state",
                "eu.europa.ec.eudi.pid.1.birth_place.locality",
                "eu.europa.ec.eudi.pid.1.birth_place.country",
            ),
            credentialData.items.map { it.path.id },
        )
        assertTrue(credentialData.items.none { it.value is DisplayValue.ObjectValue })
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
        val decodedJson = assertIs<DisplayValue.ObjectValue>(claims.first { it.path.id == "json_note" }.value)
        assertEquals(DisplayValue.Text("age proof"), decodedJson.entries.first { it.label == "Purpose" }.value)
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

    private fun onePixelPngByteArrayJson(): String =
        Base64.Default.decode(onePixelPngBase64).joinToString(prefix = "[", postfix = "]") { byte ->
            (byte.toInt() and 0xFF).toString()
        }

    private companion object {
        const val onePixelPngBase64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    }
}
