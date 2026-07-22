package id.walt.sdjwt

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Crypto2AsyncJWTCryptoProviderTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `sign verify tamper and requested key selection`() = runTest {
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("key-id"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val provider = Crypto2AsyncJWTCryptoProvider(
            mapOf("alias" to Crypto2SdJwtKey(key, JwsAlgorithm.ES256, "public-kid"))
        )
        val jwt = provider.sign(
            payload = buildJsonObject { put("sub", "subject") },
            keyID = "alias",
            typ = "vc+sd-jwt",
            headers = emptyMap(),
        )

        assertTrue(provider.verify(jwt).verified)
        assertTrue(provider.verify(jwt, "alias").verified)
        assertTrue(provider.verify(jwt, "public-kid").verified)
        val parts = jwt.split('.').toMutableList().apply {
            this[2] = (if (this[2].first() == 'A') "B" else "A") + this[2].drop(1)
        }
        assertFalse(provider.verify(parts.joinToString(".")).verified)
        assertFalse(provider.verify(jwt, "missing").verified)
    }

    @Test
    fun `missing selection and conflicting kid fail signing`() = runTest {
        val keys = listOf("one", "two").associateWith { id ->
            val key = runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId(id),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
            )
            Crypto2SdJwtKey(key, JwsAlgorithm.ES256)
        }
        val provider = Crypto2AsyncJWTCryptoProvider(keys)
        assertFailsWith<IllegalArgumentException> {
            provider.sign(buildJsonObject {}, null, "vc+sd-jwt", emptyMap())
        }
        assertFailsWith<IllegalArgumentException> {
            provider.sign(
                buildJsonObject {},
                "one",
                "vc+sd-jwt",
                mapOf("kid" to "conflict"),
            )
        }
    }
}
