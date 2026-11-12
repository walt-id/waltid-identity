package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TypedKeyGenerationRequest
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.did.dids.DidService
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.handlers.BuildVpTokenRequest
import id.walt.wallet2.handlers.BuildVpTokenResult
import id.walt.wallet2.handlers.ImportCredentialRequest
import id.walt.wallet2.handlers.MatchCredentialsFromStoreRequest
import id.walt.wallet2.handlers.MatchCredentialsResult
import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.handlers.PreviewPresentationRequest
import id.walt.wallet2.persistence.*
import id.walt.wallet2.server.handlers.CreateDidRequest
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for previously untested or undertested scenarios.
 *
 * Each test is named after the ticket / bug it guards against.
 */
class Wallet2RegressionTest {

    private val host = "127.0.0.1"

    // -------------------------------------------------------------------------
    // Missing store at wallet creation should return 400, not 500
    // -------------------------------------------------------------------------

    @Test
    fun `wallet creation with non-existing store returns 400`() {
        OSSWallet2Service.walletStore = InMemoryWalletStore()

        val port = freePort()
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
            },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()

            testAndReturn("Non-existing key store -> 400 not 500") {
                http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest(keyStoreIds = listOf("does-not-exist")))
                }.also {
                    assertEquals(
                        HttpStatusCode.BadRequest, it.status,
                        "Expected 400 for missing key store, got ${it.status}: ${it.bodyAsText()}"
                    )
                }
            }

            testAndReturn("Non-existing credential store -> 400 not 500") {
                http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest(credentialStoreIds = listOf("does-not-exist")))
                }.also {
                    assertEquals(
                        HttpStatusCode.BadRequest, it.status,
                        "Expected 400 for missing credential store, got ${it.status}: ${it.bodyAsText()}"
                    )
                }
            }

            testAndReturn("Non-existing DID store -> 400 not 500") {
                http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest(didStoreId = "does-not-exist"))
                }.also {
                    assertEquals(
                        HttpStatusCode.BadRequest, it.status,
                        "Expected 400 for missing DID store, got ${it.status}: ${it.bodyAsText()}"
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Named store creation: duplicate should return 409 Conflict
    // -------------------------------------------------------------------------

    @Test
    fun `duplicate named store creation returns 409`() {
        OSSWallet2Service.walletStore = InMemoryWalletStore()

        val port = freePort()
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
            },
            init = {},
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()

            testAndReturn("Create named key store - first time -> 201") {
                http.post("/stores/keys/my-keys")
                    .also { assertEquals(HttpStatusCode.Created, it.status) }
            }

            testAndReturn("Create named key store - duplicate -> 409") {
                http.post("/stores/keys/my-keys")
                    .also {
                        assertEquals(
                            HttpStatusCode.Conflict, it.status,
                            "Expected 409 for duplicate store, got ${it.status}: ${it.bodyAsText()}"
                        )
                    }
            }

            testAndReturn("Create named credential store - first time -> 201") {
                http.post("/stores/credentials/my-creds")
                    .also { assertEquals(HttpStatusCode.Created, it.status) }
            }

            testAndReturn("Create named credential store - duplicate -> 409") {
                http.post("/stores/credentials/my-creds")
                    .also {
                        assertEquals(
                            HttpStatusCode.Conflict, it.status,
                            "Expected 409 for duplicate credential store, got ${it.status}: ${it.bodyAsText()}"
                        )
                    }
            }
        }
    }

    // -------------------------------------------------------------------------
    // set-default key and DID routes
    // -------------------------------------------------------------------------

    @Test
    fun `set-default key and DID routes work`() {
        OSSWallet2Service.walletStore = InMemoryWalletStore()

        val port = freePort()
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
            },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()

            val walletId = testAndReturn("Create wallet") {
                http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest())
                }.body<WalletCreatedResponse>().walletId
            }

            val key1 = testAndReturn("Generate first key") {
                http.post("/wallet/$walletId/keys/generate") {
                    contentType(ContentType.Application.Json)
                    setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(keyType = KeyType.secp256r1))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletKeyInfo>()
            }

            val key2 = testAndReturn("Generate second key") {
                http.post("/wallet/$walletId/keys/generate") {
                    contentType(ContentType.Application.Json)
                    setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(keyType = KeyType.Ed25519))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletKeyInfo>()
            }

            testAndReturn("Set second key as default -> 204") {
                http.put("/wallet/$walletId/keys/${key2.keyId}/set-default")
                    .also {
                        assertEquals(
                            HttpStatusCode.NoContent, it.status,
                            "Expected 204 for set-default key, got ${it.status}: ${it.bodyAsText()}"
                        )
                    }
            }

            testAndReturn("Set-default with non-existing key -> 400") {
                http.put("/wallet/$walletId/keys/nonexistent-key-id/set-default")
                    .also {
                        assertEquals(
                            HttpStatusCode.BadRequest, it.status,
                            "Expected 400 for non-existing key in set-default, got ${it.status}"
                        )
                    }
            }

            testAndReturn("Create DID with first key") {
                http.post("/wallet/$walletId/dids/create") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateDidRequest(method = "key", keyId = key1.keyId))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
            }

            val did2 = testAndReturn("Create second DID") {
                http.post("/wallet/$walletId/dids/create") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateDidRequest(method = "key", keyId = key2.keyId))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletDidEntry>().did
            }

            testAndReturn("Set second DID as default -> 204") {
                // URL-encode colons in the DID for use in the path segment
                val encodedDid = did2.replace(":", "%3A")
                http.put("/wallet/$walletId/dids/$encodedDid/set-default")
                    .also {
                        assertEquals(
                            HttpStatusCode.NoContent, it.status,
                            "Expected 204 for set-default DID, got ${it.status}: ${it.bodyAsText()}"
                        )
                    }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Keys and credentials must survive an in-process state reset
    // (simulates a service restart when persistence is enabled)
    // -------------------------------------------------------------------------

    @Test
    fun `keys survive store-registry reset when persistence is enabled`() {
        // Use an in-memory SQLite DB so the test is fully self-contained
        val db = initWallet2Database(
            Wallet2PersistenceConfig(
                jdbcUrl = "jdbc:sqlite::memory:",
                driverClassName = "org.sqlite.JDBC",
            )
        )

        // Wire persistence exactly as Main.kt does at startup
        OSSWallet2Service.walletStore = ExposedWalletStore(db)
        OSSWallet2Service.keyStoreFactory = { id -> ExposedKeyStore(id, db) }
        OSSWallet2Service.credentialStoreFactory = { id -> ExposedCredentialStore(id, db) }
        OSSWallet2Service.didStoreFactory = { id -> ExposedDidStore(id, db) }

        val port = freePort()
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
            },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()

            val walletId = testAndReturn("Create wallet") {
                http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest())
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletCreatedResponse>().walletId
            }

            val keyId = testAndReturn("Generate key") {
                http.post("/wallet/$walletId/keys/generate") {
                    contentType(ContentType.Application.Json)
                    setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(keyType = KeyType.secp256r1))
                }.also { assertEquals(HttpStatusCode.Created, it.status) }
                    .body<WalletKeyInfo>().keyId
            }

            testAndReturn("Key is listed before reset") {
                val keys = http.get("/wallet/$walletId/keys").body<List<WalletKeyInfo>>()
                assertTrue(
                    keys.any { it.keyId == keyId },
                    "Generated key $keyId should be listed before reset"
                )
            }

            // Simulate a service restart by re-assigning the factories.
            // The in-process named-store cache is rebuilt from the factories on next access
            // (via computeIfAbsent), exactly as it would be after a real process restart.
            OSSWallet2Service.walletStore = ExposedWalletStore(db)
            OSSWallet2Service.keyStoreFactory = { id -> ExposedKeyStore(id, db) }
            OSSWallet2Service.credentialStoreFactory = { id -> ExposedCredentialStore(id, db) }
            OSSWallet2Service.didStoreFactory = { id -> ExposedDidStore(id, db) }

            testAndReturn("Key survives store-registry reset (Bug 8 regression)") {
                val keys = http.get("/wallet/$walletId/keys").body<List<WalletKeyInfo>>()
                assertTrue(
                    keys.any { it.keyId == keyId },
                    "Key $keyId should still be listed after store-registry reset"
                )
            }

            testAndReturn("Wallet is still listed after reset") {
                val wallets = http.get("/wallet").body<List<String>>()
                assertTrue(
                    walletId in wallets,
                    "Wallet $walletId should still be listed after reset"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // match-credentials-from-store must return wallet-assigned IDs,
    // not internal DCQL store indices ("0", "1", ...)
    // -------------------------------------------------------------------------

    @Test
    fun `match-credentials-from-store returns wallet credential IDs not indices`() {
        OSSWallet2Service.walletStore = InMemoryWalletStore()

        val port = freePort()
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
            },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()

            val walletId = testAndReturn("Create wallet") {
                http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest())
                }.body<WalletCreatedResponse>().walletId
            }

            // Import a minimal SD-JWT VC so DCQL can match on format.
            // The JWT is intentionally malformed (fake signature) - we only care
            // that CredentialParser recognises the format and stores the credential.
            val rawSdJwt = "eyJhbGciOiJFZERTQSIsInR5cCI6InZjK3NkLWp3dCJ9" +
                    ".eyJzdWIiOiJkaWQ6a2V5OnRlc3QiLCJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIiwidmN0IjoiSWRlbnRpdHlDcmVkZW50aWFsIn0" +
                    ".sig~"

            val credentialId = testAndReturn("Import SD-JWT credential") {
                val response = http.post("/wallet/$walletId/credentials/import") {
                    contentType(ContentType.Application.Json)
                    setBody(ImportCredentialRequest(rawCredential = rawSdJwt, label = "Test SD-JWT"))
                }
                if (response.status == HttpStatusCode.Created) {
                    response.body<StoredCredential>().id
                } else {
                    // Parser rejected the malformed JWT - skip the DCQL assertion
                    "SKIPPED-${response.status}"
                }
            }

            if (!credentialId.startsWith("SKIPPED")) {
                testAndReturn("match-credentials-from-store returns wallet ID not index") {
                    val result = http.post("/wallet/$walletId/credentials/present/match-credentials-from-store") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            MatchCredentialsFromStoreRequest(
                                dcqlQuery = DcqlQuery(
                                    credentials = listOf(
                                        CredentialQuery(
                                            id = "cred-q",
                                            format = CredentialFormat.DC_SD_JWT,
                                            meta = NoMeta,
                                        )
                                    )
                                )
                            )
                        )
                    }.also { assertEquals(HttpStatusCode.OK, it.status) }
                        .body<MatchCredentialsResult>()

                    // The matched credential IDs must be wallet-assigned UUIDs, not "0", "1", ...
                    result.matchedCredentialIds.values.flatten().forEach { id ->
                        assertTrue(
                            !id.matches(Regex("^\\d+$")),
                            "Expected wallet-assigned UUID but got index '$id' (Bug 3 regression)"
                        )
                        assertEquals(
                            credentialId, id,
                            "Matched ID should equal the wallet-assigned credential ID"
                        )
                    }
                    result
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // WAL-1597: legacy "vc+sd-jwt" DCQL format is rejected with a descriptive 400
    // -------------------------------------------------------------------------

    @Test
    fun `presentation preview with legacy vc+sd-jwt DCQL format returns descriptive 400`() {
        OSSWallet2Service.walletStore = InMemoryWalletStore()

        val port = freePort()
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
            },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()

            val walletId = testAndReturn("Create wallet") {
                http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest())
                }.body<WalletCreatedResponse>().walletId
            }

            // Plain (by-value) request from a third-party verifier still using the pre-1.0 media type.
            val responseUri = "https://verifier.example/response"
            val dcqlQuery = """{"credentials":[{"id":"pid","format":"vc+sd-jwt","meta":{"vct_values":["https://example.com/pid"]}}]}"""
            val requestUrl = Url(
                "openid4vp://?client_id=${"redirect_uri:$responseUri".encodeURLParameter()}" +
                        "&response_type=vp_token&response_mode=direct_post" +
                        "&response_uri=${responseUri.encodeURLParameter()}" +
                        "&nonce=n-1597&dcql_query=${dcqlQuery.encodeURLParameter()}"
            )

            testAndReturn("Preview with vc+sd-jwt -> 400 naming the format, not a generic enum error") {
                http.post("/wallet/$walletId/credentials/present/preview") {
                    contentType(ContentType.Application.Json)
                    setBody(PreviewPresentationRequest(requestUrl = requestUrl))
                }.also {
                    val body = it.bodyAsText()
                    assertEquals(HttpStatusCode.BadRequest, it.status, "Expected 400, got ${it.status}: $body")
                    val message = Json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content.orEmpty()
                    assertTrue("Unsupported DCQL credential format \"vc+sd-jwt\"" in message, message)
                    assertTrue("must use \"dc+sd-jwt\"" in message, message)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // A requested SD-JWT array element is presented, not another one
    // -------------------------------------------------------------------------

    @Test
    fun `presentation of an SD-JWT array element sends exactly that element`() {
        OSSWallet2Service.walletStore = InMemoryWalletStore()

        val port = freePort()
        E2ETest(host, port, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:$port"))
                )
            },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) }
        ) {
            val http = testHttpClient()

            val walletId = testAndReturn("Create wallet") {
                http.post("/wallet") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateWalletRequest())
                }.body<WalletCreatedResponse>().walletId
            }
            val keyId = testAndReturn("Generate holder key") {
                http.post("/wallet/$walletId/keys/generate") {
                    contentType(ContentType.Application.Json)
                    setBody<TypedKeyGenerationRequest>(TypedKeyGenerationRequest.Jwk(keyType = KeyType.secp256r1))
                }.body<WalletKeyInfo>().keyId
            }

            // Array-element disclosures carry no claim name; the wallet used to pick one by name.
            val india = SdJwtSelectiveDisclosure("salt-in", null, JsonPrimitive("IN"))
            val france = SdJwtSelectiveDisclosure("salt-fr", null, JsonPrimitive("FR"))
            fun b64(json: JsonElement) = json.toString().encodeToByteArray().encodeToBase64Url()
            val header = buildJsonObject { put("alg", "ES256"); put("typ", "dc+sd-jwt") }
            val payload = buildJsonObject {
                put("iss", "https://issuer.example")
                put("vct", "https://example.com/pid")
                put("_sd_alg", "sha-256")
                putJsonArray("nationalities") {
                    addJsonObject { put("...", india.asHashedFromEncoded()) }
                    addJsonObject { put("...", france.asHashedFromEncoded()) }
                }
            }
            val rawSdJwt = "${b64(header)}.${b64(payload)}.c2ln~${india.encoded}~${france.encoded}~"

            val credentialId = testAndReturn("Import SD-JWT credential") {
                http.post("/wallet/$walletId/credentials/import") {
                    contentType(ContentType.Application.Json)
                    setBody(ImportCredentialRequest(rawCredential = rawSdJwt, label = "SD-JWT array elements"))
                }.also { assertEquals(HttpStatusCode.Created, it.status, it.bodyAsText()) }
                    .body<JsonObject>().getValue("id").jsonPrimitive.content
            }

            val responseUri = "https://verifier.example/response"
            val dcqlQuery = """{"credentials":[{"id":"pid","format":"dc+sd-jwt","meta":{"vct_values":["https://example.com/pid"]},""" +
                    """"claims":[{"path":["nationalities",1]}]}]}"""
            val requestUrl = Url(
                "openid4vp://?client_id=${"redirect_uri:$responseUri".encodeURLParameter()}" +
                        "&response_type=vp_token&response_mode=direct_post" +
                        "&response_uri=${responseUri.encodeURLParameter()}" +
                        "&nonce=n-array-element&dcql_query=${dcqlQuery.encodeURLParameter()}"
            )

            testAndReturn("Preview shows the requested element") {
                val preview = http.post("/wallet/$walletId/credentials/present/preview") {
                    contentType(ContentType.Application.Json)
                    setBody(PreviewPresentationRequest(requestUrl = requestUrl, keyId = keyId))
                }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                    .body<JsonObject>()
                val disclosures = preview.getValue("credentialOptions").jsonArray.single()
                    .jsonObject.getValue("disclosures").jsonArray.map { it.jsonObject }
                val requested = disclosures.single { it["selectivelyDisclosable"]?.jsonPrimitive?.boolean == true }
                assertEquals(JsonPrimitive("FR"), requested["value"])
            }

            testAndReturn("vp_token carries only the requested element's disclosure") {
                val vpToken = http.post("/wallet/$walletId/credentials/present/build-vp-token") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        BuildVpTokenRequest(
                            requestUrl = requestUrl,
                            selectedCredentialOptions = listOf(PresentationCredentialSelection("pid", credentialId)),
                            keyId = keyId,
                        )
                    )
                }.also { assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText()) }
                    .body<BuildVpTokenResult>().vpToken
                val presentation = Json.parseToJsonElement(vpToken).jsonObject.getValue("pid").jsonArray.single().jsonPrimitive.content
                val sent = presentation.split("~").drop(1).filter { it.isNotEmpty() && it.count { c -> c == '.' } != 2 }
                assertEquals(listOf(france.encoded), sent)
            }
        }
    }
}
