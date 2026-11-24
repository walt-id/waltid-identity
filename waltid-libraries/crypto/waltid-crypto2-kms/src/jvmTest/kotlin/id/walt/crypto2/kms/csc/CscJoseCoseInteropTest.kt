package id.walt.crypto2.kms.csc

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.createAndSign
import id.walt.cose.verify
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CscJoseCoseInteropTest {
    @Test
    fun `CSC key signs compact JWS and COSE Sign1 that an independent verifier accepts`() = runTest {
        val rssp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val rsspPublicKey = CryptographySoftwareKeyProvider().restore(
            StoredKey.Software(
                version = StoredKey.CURRENT_VERSION,
                id = KeyId("rssp-public-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.VERIFY),
                material = EncodedKey.SpkiDer(BinaryData(rssp.public.encoded)),
            )
        )
        val stub = CscStub(testChain(rsspPublicKey, ca = TestRsspKey.generate().key))
        // A JDK "RSSP" that signs the raw hash and returns DER, as many CSC services do.
        stub.signatureFor = { hash ->
            Signature.getInstance("NONEwithECDSA").run {
                initSign(rssp.private)
                update(hash)
                sign()
            }
        }
        val provider = CscKeyProvider(
            client = stub.client,
            credentialResolver = { CscCredentials(CLIENT_ID, CLIENT_SECRET, mapOf("PIN" to PIN)) },
            now = { NOW },
        )
        val key = provider.restore(
            provider.storedKeyForExisting(
                KeyId("qeaa-seal"),
                CscOptions("https://rssp.example/csc/v2", CredentialReference("qtsp-seal")),
                CREDENTIAL_ID,
            )
        )

        val jws = CompactJws.sign("jose".encodeToByteArray(), key, JwsAlgorithm.ES256)
        assertEquals("jose", CompactJws.verify(jws, key, JwsAlgorithm.ES256).payload.decodeToString())
        val (header, payload, signature) = jws.split('.')
        val jdk = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(rssp.public)
            update("$header.$payload".encodeToByteArray())
        }
        val p1363 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(signature)
        assertTrue(jdk.verify(EcdsaSignatureCodec.p1363ToDer(p1363, 32)))

        val cose = CoseSign1.createAndSign(
            CoseHeaders(algorithm = Cose.Algorithm.ES256),
            payload = "cose".encodeToByteArray(),
            key = key,
        )
        assertTrue(cose.verify(key, Cose.Algorithm.ES256))
        assertEquals(2, stub.count(SIGN_HASH_PATH))
    }
}
