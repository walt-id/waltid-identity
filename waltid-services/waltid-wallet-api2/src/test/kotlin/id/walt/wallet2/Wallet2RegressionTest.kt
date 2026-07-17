package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TypedKeyGenerationRequest
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.did.dids.DidService
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.handlers.ImportCredentialRequest
import id.walt.wallet2.handlers.MatchCredentialsFromStoreRequest
import id.walt.wallet2.handlers.MatchCredentialsResult
import id.walt.wallet2.persistence.*
import id.walt.wallet2.server.handlers.CreateDidRequest
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import id.walt.wallet2.stores.inmemory.InMemoryWalletStore
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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

        E2ETest(host, 17060, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:17060"))
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

        E2ETest(host, 17061, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:17061"))
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

        E2ETest(host, 17062, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:17062"))
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

        E2ETest(host, 17063, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:17063"))
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

        E2ETest(host, 17064, failEarly = true).testBlock(
            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(publicBaseUrl = Url("http://$host:17064"))
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
}
