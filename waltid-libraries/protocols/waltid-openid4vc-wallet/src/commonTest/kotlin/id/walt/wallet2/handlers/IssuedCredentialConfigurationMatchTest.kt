package id.walt.wallet2.handlers

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDefinition
import id.walt.wallet2.data.WalletKeyStoreEntry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuedCredentialConfigurationMatchTest {
    @Test
    fun jwtSecuredW3cFormatsMatchEachOtherAndStayDistinctFromOtherEncodings() {
        val jwtFormats = listOf("jwt_vc_json", "jwt_vc_json-ld", "jwt_vc")
        val otherFormats = listOf("ldp_vc", "dc+sd-jwt", "mso_mdoc", "vc+cose", "unsigned")
        for (issued in jwtFormats) for (requested in jwtFormats) {
            assertTrue(issuedCredentialFormatMatches(issued, requested), "$issued -> $requested")
        }
        for (issued in jwtFormats) for (other in otherFormats) {
            assertFalse(issuedCredentialFormatMatches(issued, other), "$issued -> $other")
            assertFalse(issuedCredentialFormatMatches(other, issued), "$other -> $issued")
        }
        for (format in otherFormats) assertTrue(issuedCredentialFormatMatches(format, format), format)
        assertFalse(issuedCredentialFormatMatches("ldp_vc", "dc+sd-jwt"))
        assertFalse(issuedCredentialFormatMatches("dc+sd-jwt", "mso_mdoc"))
        assertFalse(issuedCredentialFormatMatches("mso_mdoc", "ldp_vc"))
    }

    @Test
    fun parsedJwtW3cCredentialsMatchJwtVcJsonLdAndJwtVcConfigurations() = runTest {
        val fixture = batchTestFixture(true)
        val holderJwk = fixture.keys.first().publicJwk()
        val expectedTypes = listOf("VerifiableCredential", "UniversityDegree")
        for (credential in listOf(
            w3cJwtWithVcClaim(holderJwk, expectedTypes + "ExtraType"),
            w3cJwtEnveloped(holderJwk, expectedTypes),
        )) {
            for (format in listOf(CredentialFormat.JWT_VC_JSON, CredentialFormat.JWT_VC_JSON_LD, CredentialFormat.JWT_VC)) {
                val stored = fixture.prepare(credential, w3cConfiguration(format, expectedTypes))
                assertEquals("jwt_vc_json", stored.credential.format)
                assertContains(
                    stored.credential.credentialData["type"]!!.jsonArray.map { it.jsonPrimitive.content },
                    "UniversityDegree",
                )
            }
        }
    }

    @Test
    fun parsedCredentialsRejectCrossFormatAndKindMismatches() = runTest {
        val fixture = batchTestFixture(true)
        val holderJwk = fixture.keys.first().publicJwk()
        val jwt = w3cJwtWithVcClaim(holderJwk)
        val ldp = ldpVc(holderJwk)
        val sdJwt = batchTestCredential(holderJwk, "identity")
        val degree = listOf("VerifiableCredential", "UniversityDegree")

        for (format in listOf(CredentialFormat.JWT_VC_JSON, CredentialFormat.JWT_VC_JSON_LD, CredentialFormat.JWT_VC)) {
            assertFormatMismatch(fixture, ldp, w3cConfiguration(format, degree))
            assertFormatMismatch(fixture, sdJwt, w3cConfiguration(format, degree))
            assertFormatMismatch(fixture, jwt, CredentialConfiguration(format = CredentialFormat.MSO_MDOC, doctype = "org.iso.18013.5.1.mDL"))
            assertMessage(
                fixture,
                w3cJwtWithVcClaim(holderJwk, listOf("VerifiableCredential")),
                w3cConfiguration(format, degree),
                "types do not match",
            )
        }

        val storedLdp = fixture.prepare(ldp, w3cConfiguration(CredentialFormat.LDP_VC, degree))
        assertEquals("ldp_vc", storedLdp.credential.format)
        assertFormatMismatch(fixture, jwt, w3cConfiguration(CredentialFormat.LDP_VC, degree))
        assertFormatMismatch(fixture, sdJwt, w3cConfiguration(CredentialFormat.LDP_VC, degree))
        assertMessage(
            fixture,
            ldpVc(holderJwk, listOf("VerifiableCredential")),
            w3cConfiguration(CredentialFormat.LDP_VC, degree),
            "types do not match",
        )

        val storedSdJwt = fixture.prepare(sdJwt, CredentialConfiguration(format = CredentialFormat.SD_JWT_VC, vct = "identity"))
        assertEquals("dc+sd-jwt", storedSdJwt.credential.format)
        assertFormatMismatch(fixture, jwt, CredentialConfiguration(format = CredentialFormat.SD_JWT_VC, vct = "identity"))
        assertFormatMismatch(fixture, ldp, CredentialConfiguration(format = CredentialFormat.SD_JWT_VC, vct = "identity"))
        assertMessage(
            fixture,
            batchTestCredential(holderJwk, "unrequested-type"),
            CredentialConfiguration(format = CredentialFormat.SD_JWT_VC, vct = "identity"),
            "vct does not match",
        )
    }

    @Test
    fun receiveStoresJwtVcJsonLdAndRejectsADifferentEncoding() = runTest {
        for (format in listOf("jwt_vc_json", "jwt_vc_json-ld", "jwt_vc")) {
            val fixture = batchTestFixture(true)
            val result = WalletIssuanceHandler.receiveCredentials(
                fixture.wallet,
                ReceiveCredentialRequest(offerJson = batchTestOffer(), credentials = listOf(fixture.selection(1))),
                httpClient = batchTestClient(metadata = w3cMetadata(format), credential = { request ->
                    val jwk = CompactJws.decodeUnverified(request.batchProofs().single()).protectedHeader.getValue("jwk").jsonObject
                    credentialResponse(w3cJwtWithVcClaim(jwk))
                }),
            )
            assertNull(result.failure)
            assertEquals(1, result.credentialIds.size)
            assertEquals("jwt_vc_json", fixture.store.listCredentials().toList().single().credential.format)
        }

        val fixture = batchTestFixture(true)
        val rejected = WalletIssuanceHandler.receiveCredentials(
            fixture.wallet,
            ReceiveCredentialRequest(offerJson = batchTestOffer(), credentials = listOf(fixture.selection(1))),
            httpClient = batchTestClient(metadata = w3cMetadata("jwt_vc_json-ld"), credential = { request ->
                credentialResponse(batchTestCredential(request.batchProofs().map { proof ->
                    CompactJws.decodeUnverified(proof).protectedHeader.getValue("jwk").jsonObject
                }.single()))
            }),
        )
        assertEquals(CredentialIssuanceStage.RESPONSE, rejected.failure?.stage)
        assertTrue(rejected.credentialIds.isEmpty())
        assertTrue(fixture.store.listCredentials().toList().isEmpty())
    }

    private suspend fun BatchTestFixture.prepare(raw: String, configuration: CredentialConfiguration) =
        wallet.prepareIssuedCredentials(
            rawCredentials = listOf(raw),
            bindings = listOf(ResolvedCredentialHolderBinding(keys.first(), null)),
            label = null,
            metadata = null,
            proofRequired = true,
            expectedConfiguration = configuration,
        ).single()

    private suspend fun assertFormatMismatch(
        fixture: BatchTestFixture,
        raw: String,
        configuration: CredentialConfiguration,
    ) = assertMessage(fixture, raw, configuration, "format does not match")

    private suspend fun assertMessage(
        fixture: BatchTestFixture,
        raw: String,
        configuration: CredentialConfiguration,
        message: String,
    ) {
        val error = assertFailsWith<IllegalArgumentException> { fixture.prepare(raw, configuration) }
        assertContains(error.message ?: "", message)
    }
}

private val expectedCredentialTypes = listOf("VerifiableCredential", "UniversityDegree")

private fun w3cConfiguration(format: CredentialFormat, types: List<String> = expectedCredentialTypes) =
    CredentialConfiguration(format = format, credentialDefinition = CredentialDefinition(type = types))

private fun w3cMetadata(format: String) = batchTestMetadata().replace(
    "\"format\":\"dc+sd-jwt\",\"vct\":\"identity\"",
    "\"format\":\"$format\",\"credential_definition\":{\"type\":[\"VerifiableCredential\",\"UniversityDegree\"]}",
)

private fun credentialResponse(credential: String) = buildJsonObject {
    put("credentials", buildJsonArray { add(buildJsonObject { put("credential", credential) }) })
}.toString()

private suspend fun WalletKeyStoreEntry.publicJwk(): JsonObject {
    val key = requireNotNull(crypto2Key)
    val encoded = key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec)
    return Json.parseToJsonElement(encoded.data.toByteArray().decodeToString()).jsonObject
}

private suspend fun w3cJwtWithVcClaim(
    holderJwk: JsonObject,
    types: List<String> = expectedCredentialTypes,
): String = signW3cJwt(buildJsonObject {
    put("iss", BATCH_TEST_ISSUER)
    put("sub", "did:example:holder")
    put("vc", w3cClaims(holderJwk, types, "https://www.w3.org/2018/credentials/v1"))
})

private suspend fun w3cJwtEnveloped(
    holderJwk: JsonObject,
    types: List<String> = expectedCredentialTypes,
): String = signW3cJwt(w3cClaims(holderJwk, types, "https://www.w3.org/ns/credentials/v2"))

private fun ldpVc(
    holderJwk: JsonObject,
    types: List<String> = expectedCredentialTypes,
): String = buildJsonObject {
    w3cClaims(holderJwk, types, "https://www.w3.org/2018/credentials/v1").forEach { (key, value) -> put(key, value) }
    putJsonObject("proof") {
        put("type", "DataIntegrityProof")
        put("cryptosuite", "eddsa-rdfc-2022")
        put("proofPurpose", "assertionMethod")
        put("verificationMethod", "$BATCH_TEST_ISSUER#key")
    }
}.toString()

private fun w3cClaims(holderJwk: JsonObject, types: List<String>, context: String) = buildJsonObject {
    putJsonArray("@context") { add(JsonPrimitive(context)) }
    put("type", JsonArray(types.map(::JsonPrimitive)))
    put("issuer", BATCH_TEST_ISSUER)
    putJsonObject("credentialSubject") { put("id", "did:example:holder") }
    putJsonObject("cnf") { put("jwk", holderJwk) }
}

private suspend fun signW3cJwt(payload: JsonObject): String {
    val issuerKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
        GenerateSoftwareKeyRequest(KeyId("test-issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN)),
    )
    return CompactJws.sign(
        key = issuerKey,
        algorithm = JwsAlgorithm.ES256,
        payload = payload.toString().encodeToByteArray(),
        protectedHeader = buildJsonObject { put("typ", "JWT") },
    )
}
