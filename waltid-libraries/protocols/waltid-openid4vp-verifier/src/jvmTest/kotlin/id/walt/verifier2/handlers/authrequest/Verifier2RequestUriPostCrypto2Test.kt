package id.walt.verifier2.handlers.authrequest

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.did.dids.DidService
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreator
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class Verifier2RequestUriPostCrypto2Test {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val migration = V1KeyMigration()

    @Test
    fun `signed request URI POST returns a nonce-bound request object`() = runTest {
        DidService.minimalInit()
        val verifierKey = JWKKey.generate(KeyType.secp256r1)
        val did = DidService.registerByKey("jwk", verifierKey).did
        val clientId = "decentralized_identifier:$did"
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    signedRequest = true,
                    clientId = clientId,
                    key = DirectSerializedKey(verifierKey),
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = NoMeta)
                        )
                    )
                )
            ),
            clientId = clientId,
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
            key = verifierKey,
        )

        val bootstrapUrl = assertNotNull(session.bootstrapAuthorizationRequestUrl)
        assertEquals("post", bootstrapUrl.parameters["request_uri_method"])
        val originalRequestObject = assertNotNull(session.signedAuthorizationRequestJwt)
        val originalKid = assertNotNull(
            CompactJws.decodeUnverified(originalRequestObject).protectedHeader["kid"]?.jsonPrimitive?.content
        )

        testApplication {
            application {
                routing {
                    post("/request") {
                        Verifier2RequestUriPostHandler.run {
                            call.respondRequestUriPost(
                                verificationSession = session,
                                updateSessionCallback = { current, _, block -> current.apply(block) },
                            )
                        }
                    }
                }
            }

            val response = client.post("/request") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("wallet_nonce=wallet-nonce&wallet_metadata=%7B%7D")
            }

            assertEquals(200, response.status.value)
            assertEquals("application/oauth-authz-req+jwt", response.contentType()?.withoutParameters()?.toString())
            val jwt = response.bodyAsText()
            val decoded = CompactJws.decodeUnverified(jwt)
            val kid = assertNotNull(decoded.protectedHeader["kid"]?.jsonPrimitive?.content)
            assertEquals(originalKid, kid)
            assertEquals(Verifier2RequestObjectKid.forClient(clientId, verifierKey), kid)
            verifierKey.getPublicKey().verifyJws(jwt).getOrThrow()
            val payload = Json.parseToJsonElement(decoded.payload.decodeToString()).jsonObject
            assertEquals("wallet-nonce", payload["wallet_nonce"]?.jsonPrimitive?.content)
        }

        assertEquals(Verification2Session.VerificationSessionStatus.IN_USE, session.status)
        assertNotNull(session.signedAuthorizationRequestJwt)
    }

    @Test
    fun `direct crypto2 re-sign verifies existing JAR and preserves protected headers`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val crypto2Key = runtime.restore(
            migration.migrate(
                recordId = KeyId(legacyKey.getKeyId()),
                serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val headers = buildJsonObject {
            put("alg", "ES256")
            put("typ", "oauth-authz-req+jwt")
            put("kid", legacyKey.getKeyId())
            put("x5c", JsonArray(listOf(JsonPrimitive("certificate"))))
        }
        val original = Verifier2RequestUriPostHandler.signRequestObject(
            crypto2Key,
            buildJsonObject { put("aud", "https://self-issued.me/v2") },
            headers,
        )
        Verifier2RequestUriPostHandler.verifyExistingRequestObject(original, crypto2Key)
        val resigned = Verifier2RequestUriPostHandler.signRequestObject(
            crypto2Key,
            buildJsonObject { put("wallet_nonce", "nonce") },
            CompactJws.decodeUnverified(original).protectedHeader,
        )
        val decoded = CompactJws.decodeUnverified(resigned)

        assertEquals("oauth-authz-req+jwt", decoded.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("certificate", decoded.protectedHeader["x5c"]?.let { (it as JsonArray).single().jsonPrimitive.content })

        val replacement = JWKKey.generate(KeyType.secp256r1)
        val replacementWithSameId = runtime.restore(
            migration.migrate(
                recordId = KeyId(legacyKey.getKeyId()),
                serialized = KeySerialization.serializeKeyToJson(replacement).jsonObject,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        assertFailsWith<IllegalArgumentException> {
            Verifier2RequestUriPostHandler.verifyExistingRequestObject(original, replacementWithSameId)
        }
    }

    @Test
    fun `local JWK request object re-signs through crypto2`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val token = Verifier2RequestUriPostHandler.signRequestObject(
            signingKey = legacyKey,
            payload = buildJsonObject { put("wallet_nonce", "nonce") },
            headers = buildJsonObject {
                put("alg", "ES256")
                put("typ", "oauth-authz-req+jwt")
                put("kid", legacyKey.getKeyId())
            },
        )
        val publicKey = legacyKey.getPublicKey()
        val verificationStoredKey = V1KeyMigration().migrate(
            recordId = KeyId(publicKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(publicKey).jsonObject,
            usages = setOf(KeyUsage.VERIFY),
        )
        val verificationKey = CryptoRuntime(defaultSoftwareKeyProviders()).restore(verificationStoredKey)
        val verified = CompactJws.verify(token, verificationKey, JwsAlgorithm.ES256)

        assertEquals("oauth-authz-req+jwt", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals(
            "nonce",
            Json.parseToJsonElement(verified.payload.decodeToString())
                .jsonObject["wallet_nonce"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `public local JWK cannot re-sign request object`() = runTest {
        val publicKey = JWKKey.generate(KeyType.secp256r1).getPublicKey()

        assertFailsWith<IllegalArgumentException> {
            Verifier2RequestUriPostHandler.signRequestObject(
                signingKey = publicKey,
                payload = buildJsonObject { put("wallet_nonce", "nonce") },
                headers = buildJsonObject {
                    put("alg", "ES256")
                    put("kid", publicKey.getKeyId())
                },
            )
        }
    }

    @Test
    fun `existing request signature rejects replacement key with same kid`() = runTest {
        val originalKey = JWKKey.generate(KeyType.secp256r1)
        val originalToken = originalKey.signJws(
            plaintext = buildJsonObject { put("aud", "https://self-issued.me/v2") }.toString().encodeToByteArray(),
            headers = mapOf(
                "alg" to JsonPrimitive("ES256"),
                "kid" to JsonPrimitive(originalKey.getKeyId()),
            ),
        )
        Verifier2RequestUriPostHandler.verifyExistingRequestObject(originalToken, originalKey)
        val replacement = JWKKey.generate(KeyType.secp256r1)
        val replacementWithSameKid = JWKKey.importJWK(
            JsonObject(replacement.exportJWKObject() + ("kid" to JsonPrimitive(originalKey.getKeyId()))).toString()
        ).getOrThrow()

        assertFailsWith<IllegalArgumentException> {
            Verifier2RequestUriPostHandler.verifyExistingRequestObject(originalToken, replacementWithSameKid)
        }
    }
}
