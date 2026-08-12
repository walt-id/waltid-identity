package id.walt.crypto2.jose

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.jwk.ECKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NimbusJweInteropTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `crypto2 decrypts Nimbus ECDH-ES A128GCM`() = runTest {
        val recipient = recipientKey()
        val nimbusKey = recipient.toNimbusJwk()
        val jwe = JWEObject(
            JWEHeader(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM),
            Payload("nimbus-jwe"),
        )
        jwe.encrypt(ECDHEncrypter(nimbusKey.toPublicJWK()))

        val decrypted = CompactJwe.decrypt(jwe.serialize(), recipient, setOf(JweContentEncryption.A128GCM))

        assertEquals("nimbus-jwe", decrypted.plaintext.decodeToString())
    }

    @Test
    fun `Nimbus decrypts crypto2 ECDH-ES A256GCM`() = runTest {
        val recipient = recipientKey()
        val compact = CompactJwe.encrypt(
            plaintext = "crypto2-jwe".encodeToByteArray(),
            recipientPublicKey = recipient.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk,
            contentEncryption = JweContentEncryption.A256GCM,
        )
        val nimbus = JWEObject.parse(compact)
        nimbus.decrypt(ECDHDecrypter(recipient.toNimbusJwk()))

        assertContentEquals("crypto2-jwe".encodeToByteArray(), nimbus.payload.toBytes())
    }

    private suspend fun recipientKey(): SoftwareKey = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("jwe-interop-key"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.KEY_AGREEMENT),
        ),
    )

    private fun SoftwareKey.toNimbusJwk(): ECKey {
        val material = storedKey.material as EncodedKey.Jwk
        return ECKey.parse(material.data.toByteArray().decodeToString())
    }
}
