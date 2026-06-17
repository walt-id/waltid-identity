package id.walt.issuer2.testsupport

import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.issuer2.domain.IssuanceSession
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.sdjwt.SDJwt
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

fun assertBearerAccessToken(tokenResponse: TokenRequestBuilder.TokenResponse) {
    assertTrue(tokenResponse.token_type.equals("bearer", ignoreCase = true))
    assertTrue(tokenResponse.access_token.isNotBlank())
}

suspend fun assertSessionStatus(
    client: HttpClient,
    sessionId: String,
    expectedStatus: String,
) {
    val response = client.get("/issuer2/sessions/$sessionId")
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

    // ACTIVE is the default enum value and is omitted from JSON when encodeDefaults=false.
    // Decode the session so Kotlin serialization applies the same default the service uses.
    val session = response.body<IssuanceSession>()
    assertEquals(expectedStatus, session.status.name)
}

fun assertJwtVcJsonCredentialPayload(credentialPayload: JsonObject): String {
    val issuedCredential = issuedCredentialString(credentialPayload)
    assertTrue(issuedCredential.split(".").size >= 3)
    assertJwtVcJsonMappingFunctionsApplied(issuedCredential)
    return issuedCredential
}

fun assertSdJwtVcCredentialPayload(
    credentialPayload: JsonObject,
    expectedVctSuffix: String,
    expectedDisclosureKeys: Set<String>,
    expectedClaims: Map<String, String> = emptyMap(),
): String {
    val issuedCredential = issuedCredentialString(credentialPayload)
    assertTrue(issuedCredential.endsWith("~"), "Expected SD-JWT VC to end with the disclosure separator")

    val sdJwt = SDJwt.parse(issuedCredential)
    assertEquals("dc+sd-jwt", sdJwt.type)
    assertSdJwtVcDisclosures(sdJwt, expectedDisclosureKeys)
    assertTrue(
        assertStringClaim(sdJwt.fullPayload["vct"], "sd-jwt.vct").endsWith(expectedVctSuffix),
        "Expected SD-JWT VC vct to end with $expectedVctSuffix",
    )
    expectedClaims.forEach { (claimName, expectedValue) ->
        assertEquals(expectedValue, assertStringClaim(sdJwt.fullPayload[claimName], "sd-jwt.$claimName"))
    }
    return issuedCredential
}

private fun assertSdJwtVcDisclosures(
    sdJwt: SDJwt,
    expectedDisclosureKeys: Set<String>,
) {
    val disclosures = sdJwt.disclosureObjects

    assertTrue(disclosures.isNotEmpty(), "Expected SD-JWT VC to include at least one parsed disclosure")
    assertEquals(disclosures.size, sdJwt.disclosures.size)
    val objectDisclosures = disclosures.filterIsInstance<id.walt.sdjwt.ObjectPropertyDisclosure>()
    assertEquals(expectedDisclosureKeys, objectDisclosures.map { it.key }.toSet())
    assertTrue(sdJwt.sdPayload.verifyDisclosures(), "Expected SD-JWT VC disclosures to match the payload digests")
    disclosures.forEachIndexed { index, disclosure ->
        assertTrue(disclosure.salt.isNotBlank(), "Expected SD-JWT VC disclosure #$index to have a salt")
    }
    objectDisclosures.forEachIndexed { index, disclosure ->
        assertTrue(disclosure.key.isNotBlank(), "Expected SD-JWT VC object-property disclosure #$index to have a claim key")
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun assertMdocCredentialPayload(
    credentialPayload: JsonObject,
    expectedDocType: String,
    expectedNamespace: String,
    expectedElementIdentifiers: Set<String>,
    expectedClaims: Map<String, String> = emptyMap(),
): String {
    val issuedCredential = issuedCredentialString(credentialPayload)
    assertFalse(issuedCredential.contains("."), "Expected mDOC credential to be CBOR/base64url, not a JWT")
    assertFalse(issuedCredential.contains("~"), "Expected mDOC credential to be CBOR/base64url, not an SD-JWT")
    val issuedCredentialBytes = issuedCredential.decodeFromBase64Url()
    assertTrue(issuedCredentialBytes.isNotEmpty(), "Expected mDOC credential to decode to CBOR bytes")

    val issuerSigned = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(issuedCredentialBytes)

    val mobileSecurityObject = issuerSigned.decodeMobileSecurityObject()
    assertEquals(expectedDocType, mobileSecurityObject.docType)
    assertTrue(
        mobileSecurityObject.valueDigests.containsKey(expectedNamespace),
        "Expected mDOC MSO value digests for namespace $expectedNamespace",
    )

    val issuerSignedList = assertNotNull(
        issuerSigned.namespaces?.get(expectedNamespace),
        "Expected mDOC namespace $expectedNamespace",
    )
    val actualElementIdentifiers = issuerSignedList.entries.map { it.value.elementIdentifier }.toSet()
    assertTrue(
        actualElementIdentifiers.containsAll(expectedElementIdentifiers),
        "Expected mDOC namespace $expectedNamespace to contain $expectedElementIdentifiers, got $actualElementIdentifiers",
    )

    val namespaceJson = assertNotNull(
        issuerSigned.namespacesToJson()[expectedNamespace]?.jsonObject,
        "Expected mDOC namespace $expectedNamespace to be convertible to JSON",
    )
    expectedClaims.forEach { (claimName, expectedValue) ->
        assertEquals(expectedValue, namespaceJson[claimName]?.jsonPrimitive?.contentOrNull)
    }

    return issuedCredential
}

fun assertIsoMdlCredentialPayload(
    credentialPayload: JsonObject,
    expectedClaims: Map<String, String> = mapOf(
        "family_name" to "Musterfrau",
        "given_name" to "Anna Maria",
        "document_number" to "DL-AT-2025-00018427",
    ),
): String =
    assertMdocCredentialPayload(
        credentialPayload = credentialPayload,
        expectedDocType = "org.iso.18013.5.1.mDL",
        expectedNamespace = "org.iso.18013.5.1",
        expectedElementIdentifiers = setOf(
            "family_name",
            "given_name",
            "birth_date",
            "issue_date",
            "expiry_date",
            "issuing_country",
            "document_number",
            "portrait",
            "driving_privileges",
        ),
        expectedClaims = expectedClaims,
    )

private fun issuedCredentialString(credentialPayload: JsonObject): String =
    assertNotNull(
        credentialPayload["credentials"]
            ?.jsonArray
            ?.single()
            ?.jsonObject
            ?.get("credential")
            ?.jsonPrimitive
            ?.content
    )

private fun assertJwtVcJsonMappingFunctionsApplied(issuedCredential: String) {
    val jwtPayload = issuedCredential.decodeJws().payload
    val vc = jwtPayload["vc"]?.jsonObject ?: jwtPayload
    val credentialSubject = assertNotNull(vc["credentialSubject"]?.jsonObject)

    // UniversityDegree is the default W3C wallet-flow profile. Its profile mapping uses
    // <uuid>, <issuerDid>, <subjectDid>, and <timestamp>; the issued JWT must contain
    // resolved values, not the config placeholders.
    val vcText = vc.toString()
    assertFalse(vcText.contains("THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION"))
    assertFalse(vcText.contains("<uuid>"))
    assertFalse(vcText.contains("<issuerDid>"))
    assertFalse(vcText.contains("<subjectDid>"))
    assertFalse(vcText.contains("<timestamp>"))

    assertTrue(
        assertStringClaim(vc["id"], "vc.id").startsWith("urn:uuid:"),
        "Expected vc.id to be generated by <uuid>",
    )
    assertTrue(
        assertStringClaim(vc["issuer"]?.jsonObject?.get("id"), "vc.issuer.id").startsWith("did:jwk:"),
        "Expected vc.issuer.id to be resolved from <issuerDid>",
    )
    assertTrue(
        assertStringClaim(credentialSubject["id"], "vc.credentialSubject.id").startsWith("did:jwk:"),
        "Expected vc.credentialSubject.id to be resolved from <subjectDid>",
    )
    Instant.parse(assertStringClaim(vc["issuanceDate"], "vc.issuanceDate"))
}

private fun assertStringClaim(value: JsonElement?, claimName: String): String =
    assertNotNull(value?.jsonPrimitive?.contentOrNull, "Expected $claimName")