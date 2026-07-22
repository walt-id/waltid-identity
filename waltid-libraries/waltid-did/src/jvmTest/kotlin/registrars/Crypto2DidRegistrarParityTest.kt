package registrars

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Crypto2DidRegistrarParityTest {
    @Test
    fun `crypto2 default did key identifiers match v1 output`() = runTest {
        assertV1Parity(KeySpec.Edwards(EdwardsCurve.ED25519), KeyType.Ed25519, useJwkJcsPub = false)
        assertV1Parity(KeySpec.Ec(EcCurve.P256), KeyType.secp256r1, useJwkJcsPub = false)
        assertV1Parity(KeySpec.Rsa(2048), KeyType.RSA, useJwkJcsPub = false)
    }

    @Test
    fun `crypto2 JWK JCS did key identifiers match v1 output`() = runTest {
        assertV1Parity(KeySpec.Edwards(EdwardsCurve.ED25519), KeyType.Ed25519, useJwkJcsPub = true)
        assertV1Parity(KeySpec.Ec(EcCurve.P256), KeyType.secp256r1, useJwkJcsPub = true)
        assertV1Parity(KeySpec.Rsa(2048), KeyType.RSA, useJwkJcsPub = true)
    }

    private suspend fun assertV1Parity(spec: KeySpec, keyType: KeyType, useJwkJcsPub: Boolean) {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val crypto2Key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("parity-${keyType.name}-$useJwkJcsPub"),
                spec = spec,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val publicJwk = crypto2Key.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk
        val v1Key = JWKKey.importJWK(publicJwk.data.toByteArray().decodeToString()).getOrThrow()
        val options = DidKeyCreateOptions(keyType, useJwkJcsPub)

        val expected = DidKeyRegistrar().registerByKey(v1Key, options)
        val actual = DidService.registerByKey("key", crypto2Key, options)

        assertEquals(expected.did, actual.did)
    }
}
