package id.walt.credentials.issuance

import id.walt.credentials.issuance.CredentialDataMergeUtils.mergeSDJwtVCPayloadWithMapping
import id.walt.credentials.issuance.CredentialDataMergeUtils.mergeWithMapping
import id.walt.credentials.issuance.MergingIssuer.mergingJwtIssue
import id.walt.credentials.issuance.MergingIssuer.mergingSdJwtIssue
import id.walt.credentials.issuance.MergingIssuer.mergingToVc
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.sdjwt.SDMap
import id.walt.w3c.vc.vcs.W3CVC
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class MergingIssuerTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    private suspend fun key() = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(id = KeyId("merging-key"), spec = KeySpec.Ec(EcCurve.P256), usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
    )

    private val credential = W3CVC.build(
        context = listOf("https://www.w3.org/2018/credentials/v1"),
        type = listOf("VerifiableCredential", "ExampleCredential"),
        "credentialSubject" to buildJsonObject { put("name", "Jane") },
    )

    @Test
    fun `merging issuance signs JWT and SD-JWT credentials with crypto2`() = runTest {
        val key = key()
        val mergedJwt = credential.mergingJwtIssue(
            issuerKey = key, algorithm = JwsAlgorithm.ES256, issuerId = "https://issuer.example", subjectDid = "did:example:holder",
            mappings = JsonObject(emptyMap()), additionalJwtHeader = emptyMap(), additionalJwtOptions = emptyMap(),
        )
        val mergedSdJwt = credential.mergingSdJwtIssue(
            issuerKey = key, algorithm = JwsAlgorithm.ES256, issuerId = "https://issuer.example", subjectDid = "did:example:holder",
            mappings = JsonObject(emptyMap()), type = "vc+sd-jwt", additionalJwtHeaders = emptyMap(), additionalJwtOptions = emptyMap(),
            disclosureMap = SDMap(emptyMap()),
        )

        assertTrue(CompactJws.verify(mergedJwt, key, JwsAlgorithm.ES256).payload.isNotEmpty())
        assertTrue(CompactJws.verify(mergedSdJwt.substringBefore('~'), key, JwsAlgorithm.ES256).payload.isNotEmpty())
    }

    @Test
    fun `the template context names the issuer in both fields also when it is not a DID`() {
        // The example profiles map "issuer": {"id": "<issuerDid>"}; an issuer URL must still resolve it.
        val url = issuanceTemplateContext(issuerId = "https://issuer.example", subjectDid = "did:example:holder")
        assertEquals(JsonPrimitive("https://issuer.example"), url["issuerId"])
        assertEquals(JsonPrimitive("https://issuer.example"), url["issuerDid"])

        val did = issuanceTemplateContext(issuerId = "did:key:z6Mk", subjectDid = null)
        assertEquals(JsonPrimitive("did:key:z6Mk"), did["issuerDid"])
        assertFalse("subjectDid" in did, "absent values are left out")
    }

    private class MappingKey(key: Key, exporter: PublicKeyExporter) : Key by key {
        override val capabilities = key.capabilities.copy(publicKeyExporter = exporter)
    }

    @Test
    fun `both subject DID lookups prefer the supplied DID and derive once per merge on demand`() = runTest {
        val key = key()
        val publicKey = requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey()
        var exports = 0
        val holder = MappingKey(key) {
            exports++
            publicKey
        }
        val mapping = buildJsonObject {
            put("jwt:sub", "<context:subjectDid>")
            put("reference", "<subjectDid>")
            put("holder", buildJsonObject { put("id", "<context:subjectDid>") })
        }

        withContext(IssuanceHolderKey(holder)) {
            val literalArray = JsonArray(listOf(JsonPrimitive("<subjectDid>")))
            val literal = credential.mergingToVc("issuer", "", buildJsonObject { put("tags", literalArray) })
            assertEquals(literalArray, literal.w3cVc["tags"])

            val supplied = "did:example:wallet"
            val mapped = credential.mergingToVc("issuer", supplied, mapping).w3cVc
            assertEquals(JsonPrimitive(supplied), mapped["reference"])
            assertEquals(mapped["reference"], mapped["holder"]?.jsonObject?.get("id"))
            assertEquals(0, exports)

            val context = issuanceTemplateContext("issuer", null)
            val functions = issuanceDataFunctions()
            val w3c = credential.mergeWithMapping(mapping, context, functions)
            val did = w3c.results.getValue("jwt:sub")
            assertTrue(did.jsonPrimitive.content.startsWith("did:jwk:"))
            assertEquals(did, w3c.vc.getValue("reference"))
            assertEquals(did, w3c.vc.getValue("holder").jsonObject.getValue("id"))
            assertEquals(1, exports)

            val sdJwt = JsonObject(emptyMap()).mergeSDJwtVCPayloadWithMapping(mapping, context, functions)
            assertEquals(did, sdJwt.getValue("sub"))
            assertEquals(did, sdJwt.getValue("holder").jsonObject.getValue("id"))
            assertEquals(2, exports)
            assertFalse("subjectDid" in context)
        }
    }

    @Test
    fun `a requested subject DID reports unavailable key material and propagates cancellation`() = runTest {
        val key = key()
        val mapping = buildJsonObject { put("reference", "<subjectDid>") }
        val unavailable = MappingKey(key) { error("Public key unavailable") }
        val failure = withContext(IssuanceHolderKey(unavailable)) {
            assertFailsWith<IllegalArgumentException> { credential.mergingToVc("issuer", "", mapping) }
        }
        assertEquals("Cannot find in context: subjectDid", failure.message)
        assertEquals("Public key unavailable", failure.cause?.message)

        val cancelled = MappingKey(key) { throw CancellationException("Export cancelled") }
        assertFailsWith<CancellationException> {
            withContext(IssuanceHolderKey(cancelled)) { credential.mergingToVc("issuer", "", mapping) }
        }
    }

    @Test
    fun `every timestamp of one issuance reads the issuance clock`() = runTest {
        val issuedAt = Instant.parse("2031-02-03T04:05:06Z")
        val mapping = buildJsonObject {
            put("issuanceDate", "<timestamp>")
            put("credentialSubject", buildJsonObject { put("issuedOn", "<date>") })
        }

        val vc = withContext(IssuanceClock(issuedAt)) {
            credential.mergingToVc(issuerId = "https://issuer.example", subjectDid = "did:example:holder", mappings = mapping).w3cVc
        }

        assertEquals("2031-02-03T04:05:06Z", vc["issuanceDate"]?.jsonPrimitive?.content)
        assertEquals("2031-02-03", vc["credentialSubject"]?.jsonObject?.get("issuedOn")?.jsonPrimitive?.content)
    }
}
