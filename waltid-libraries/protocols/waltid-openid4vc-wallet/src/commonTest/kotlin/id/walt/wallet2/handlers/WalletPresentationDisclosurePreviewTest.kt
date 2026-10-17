package id.walt.wallet2.handlers

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.SdJwtCredential
import id.walt.dcql.*
import id.walt.dcql.models.*
import id.walt.dcql.models.meta.NoMeta
import kotlinx.serialization.json.*
import kotlin.test.*

class WalletPresentationDisclosurePreviewTest {
    private fun preview(format: String, signedPayload: JsonObject, fullPayload: JsonObject = signedPayload,
        requested: Map<String, Any> = mapOf("given_name" to DcqlDisclosure("given_name", JsonPrimitive("Ada")))): List<PresentationDisclosure> {
        val original = if (format == "dc+sd-jwt") SdJwtCredential(
            dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
            credentialData = fullPayload, originalCredentialData = signedPayload, signature = null, signed = null,
        ) else null
        val match = DcqlMatcher.DcqlMatchResult(
            RawDcqlCredential("one", format, fullPayload,
                disclosures = if (original != null) listOf(DcqlDisclosure("given_name", JsonPrimitive("Ada"))) else null,
                originalCredential = original),
            selectedDisclosures = requested.mapKeys { (path, _) -> JsonPrimitive(path).toString() },
            originalQuery = CredentialQuery(id = "pid", format = CredentialFormat.entries.first { format in it.id },
                meta = NoMeta, claims = requested.keys.map { ClaimsQuery(pathStrings = listOf(it)) }),
        )
        return WalletPresentationHandler.run { match.toPresentationDisclosures() }
    }

    @Test fun previewIncludesClearTextDataButNotUnrequestedSelectiveValues() {
        val signed = Json.parseToJsonElement("""{"family_name":"Lovelace","_sd":["name-digest","birth-digest"],"iss":"Issuer"}""").jsonObject
        val full = Json.parseToJsonElement("""{"given_name":"Ada","family_name":"Lovelace","birth_date":"1815-12-10","iss":"Issuer"}""").jsonObject
        val fields = preview("dc+sd-jwt", signed, full)
        assertEquals(listOf("given_name", "family_name", "iss").map { JsonPrimitive(it).toString() }, fields.map { it.path })
        assertTrue(fields.first().required)
        assertTrue(fields.first().requested)
        assertFalse(fields[1].selectivelyDisclosable)
        assertFalse(fields[1].selectable)
        assertFalse(fields[1].required)
        assertFalse(fields[1].requested)
        assertEquals(JsonPrimitive("Lovelace"), fields[1].value)
    }

    @Test fun mdocPreviewNeverAddsUnrequestedDocumentElements() {
        val data = Json.parseToJsonElement("""{"given_name":"Ada","family_name":"Lovelace"}""").jsonObject
        assertEquals(listOf(JsonPrimitive("given_name").toString()), preview("mso_mdoc", data).map { it.path })
    }

    @Test fun nonSelectiveCredentialIncludesAllOtherValuesAndDoesNotDuplicateRequestedParents() {
        val data = Json.parseToJsonElement("""{"given_name":"Ada","address":{"city":"London","postcode":"N1"}}""").jsonObject
        val fields = preview("jwt_vc_json", data, requested = mapOf("address" to data.getValue("address")))
        assertEquals(listOf("address", "given_name").map { JsonPrimitive(it).toString() }, fields.map { it.path })
    }

    @Test fun concealedArrayEntriesAndDigestFieldsAreNotPresentedAsSharedValues() {
        val data = Json.parseToJsonElement("""{"given_name":"Ada","nationalities":["GB",{"...":"digest"}],"_sd":["digest"]}""").jsonObject
        val fields = preview("dc+sd-jwt", data)
        assertEquals(JsonArray(listOf(JsonPrimitive("GB"))), fields.single { it.name == "nationalities" }.value)
        assertFalse(fields.any { it.path.contains("_sd") })
    }

    @Test fun additionalClaimPathsPreserveLiteralPunctuation() {
        val data = Json.parseToJsonElement("""{"given_name":"Ada","family.name":"Lovelace","address":{"city.name":"Vienna"}}""").jsonObject
        val fields = preview("jwt_vc_json", data)
        assertEquals(JsonPrimitive("family.name").toString(), fields.single { it.name == "family.name" }.path)
        assertEquals(listOf("address", "city.name").joinToString(".") { JsonPrimitive(it).toString() },
            fields.single { it.name == "city.name" }.path)
    }
}
