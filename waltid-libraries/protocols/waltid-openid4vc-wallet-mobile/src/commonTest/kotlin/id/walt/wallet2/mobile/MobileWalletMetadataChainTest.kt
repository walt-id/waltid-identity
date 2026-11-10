package id.walt.wallet2.mobile

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.sdjwt.*
import id.walt.sdjwt.metadata.type.TypeMetadataFixture
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock

class MobileWalletMetadataChainTest {
    @Test
    fun issuedChainSurvivesSerializedReopenAndProductionRegistryProjectionThenParentSubmission() = runTest { withContext(Dispatchers.Default) {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val keys = InMemoryMobileWalletKeyStore()
        suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val holder = key("holder").also { keys.addCrypto2Key(it) }
        val issuer = key("issuer")
        val leaf = "https://issuer.example/leaf"
        val parent = "https://types.example/sca-user"
        val payload = SDPayload.createSDPayload(buildJsonObject {
            put("iss", "https://issuer.example"); put("vct", leaf); put("vct#integrity", TypeMetadataFixture.LEAF_PIN)
            put("iat", Clock.System.now().epochSeconds); put("exp", Clock.System.now().epochSeconds + 3600)
            put("cnf", buildJsonObject {
                put("jwk", Json.parseToJsonElement(requireNotNull(holder.capabilities.publicKeyExporter)
                    .exportPublicKey().toPublicJwk(holder.spec).data.toByteArray().decodeToString()))
            })
            put("account_id", "account-1")
            put("accounts", buildJsonArray { add(buildJsonObject { put("iban", "DE123") }) })
        }, SDMap(mapOf("accounts" to SDField(sd = true))))
        val signer = Crypto2AsyncJWTCryptoProvider(mapOf("issuer" to Crypto2SdJwtKey(issuer, JwsAlgorithm.ES256)))
        val credential = SDJwt.createFromSignedJwt(signer.sign(payload.undisclosedPayload, "issuer", "dc+sd-jwt", emptyMap()), payload)
        val requested = mutableListOf<String>()
        var metadataRequests = 0
        val client = HttpClient(MockEngine { request ->
            requested += request.url.toString()
            val body = when (request.url.encodedPath) {
                "/.well-known/openid-credential-issuer" -> """{"credential_issuer":"https://issuer.example","credential_endpoint":"https://issuer.example/credential","credential_configurations_supported":{"identity":{"format":"dc+sd-jwt","vct":"$leaf","cryptographic_binding_methods_supported":["jwk"],"proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"]}}}}}"""
                "/.well-known/oauth-authorization-server" -> """{"issuer":"https://issuer.example","token_endpoint":"https://issuer.example/token","authorization_endpoint":"https://issuer.example/authorize","response_types_supported":["code"]}"""
                "/token" -> """{"access_token":"synthetic-token","token_type":"Bearer"}"""
                "/credential" -> buildJsonObject { put("credentials", buildJsonArray { add(buildJsonObject { put("credential", credential.toString()) }) }) }.toString()
                else -> {
                    metadataRequests++
                    TypeMetadataFixture.documents.getValue(request.url.toString()).decodeToString()
                }
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        var publishedRecords = emptyList<MobileWalletCredentialRegistryRecord>()
        val registry = object : MobileWalletCredentialRegistry {
            override val capabilities = MobileWalletDigitalCredentialCapabilities("test", true, "test", true, emptyList())
            override suspend fun replace(registryId: String, records: List<MobileWalletCredentialRegistryRecord>): MobileWalletCredentialRegistrationResult {
                publishedRecords = records.toList()
                return MobileWalletCredentialRegistrationResult(true, records.map { it.credentialId }.distinct().size)
            }
        }
        fun wallet(store: InMemoryCredentialStore) = MobileWallet(
            "chain-journey", keys, InMemoryDidStore(), store, issuanceHttpClient = client, credentialRegistry = registry,
        )
        try {
            val store = InMemoryCredentialStore()
            val first = wallet(store)
            val session = first.startIssuance(MobileWalletIssuanceRequest(
                MobileWalletCredentialOffer.InlineJson("""{"credential_issuer":"https://issuer.example","credential_configuration_ids":["identity"],"grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"synthetic"}}}"""),
                keyId = "holder",
            ))
            assertIs<WalletIssuanceOutcome.Stored>(first.continuePreAuthorizedIssuance(session.id))
            assertEquals(3, metadataRequests, "Metadata requests: $requested")
            val serialized = Json.encodeToString(store.listCredentials().toList())
            val restored = InMemoryCredentialStore().also { target ->
                Json.decodeFromString<List<StoredCredential>>(serialized).forEach { target.addCredential(it) }
            }
            val reopened = wallet(restored)
            assertTrue(reopened.refreshDigitalCredentialRegistration().available)
            assertEquals(setOf(leaf, parent, "https://types.example/sca"), publishedRecords.map { it.type }.toSet())
            assertEquals(1, publishedRecords.map { it.registryEntryId }.distinct().size)
            assertEquals(setOf("Bank attestation"), publishedRecords.map { it.displayName }.toSet())
            val request = """{"response_type":"vp_token","response_mode":"dc_api","nonce":"chain-nonce","dcql_query":{"credentials":[{"id":"account","format":"dc+sd-jwt","meta":{"vct_values":["$parent"]}}]}}"""
            val preview = reopened.previewDigitalCredentialPresentation(MobileWalletDigitalCredentialRequest(
                MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED, request, "https://verifier.example",
            ))
            val selected = preview.credentialOptions.single()
            val response = reopened.submitDigitalCredentialPresentation(preview.requestId,
                listOf(MobileWalletPresentationCredentialSelection(selected.queryId, selected.credentialId)))
            val presentation = Json.parseToJsonElement(response.dataJson).jsonObject.getValue("vp_token")
                .jsonObject.getValue("account").jsonArray.single().jsonPrimitive.content
            val proof = CompactJws.verify(presentation.substringAfterLast('~'), holder, JwsAlgorithm.ES256)
            assertEquals("chain-nonce", Json.parseToJsonElement(proof.payload.decodeToString()).jsonObject.getValue("nonce").jsonPrimitive.content)
            assertEquals(3, metadataRequests, "Reopen, registry, selection and submission must reuse the fully pinned snapshot")
        } finally { client.close(); runtime.close() }
    } }
}
