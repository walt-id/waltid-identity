package id.walt.cose

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CoseCrypto2VectorTest {
    @Test
    fun `crypto2 verifies COSE working-group ES256 vector`() = runTest {
        val publicJwk = """{"kty":"EC","crv":"P-256","x":"usWxHK2PmfnHKwXPS54m0kTcGJ90UiglWiGahtagnv8","y":"IBOL-C3BttVivg-lSreASjpkttcsz-1rb7btKLv8EX4"}"""
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val key = runtime.restore(
            StoredKey.Software(
                version = StoredKey.CURRENT_VERSION,
                id = KeyId("cose-vector-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.VERIFY),
                material = EncodedKey.Jwk(BinaryData(publicJwk.encodeToByteArray()), privateMaterial = false),
            ),
        )
        val message = CoseSign1.fromTagged(
            "d28443a10126a10442313154546869732069732074686520636f6e74656e742e58403a7487d9a528cb61dd8e99bd652c12577fc47d70ee5af2e703c420584f060fc7a8d61e4a35862b2b531a8447030ab966aeed8dd45ebc507c761431e349995770",
        )

        assertTrue(
            message.verify(
                key = key,
                expectedAlgorithm = Cose.Algorithm.ES256,
                externalAad = "11aa22bb33cc44dd55006699".hexToByteArray(),
            ),
        )
    }
}
