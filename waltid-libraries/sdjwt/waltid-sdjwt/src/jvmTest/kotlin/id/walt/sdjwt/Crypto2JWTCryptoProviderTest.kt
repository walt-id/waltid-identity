package id.walt.sdjwt

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

class Crypto2JWTCryptoProviderTest {
    @Test
    fun `synchronous provider delegates crypto2 signing`() {
        val key = runBlocking {
            CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("sync-key"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
            )
        }
        val provider = Crypto2JWTCryptoProvider(mapOf("sync-key" to Crypto2SdJwtKey(key, JwsAlgorithm.ES256)))
        val jwt = provider.sign(buildJsonObject {}, "sync-key", "vc+sd-jwt", emptyMap())

        assertTrue(provider.verify(jwt, "sync-key").verified)
    }
}
