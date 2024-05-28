import TestUtils.loadJwkLocal
import TestUtils.loadPemLocal
import TestUtils.loadResourceBase64
import TestUtils.loadResourceBytes
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportJWKKeyTestsAndDidManagement {

    @ParameterizedTest
    @MethodSource
    fun `given jwk string, when imported then the import succeeds having the correct key type, key id and hasPrivate values`(
        keyString: String, keyType: KeyType, isPrivate: Boolean
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

    @ParameterizedTest
    @MethodSource
    fun `given pem string, when imported then the import succeeds having the correct key type, key id and hasPrivate values`(
        keyString: String, keyType: KeyType, isPrivate: Boolean
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

    @ParameterizedTest
    @MethodSource
    fun `given raw string, when imported then the import succeeds having the correct key type, key id and hasPrivate values`(
        bytes: ByteArray, keyType: KeyType, isPrivate: Boolean
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


    companion object {
        @JvmStatic
        fun `given jwk string, when imported then the import succeeds having the correct key type, key id and hasPrivate values`(): Stream<Arguments> =
            Stream.of(
                // ed25519
                arguments(loadJwkLocal("ed25519.private.json"), KeyType.Ed25519, true),
                // secp256k1
                arguments(loadJwkLocal("secp256k1.private.json"), KeyType.secp256k1, true),
                // secp256r1
                arguments(loadJwkLocal("secp256r1.private.json"), KeyType.secp256r1, true),
                // rsa
                arguments(loadJwkLocal("rsa.private.json"), KeyType.RSA, true),
                // public
                // ed25519
                arguments(loadJwkLocal("ed25519.public.json"), KeyType.Ed25519, false),
                // secp256k1
                arguments(loadJwkLocal("secp256k1.public.json"), KeyType.secp256k1, false),
                // secp256r1
                arguments(loadJwkLocal("secp256r1.public.json"), KeyType.secp256r1, false),
                // rsa
                arguments(loadJwkLocal("rsa.public.json"), KeyType.RSA, false),
            )

        @JvmStatic
        fun `given pem string, when imported then the import succeeds having the correct key type, key id and hasPrivate values`(): Stream<Arguments> =
            Stream.of(
                // ed25519 (not implemented)
                //arguments(loadPemLocal("ed25519.private.pem"), KeyType.Ed25519, true),
                // secp256k1
                arguments(loadPemLocal("secp256k1.private.pem"), KeyType.secp256k1, true),
                // secp256r1
                arguments(loadPemLocal("secp256r1.private.pem"), KeyType.secp256r1, true),
                // rsa
                arguments(loadPemLocal("rsa.private.pem"), KeyType.RSA, true),
                // public
                // ed25519 (not implemented)
                //arguments(loadPemLocal("ed25519.public.pem"), KeyType.Ed25519, false),
                // secp256k1
                arguments(loadPemLocal("secp256k1.public.pem"), KeyType.secp256k1, false),
                // secp256r1
                arguments(loadPemLocal("secp256r1.public.pem"), KeyType.secp256r1, false),
                // rsa
                arguments(loadPemLocal("rsa.public.pem"), KeyType.RSA, false),
                // rsa (exported from Hashicorp Vault)
                arguments(loadPemLocal("rsa-vault.public.pem"), KeyType.RSA, false),
                // ECDSA P-256 / Secp256r1 (exported from Hashicorp Vault)
                arguments(loadPemLocal("ecdsa-vault.public.pem"), KeyType.secp256r1, false),
            )

        @JvmStatic
        fun `given raw string, when imported then the import succeeds having the correct key type, key id and hasPrivate values`(): Stream<Arguments> =
            Stream.of(
                arguments(loadResourceBytes("public-bytes/ed25519.bin"), KeyType.Ed25519, false),

                arguments(loadResourceBase64("public-bytes/ed25519-vault.base64"), KeyType.Ed25519, false),

                // secp256r1 (throwing Invalid point encoding 0x30)
//                arguments(loadResourceBytes("public-bytes/secp256k1.bin"), KeyType.secp256k1, false),
                // secp256r1 (throwing Invalid point encoding 0x30)
//                arguments(loadResourceBytes("public-bytes/secp256r1.bin"), KeyType.secp256r1, false),
                // rsa (not implemented)
//                arguments(loadResourceBytes("public-bytes/rsa.bin"), KeyType.RSA, false),
            )

    }
}
