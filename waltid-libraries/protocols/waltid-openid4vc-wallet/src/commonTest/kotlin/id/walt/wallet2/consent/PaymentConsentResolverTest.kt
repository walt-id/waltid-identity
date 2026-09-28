package id.walt.wallet2.consent

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto.utils.ShaUtils
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.test.*

class PaymentConsentResolverTest {
    private fun test(block: suspend PaymentConsentFixture.() -> Unit) = runTest {
        withContext(Dispatchers.Default) {
            val fixture = PaymentConsentFixture.create()
            try { fixture.block() } finally { fixture.close() }
        }
    }

    @Test fun authenticatedInlineMetadataResolvesWithNoRequiredIntegrityPin() = test {
        val credential = credential()
        val en = resolver.resolve(credential, payload, listOf("en-US"))
        val de = resolver.resolve(credential, payload, listOf("de-AT"))
        assertEquals("Pay", en.affirmativeAction)
        assertEquals("Zahlen", de.affirmativeAction)
        assertEquals(en.fields.map { it.value }, de.fields.map { it.value })
        assertEquals(2, requests.size) // A new resolution does not reuse a previous metadata snapshot.
        assertNull(en.securityHint)
    }

    @Test fun signatureIssuerAndStoredClaimTamperingFailBeforeMetadataFetch() = test {
        val valid = credential()
        assertFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL) {
            PaymentConsentResolver(emptyList(), client).resolve(valid, payload, listOf("en"))
        }
        assertFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL) {
            resolver.resolve(credential(buildJsonObject { put("iss", "https://attacker.example") }), payload, listOf("en"))
        }
        assertFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL) {
            resolver.resolve(valid.copy(credentialData = JsonObject(valid.credentialData + ("vct" to JsonPrimitive("https://attacker.example/vct")))), payload, listOf("en"))
        }
        val wire = requireNotNull(valid.signedWithDisclosures)
        val segments = wire.substringBefore('~').split('.').toMutableList()
        segments[2] = (if (segments[2][0] == 'A') "B" else "A") + segments[2].drop(1)
        val altered = segments.joinToString(".") + "~"
        assertFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL) {
            resolver.resolve(valid.copy(signed = altered.substringBefore('~'), signedWithDisclosures = altered), payload, listOf("en"))
        }
        assertTrue(requests.isEmpty())
    }

    @Test fun validityAndUnreachableDisclosuresAreNotAcceptedAsAuthenticatedClaims() = test {
        for (extra in listOf(
            buildJsonObject { put("exp", 1) }, buildJsonObject { put("nbf", 99_999_999_999) },
            buildJsonObject { put("exp", "99999999999") },
        )) assertFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL) {
            resolver.resolve(credential(extra), payload, listOf("en"))
        }
        val valid = credential()
        val rogue = kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
            .encode("[\"salt\",\"vct\",\"https://attacker.example/vct\"]".encodeToByteArray())
        assertFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL) {
            resolver.resolve(valid.copy(signedWithDisclosures = valid.signedWithDisclosures + rogue + "~"), payload, listOf("en"))
        }
        assertTrue(requests.isEmpty())
    }

    @Test fun vctCategoryAndUnsupportedInheritedSchemaNeverBecomeConsent() = test {
        val base = metadata
        for ((name, value, reason) in listOf(
            Triple("vct", JsonPrimitive("https://issuer.example/other"), PaymentConsentFailure.INVALID_METADATA),
            Triple("category", JsonPrimitive("other"), PaymentConsentFailure.INVALID_METADATA),
            Triple("transaction_data_types", JsonObject(emptyMap()), PaymentConsentFailure.INVALID_METADATA),
            Triple("extends", JsonPrimitive("https://issuer.example/base"), PaymentConsentFailure.UNSUPPORTED_SCHEMA),
            Triple("schema_uri", JsonPrimitive("https://issuer.example/schema"), PaymentConsentFailure.UNSUPPORTED_SCHEMA),
        )) {
            metadata = JsonObject(base + (name to value))
            assertFailure(reason) { resolver.resolve(credential(), payload, listOf("en")) }
        }
    }

    @Test fun unknownReferenceExtensionsAreIgnoredWithoutFetchingOrWeakeningKnownIntegrity() = test {
        val type = metadata.getValue("transaction_data_types").jsonObject.getValue(TS12_PAYMENT_TYPE).jsonObject
        metadata = JsonObject(metadata + ("transaction_data_types" to buildJsonObject {
            put(TS12_PAYMENT_TYPE, JsonObject(type + mapOf(
                "vendor_uri" to JsonPrimitive("https://unused.example/document"),
                "vendor_uri#integrity" to JsonPrimitive("unknown-extension-value"),
            )))
        }))
        val extended = credential(buildJsonObject {
            put("transaction_data_types['urn:other'].claims_uri#integrity", "unknown-extension-value")
        })
        assertEquals("Pay", resolver.resolve(extended, payload, listOf("en")).affirmativeAction)
        assertEquals(listOf("/vct"), requests)
        assertFailure(PaymentConsentFailure.UNSUPPORTED_SCHEMA) {
            resolver.resolve(credential(buildJsonObject {
                put("transaction_data_types['$TS12_PAYMENT_TYPE'].schema_uri#integrity", "sha256-unsupported")
            }), payload, listOf("en"))
        }
    }

    @Test fun referenceDocumentsHonorBothCredentialAndMetadataIntegrityOverExactBytes() = test {
        val type = metadata["transaction_data_types"]!!.jsonObject[TS12_PAYMENT_TYPE]!!.jsonObject
        documents["/claims"] = type.getValue("claims").toString()
        documents["/catalogue"] = type.getValue("ui_labels").toString()
        fun hash(path: String) = "sha256-" + ShaUtils.sha256Base64Url(documents.getValue(path).encodeToByteArray())
        metadata = JsonObject(metadata + ("transaction_data_types" to buildJsonObject {
            put(TS12_PAYMENT_TYPE, buildJsonObject {
                put("schema", TS12_PAYMENT_TYPE)
                put("claims_uri", "$ISSUER/claims"); put("claims_uri#integrity", hash("/claims"))
                put("ui_labels_uri", "$ISSUER/catalogue"); put("ui_labels_uri#integrity", hash("/catalogue"))
            })
        }))
        val pinned = credential(buildJsonObject {
            put("vct#integrity", "sha256-" + ShaUtils.sha256Base64Url(metadata.toString().encodeToByteArray()))
            put("transaction_data_types['$TS12_PAYMENT_TYPE'].claims_uri#integrity", hash("/claims"))
            put("transaction_data_types['$TS12_PAYMENT_TYPE'].ui_labels_uri#integrity", hash("/catalogue"))
        })
        assertEquals("Pay", resolver.resolve(pinned, payload, listOf("en")).affirmativeAction)
        assertEquals(listOf("/vct", "/claims", "/catalogue"), requests)
        documents["/catalogue"] = " " + documents.getValue("/catalogue")
        assertFailure(PaymentConsentFailure.INTEGRITY_MISMATCH) { resolver.resolve(pinned, payload, listOf("en")) }
        documents["/catalogue"] = documents.getValue("/catalogue").trimStart()
        val conflicting = credential(buildJsonObject {
            put("transaction_data_types['$TS12_PAYMENT_TYPE'].claims_uri#integrity", "sha256-" + ShaUtils.sha256Base64Url("different".encodeToByteArray()))
        })
        assertFailure(PaymentConsentFailure.INTEGRITY_MISMATCH) { resolver.resolve(conflicting, payload, listOf("en")) }
        metadata = JsonObject(metadata + ("description" to JsonPrimitive("changed")))
        assertFailure(PaymentConsentFailure.INTEGRITY_MISMATCH) { resolver.resolve(pinned, payload, listOf("en")) }
    }
}

internal suspend fun assertFailure(reason: PaymentConsentFailure, action: suspend () -> Unit) {
    assertEquals(reason, assertFailsWith<PaymentConsentException> { action() }.reason)
}

internal const val ISSUER = "https://issuer.example"
internal class PaymentConsentFixture private constructor(val runtime: CryptoRuntime, val issuerKey: Key) {
    val payload = Json.parseToJsonElement("""{"transaction_id":"txn-1","payee":{"name":"Super Store","id":"merchant-001"},"currency":"EUR","amount":11.56}""").jsonObject
    var metadata = buildJsonObject {
        put("vct", "$ISSUER/vct"); put("category", SCA_ATTESTATION_CATEGORY)
        put("transaction_data_types", buildJsonObject {
            put(TS12_PAYMENT_TYPE, buildJsonObject {
                put("schema", TS12_PAYMENT_TYPE)
                put("claims", JsonArray(paymentValues(payload).keys.map { path -> buildJsonObject {
                    put("path", JsonArray(path.map(::JsonPrimitive)))
                    put("display", buildJsonArray {
                        add(buildJsonObject { put("locale", "en"); put("label", path.last()) })
                        add(buildJsonObject { put("locale", "de"); put("label", "DE ${path.last()}") })
                    })
                } }))
                put("ui_labels", buildJsonObject { put("affirmative_action_label", buildJsonArray {
                    add(buildJsonObject { put("lang", "en"); put("value", "Pay") })
                    add(buildJsonObject { put("lang", "de"); put("value", "Zahlen") })
                }) })
            })
        })
    }
    var onMetadataRequest: suspend () -> Unit = {}
    val requests = mutableListOf<String>()
    val documents = mutableMapOf<String, String>()
    val client = HttpClient(MockEngine { request ->
        onMetadataRequest()
        requests += request.url.encodedPath
        val body = if (request.url.encodedPath == "/vct") metadata.toString() else documents.getValue(request.url.encodedPath)
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    })
    lateinit var resolver: PaymentConsentResolver
        private set
    suspend fun credential(extra: JsonObject = JsonObject(emptyMap())): SdJwtCredential {
        val claims = buildJsonObject {
            put("iss", ISSUER); put("vct", "$ISSUER/vct"); put("iat", 1); put("exp", 99_999_999_999)
            put("_sd_alg", "sha-256"); put("_sd", JsonArray(emptyList()))
            extra.forEach { (name, value) -> put(name, value) }
        }
        val jwt = CompactJws.sign(claims.toString().encodeToByteArray(), issuerKey, JwsAlgorithm.ES256,
            buildJsonObject { put("typ", "dc+sd-jwt") })
        return CredentialParser.detectAndParse("$jwt~").second as SdJwtCredential
    }
    suspend fun close() { client.close(); runtime.close() }
    companion object {
        suspend fun create(): PaymentConsentFixture {
            val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
            val key = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(KeyId("issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)))
            return PaymentConsentFixture(runtime, key).also { fixture ->
                val public = requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey().toPublicJwk(key.spec)
                fixture.resolver = PaymentConsentResolver(listOf(PaymentCredentialIssuer(ISSUER, public.data.toByteArray().decodeToString())), fixture.client)
            }
        }
    }
}
