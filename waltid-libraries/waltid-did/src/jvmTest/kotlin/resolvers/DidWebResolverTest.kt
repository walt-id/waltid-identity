package resolvers

import TestClient
import TestServer
import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.resolver.local.DidWebResolver
import id.walt.did.dids.resolver.local.LocalResolverMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class DidWebResolverTest : DidResolverTestBase() {
    override val resolver: LocalResolverMethod = DidWebResolver(TestClient.http)

    @ParameterizedTest
    @MethodSource
    override fun `given a did String, when calling resolve, then the result is a valid did document`(
        did: String,
        key: JsonObject,
        resolverAssertion: resolverAssertion<DidDocument>,
    ) {
        super.`given a did String, when calling resolve, then the result is a valid did document`(did, key, resolverAssertion)
    }

    @ParameterizedTest
    @MethodSource
    override fun `given a did String, when calling resolveToKey, then the result is valid key`(
        did: String,
        key: JsonObject,
        resolverAssertion: resolverAssertion<Key>,
    ) {
        super.`given a did String, when calling resolveToKey, then the result is valid key`(did, key, resolverAssertion)
    }

    @ParameterizedTest
    @MethodSource
    override fun `given a did String, when calling resolveToKeys, then the result is valid keys set`(
        did: String,
        key: JsonObject,
        resolverAssertion: resolverAssertion<Set<Key>>,
    ) {
        super.`given a did String, when calling resolveToKeys, then the result is valid keys set`(did, key, resolverAssertion)
    }

    companion object {

        var serverStarted = false

        @JvmStatic
        @BeforeAll
        fun ensureServerStarted() {
            if (!serverStarted) {
                println("Starting test web server...")
                TestServer.server.start()
                serverStarted = true
            }
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            TestServer.server.stop()
        }

        @JvmStatic
        fun `given a did String, when calling resolve, then the result is a valid did document`(): Stream<Arguments> =
            Stream.concat(
                testData(secp256DidAssertions, ed25519DidAssertions, rsaDidAssertions),
                Stream.of(
                    // Test multi-key DID document
                    arguments(
                        "did:web:localhost%3A${TestServer.DID_WEB_SSL_PORT}:multi-key",
                        // Doesn't matter which key we specify here, we're just testing that the document is resolved
                        Json.decodeFromString<JsonObject>("{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"kid\":\"key1\",\"kty\":\"OKP\",\"use\":\"sig\",\"x\":\"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM\"}"),
                        { did: String, key: JsonObject, result: Result<DidDocument> ->
                            // Ensure we got a successful result
                            assertTrue(result.isSuccess)

                            // Get the document
                            val doc = result.getOrThrow()

                            // Verify document contains verification methods
                            val hasVerificationMethods = doc.get("verificationMethod") != null
                            assertTrue(hasVerificationMethods) { "No verification methods found in document" }
                        }
                    )
                )
            )

        @JvmStatic
        fun `given a did String, when calling resolveToKey, then the result is valid key`(): Stream<Arguments> =
            Stream.concat(
                testData(secp256KeyAssertions, ed25519KeyAssertions, rsaKeyAssertions),
                Stream.of(
                    // Test that resolveToKey returns just one key from a multi-key DID
                    arguments(
                        "did:web:localhost%3A${TestServer.DID_WEB_SSL_PORT}:multi-key",
                        // We use Ed25519 key details, but the actual returned key might be any of the three - we'll accept any
                        Json.decodeFromString<JsonObject>("{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"kid\":\"key1\",\"kty\":\"OKP\",\"use\":\"sig\",\"x\":\"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM\"}"),
                        { did: String, key: JsonObject, result: Result<Key> ->
                            // Just check that we got some key successfully
                            assertTrue(result.isSuccess)
                            result.getOrThrow()

                            // Just having a non-null key is sufficient for this test
                            result.getOrThrow()
                        }
                    )
                )
            )

        @JvmStatic
        fun `given a did String, when calling resolveToKeys, then the result is valid keys set`(): Stream<Arguments> =
            Stream.concat(
                testData(secp256KeysSetAssertions, ed25519KeysSetAssertions, rsaKeysSetAssertions),
                Stream.of(
                    // Multi-key test with all three key types
                    arguments(
                        "did:web:localhost%3A${TestServer.DID_WEB_SSL_PORT}:multi-key",
                        // We'll use the Ed25519 key for the test verification
                        Json.decodeFromString<JsonObject>("{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"kid\":\"key1\",\"kty\":\"OKP\",\"use\":\"sig\",\"x\":\"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM\"}"),
                        { did: String, key: JsonObject, result: Result<Set<Key>> ->
                            // Ensure we got a successful result with multiple keys
                            assertTrue(result.isSuccess)
                            val keys = result.getOrThrow()

                            // Test that we got exactly 3 keys
                            assertTrue(keys.size == 3) { "Expected 3 keys, got ${keys.size}" }
                        }
                    )
                )
            )

        private fun <T> testData(
            secpAssertions: resolverAssertion<T>,
            ed25519Assertions: resolverAssertion<T>,
            rsaAssertions: resolverAssertion<T>,
        ) =
            Stream.of(
                //secp256k1
                arguments(
                    "did:web:localhost%3A${TestServer.DID_WEB_SSL_PORT}:secp256k1",
                    Json.decodeFromString<JsonObject>("{\"alg\":\"ES256K\",\"crv\":\"secp256k1\",\"kid\":\"d5c5048d0e7440a4830ea0b407174f83\",\"kty\":\"EC\",\"use\":\"sig\",\"x\":\"eKx_FaLzPMT4ndwvImdV_pTv-JX1SQpJ8tDK6GLiYIE\",\"y\":\"-TzpGxGLPnXWMJWTqYvqn55Z8Xi-J_ZM40bjtjGaELs\"}"),
                    secpAssertions
                ),
                //secp256r1
                arguments(
                    "did:web:localhost%3A${TestServer.DID_WEB_SSL_PORT}:secp256r1",
                    Json.decodeFromString<JsonObject>("{\"alg\":\"ES256\",\"crv\":\"P-256\",\"kid\":\"292dfabd1f9d477eb5cef239909111a1\",\"kty\":\"EC\",\"use\":\"sig\",\"x\":\"NN9jDT3CL9yxgYEjaEkP80CB8q1WBbUHIlVuPIBQhi8\",\"y\":\"op40OaekSUJynfo1hClZWu8SAYMTf1OcaCUr0YErNSc\"}"),
                    secpAssertions
                ),
                //ed25519
                arguments(
                    "did:web:localhost%3A${TestServer.DID_WEB_SSL_PORT}:ed25519",
                    Json.decodeFromString<JsonObject>("{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"kid\":\"151df6ec01714883b812f26f2d63e584\",\"kty\":\"OKP\",\"use\":\"sig\",\"x\":\"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM\"}"),
                    ed25519Assertions
                ),
                //rsa
                arguments(
                    "did:web:localhost%3A${TestServer.DID_WEB_SSL_PORT}:rsa",
                    Json.decodeFromString<JsonObject>("{\"alg\":\"RS256\",\"e\":\"AQAB\",\"kid\":\"ab269ce10ce94b7c9565e30c034b5692\",\"kty\":\"RSA\",\"n\":\"0qbslQ5uMXL1Wk4dUD5ftrGWLhgaQENQn8AaPVREg12H_Mfr2GEL0IkBd7EQPeRFzRzngF2kWpij_nyueYKGQ3um_hione72pozP76etXNk4imTzmg3RsHcfPC5JBJAGpb5htnUQ5-VsuqbzlCUTOWNK4kIDWzbU0o-neglLAwU846_h6lTRI7xE1kh0iZyseAdx7sZ8Cd5eSYuvwQVxnNn0w-m9Bwd30g-s8xmqn9-7LBa0-UdumMLwtan4IGXltMJGYU9br1wsmz9vlG-TvfmxlgXzilJOJQMvlMKGXRmbUJRaNSYdrVJciEQEWK0tkaT45r3_LJw7dwx4DnNxzw\",\"use\":\"sig\"}"),
                    rsaAssertions
                ),
            )
    }
}
