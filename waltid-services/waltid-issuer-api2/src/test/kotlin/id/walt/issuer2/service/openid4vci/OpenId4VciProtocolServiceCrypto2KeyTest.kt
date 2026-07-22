package id.walt.issuer2.service.openid4vci

import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.issuer2.domain.IssuanceSession
import id.walt.openid4vci.offers.AuthenticationMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class OpenId4VciProtocolServiceCrypto2KeyTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `JWK session without validated sidecar cannot downgrade to v1`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            restoreSessionIssuerCrypto2Key(session(), runtime)
        }
    }

    @Test
    fun `non-JWK managed session retains provider compatibility fallback`() = runTest {
        assertNull(
            restoreSessionIssuerCrypto2Key(
                session().copy(issuerKey = JsonObject(mapOf("type" to JsonPrimitive("managed-provider")))),
                runtime,
            )
        )
    }

    private suspend fun session() = IssuanceSession(
        sessionId = "session",
        profileId = "profile",
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        credentialConfigurationId = "identity_credential",
        issuerKey = KeySerialization.serializeKeyToJson(JWKKey.generate(KeyType.secp256r1)).jsonObject,
        credentialData = buildJsonObject {},
        expiresAt = Clock.System.now() + 5.minutes,
    )
}
