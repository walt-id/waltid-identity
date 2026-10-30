package id.walt.issuer2.openid4vci

import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.testsupport.*
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JweUtils
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class DeferredInitialResponseRecoveryTest {
    @AfterEach fun clear() = clearIssuer2TestEnvironment()

    @Test fun correctedInitialResponseEncryptionCanRetryCredentialRequest() = testApplication {
        val key = JWKKey.generate(KeyType.secp256r1)
        val serializedKey = KeySerialization.serializeKey(key)
        installIssuer2WithConfigFiles(configureServiceConfig = { it.copy(credentialEncryptionKey = serializedKey) })
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client)
        val offer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_DEFERRED_OFFER)
        val resolved = wallet.resolve(offer)
        val token = wallet.exchangePreAuthorizedCode(resolved, null)
        val configuration = resolved.offer.credentialConfigurationIds.single()
        val firstPayload = credentialRequest(configuration, wallet.buildJwtProofs(resolved.issuerMetadata, configuration))
        val badEncryption = buildJsonObject {
            putJsonObject("jwk") {
                put("kty", "EC"); put("crv", "P-256"); put("x", "AA"); put("y", "AA"); put("alg", "ECDH-ES"); put("use", "enc")
            }
            put("enc", "A128GCM")
        }
        val encrypted = JweUtils.toJWE(
            JsonObject(firstPayload + ("credential_response_encryption" to badEncryption)), key.getPublicKey().exportJWK(), "ECDH-ES", "A128GCM",
            headerParams = mapOf("kid" to JsonPrimitive(key.getKeyId())),
        )
        val rejected = client.post(resolved.issuerMetadata.credentialEndpoint) {
            bearerAuth(token.access_token); contentType(ContentType.parse("application/jwt")); setBody(encrypted)
        }
        assertEquals(HttpStatusCode.BadRequest, rejected.status, rejected.bodyAsText())
        assertEquals("invalid_encryption_parameters", rejected.body<JsonObject>()["error"]?.jsonPrimitive?.content)
        val correctedProof = wallet.buildJwtProofs(resolved.issuerMetadata, configuration)
        val corrected = client.post(resolved.issuerMetadata.credentialEndpoint) {
            bearerAuth(token.access_token); contentType(ContentType.Application.Json)
            setBody(credentialRequest(configuration, correctedProof))
        }
        assertEquals(HttpStatusCode.Accepted, corrected.status, "Corrected request should start deferral: ${corrected.bodyAsText()}")
    }
}
