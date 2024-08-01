import TestUtils.loadJwkLocal
import TestUtils.loadPemLocal
import TestUtils.loadResourceBase64
import TestUtils.loadResourceBytes
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportJWKKeyTestsAndDidManagement {

    private fun testJwkImport(
        keyString: String, keyType: KeyType, isPrivate: Boolean,
    ) = runTest {
        // Importing
        val imported = JWKKey.importJWK(keyString)
        // Checking import success
        assertTrue { imported.isSuccess }
        // Getting key
        val key = imported.getOrThrow()
        // Checking for private key
        assertEquals(isPrivate, key.hasPrivateKey)
        // Checking for key type
        assertEquals(keyType, key.keyType)
        // Checking keyId from thumbprint
        assertEquals(key.getThumbprint(), key.getKeyId())
    }

    private fun testPemImport(
        keyString: String, keyType: KeyType, isPrivate: Boolean,
    ) = runTest {
        println("> Importing supposed $keyType (${if (isPrivate) "private" else "public"}) as PEM:")

        // Importing
        val imported = JWKKey.importPEM(keyString)
        // Checking import success
        assertTrue("Import of ${if (isPrivate) "private" else "public"} $keyType: ${imported.exceptionOrNull()}") { imported.isSuccess }
        // Getting key
        val key = imported.getOrThrow()
        // Checking for private key
        assertEquals(isPrivate, key.hasPrivateKey)
        // Checking for key type
        assertEquals(keyType, key.keyType)
        // Checking keyId from thumbprint
        assertEquals(key.getThumbprint(), key.getKeyId())

        println("All checks succeeded for: $keyType (${if (isPrivate) "private" else "public"})")
    }

    private fun testRawImport(
        bytes: ByteArray, keyType: KeyType, isPrivate: Boolean,
    ) = runTest {
        // Importing
        val key = JWKKey.importRawPublicKey(keyType, bytes, null)
        // Checking for private key
        assertEquals(isPrivate, key.hasPrivateKey)
        // Checking for key type
        assertEquals(keyType, key.keyType)
        // Checking keyId from thumbprint
        assertEquals(key.getThumbprint(), key.getKeyId())

        println(key.exportJWK())
    }

    @Test
    fun testJwkEd25519PrivateImport() = testJwkImport(loadJwkLocal("ed25519.private.json"), KeyType.Ed25519, true)
    @Test
    fun testJwkSecp256k1PrivateImport() = testJwkImport(loadJwkLocal("secp256k1.private.json"), KeyType.secp256k1, true)
    @Test
    fun testJwkSecp256r1PrivateImport() = testJwkImport(loadJwkLocal("secp256r1.private.json"), KeyType.secp256r1, true)
    @Test
    fun testJwkRsaPrivateImport() = testJwkImport(loadJwkLocal("rsa.private.json"), KeyType.RSA, true)

    @Test
    fun testJwkEd25519PublicImport() = testJwkImport(loadJwkLocal("ed25519.public.json"), KeyType.Ed25519, false)
    @Test
    fun testJwkSecp256k1PublicImport() = testJwkImport(loadJwkLocal("secp256k1.public.json"), KeyType.secp256k1, false)
    @Test
    fun testJwkSecp256r1PublicImport() = testJwkImport(loadJwkLocal("secp256r1.public.json"), KeyType.secp256r1, false)
    @Test
    fun testJwkRsaPublicImport() = testJwkImport(loadJwkLocal("rsa.public.json"), KeyType.RSA, false)

    @Test
    fun testPemSecp256k1PrivateImport() = testPemImport(loadPemLocal("secp256k1.private.pem"), KeyType.secp256k1, true)
    @Test
    fun testPemSecp256r1PrivateImport() = testPemImport(loadPemLocal("secp256r1.private.pem"), KeyType.secp256r1, true)
    @Test
    fun testPemRsaPrivateImport() = testPemImport(loadPemLocal("rsa.private.pem"), KeyType.RSA, true)

    @Test
    fun testPemSecp256k1PublicImport() = testPemImport(loadPemLocal("secp256k1.public.pem"), KeyType.secp256k1, false)
    @Test
    fun testPemSecp256r1PublicImport() = testPemImport(loadPemLocal("secp256r1.public.pem"), KeyType.secp256r1, false)
    @Test
    fun testPemRsaPublicImport() = testPemImport(loadPemLocal("rsa.public.pem"), KeyType.RSA, false)
    @Test
    fun testPemRsaVaultPublicImport() = testPemImport(loadPemLocal("rsa-vault.public.pem"), KeyType.RSA, false)
    @Test
    fun testPemEcdsaVaultPublicImport() = testPemImport(loadPemLocal("ecdsa-vault.public.pem"), KeyType.secp256r1, false)

    @Test
    fun testRawEd25519PublicImport() = testRawImport(loadResourceBytes("public-bytes/ed25519.bin"), KeyType.Ed25519, false)
    @Test
    fun testRawEd25519VaultPublicImport() = testRawImport(loadResourceBase64("public-bytes/ed25519-vault.base64"), KeyType.Ed25519, false)
    @Test
    fun testRawSecp256k1PublicImport() = testRawImport(loadResourceBytes("public-bytes/secp256k1.bin"), KeyType.secp256k1, false)
    @Test
    fun testRawSecp256r1PublicImport() = testRawImport(loadResourceBytes("public-bytes/secp256r1.bin"), KeyType.secp256r1, false)
    @Test
    fun testRawRsaPublicImport() = testRawImport(loadResourceBytes("public-bytes/rsa.bin"), KeyType.RSA, false)

}
