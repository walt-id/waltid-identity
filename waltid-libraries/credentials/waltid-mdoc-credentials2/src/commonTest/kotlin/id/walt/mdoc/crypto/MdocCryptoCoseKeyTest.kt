package id.walt.mdoc.crypto

import id.walt.cose.Cose
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MdocCryptoCoseKeyTest {
    @Test
    fun `verification key rejects a mismatched COSE algorithm`() = runTest {
        val coseKey = publicCoseKey()

        assertFailsWith<IllegalArgumentException> {
            MdocCrypto.coseKeyToCrypto2Key(
                coseKey.copy(alg = Cose.Algorithm.ES384),
                Cose.Algorithm.ES256,
            )
        }
    }

    @Test
    fun `verification key requires the verify operation`() = runTest {
        val coseKey = publicCoseKey()

        assertFailsWith<IllegalArgumentException> {
            MdocCrypto.coseKeyToCrypto2Key(
                coseKey.copy(key_ops = emptyList()),
                Cose.Algorithm.ES256,
            )
        }
    }

    private suspend fun publicCoseKey() = CryptoRuntime(defaultSoftwareKeyProviders())
        .generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("mdoc-verification-guard"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        .capabilities.publicKeyExporter!!
        .exportPublicKey()
        .let { it as EncodedKey.Jwk }
        .toCoseKey()
}
