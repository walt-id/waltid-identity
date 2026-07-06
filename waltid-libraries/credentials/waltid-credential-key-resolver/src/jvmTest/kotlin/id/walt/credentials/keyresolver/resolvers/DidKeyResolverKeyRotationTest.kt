package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.Key
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.DidResolver
import id.walt.did.dids.resolver.local.DidWebResolver
import id.walt.webdatafetching.WebDataFetcher
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DidKeyResolverKeyRotationTest {

    private var previousWebResolver: DidResolver? = null
    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    @BeforeTest
    fun setup() {
        DidWebResolver.enableHttps(false)
        server = embeddedServer(Netty, port = DID_WEB_PORT) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                get("/key-rotation/did.json") {
                    call.respond(Json.decodeFromString<JsonObject>(keyRotationDidDocument))
                }
            }
        }.start(wait = false)

        previousWebResolver = DidService.resolverMethods["web"]
        DidService.registerResolverForMethod("web", testDidWebResolver)
    }

    @AfterTest
    fun tearDown() {
        previousWebResolver?.let {
            DidService.registerResolverForMethod("web", it)
        } ?: DidService.resolverMethods.remove("web")

        server.stop()
        DidWebResolver.enableHttps(true)
    }

    @Test
    fun `resolves rotated old key by full verification method kid`() = runTest {
        val key = DidKeyResolver.resolveKeyFromDid(KEY_ROTATION_DID, "$KEY_ROTATION_DID#$OLD_KEY_ID")

        assertEquals("$KEY_ROTATION_DID#$OLD_KEY_ID", key.getKeyId())
    }

    @Test
    fun `resolves rotated new key by full verification method kid even when it is not first`() = runTest {
        val key = DidKeyResolver.resolveKeyFromDid(KEY_ROTATION_DID, "$KEY_ROTATION_DID#$NEW_KEY_ID")

        assertEquals("$KEY_ROTATION_DID#$NEW_KEY_ID", key.getKeyId())
    }

    @Test
    fun `resolves rotated key when jwt kid is the public key jwk kid`() = runTest {
        val key = DidKeyResolver.resolveKeyFromDid(KEY_ROTATION_DID, OLD_KEY_ID)

        assertEquals("$KEY_ROTATION_DID#$OLD_KEY_ID", key.getKeyId())
    }

    @Test
    fun `fails instead of falling back to first key when supplied kid is unknown`() = runTest {
        assertFailsWith<NoSuchElementException> {
            DidKeyResolver.resolveKeyFromDid(KEY_ROTATION_DID, "https://vault.azure.net/keys/missing")
        }
    }

    @Test
    fun `keeps first key fallback when no kid is supplied`() = runTest {
        val key = DidKeyResolver.resolveKeyFromDid(KEY_ROTATION_DID)

        assertEquals("$KEY_ROTATION_DID#$OLD_KEY_ID", key.getKeyId())
    }

    private companion object {
        const val DID_WEB_PORT = 18089
        const val KEY_ROTATION_DID = "did:web:localhost%3A$DID_WEB_PORT:key-rotation"
        const val OLD_KEY_ID = "https://vault.azure.net/keys/old-key-xxx"
        const val NEW_KEY_ID = "https://vault.azure.net/keys/new-key-yyy"

        val didWebResolver = DidWebResolver(WebDataFetcher(id = "did-key-resolver-key-rotation-test"))

        val testDidWebResolver = object : DidResolver {
            override val name: String = "key-rotation-test-did-web-resolver"

            override suspend fun getSupportedMethods(): Result<Set<String>> = Result.success(setOf("web"))

            override suspend fun resolve(did: String): Result<JsonObject> =
                didWebResolver.resolve(did).map { it.toJsonObject() }

            override suspend fun resolveToKey(did: String): Result<Key> =
                didWebResolver.resolveToKey(did)

            override suspend fun resolveToKeys(did: String): Result<Set<Key>> =
                didWebResolver.resolveToKeys(did)
        }

        val keyRotationDidDocument = """
            {
              "assertionMethod": [
                "$KEY_ROTATION_DID#$OLD_KEY_ID",
                "$KEY_ROTATION_DID#$NEW_KEY_ID"
              ],
              "authentication": [
                "$KEY_ROTATION_DID#$OLD_KEY_ID",
                "$KEY_ROTATION_DID#$NEW_KEY_ID"
              ],
              "@context": "https://www.w3.org/ns/did/v1",
              "id": "$KEY_ROTATION_DID",
              "verificationMethod": [
                {
                  "controller": "$KEY_ROTATION_DID",
                  "id": "$KEY_ROTATION_DID#$OLD_KEY_ID",
                  "publicKeyJwk": {
                    "alg": "EdDSA",
                    "crv": "Ed25519",
                    "kid": "$OLD_KEY_ID",
                    "kty": "OKP",
                    "use": "sig",
                    "x": "qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM"
                  },
                  "type": "JsonWebKey2020"
                },
                {
                  "controller": "$KEY_ROTATION_DID",
                  "id": "$KEY_ROTATION_DID#$NEW_KEY_ID",
                  "publicKeyJwk": {
                    "alg": "ES256K",
                    "crv": "secp256k1",
                    "kid": "$NEW_KEY_ID",
                    "kty": "EC",
                    "use": "sig",
                    "x": "eKx_FaLzPMT4ndwvImdV_pTv-JX1SQpJ8tDK6GLiYIE",
                    "y": "-TzpGxGLPnXWMJWTqYvqn55Z8Xi-J_ZM40bjtjGaELs"
                  },
                  "type": "JsonWebKey2020"
                }
              ]
            }
        """.trimIndent()
    }
}
