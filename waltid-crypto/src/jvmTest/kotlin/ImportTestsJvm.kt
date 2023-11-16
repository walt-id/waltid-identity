import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportTestsJvm {

    @ParameterizedTest
    @MethodSource
    fun `given jwk string, when imported then the import succeeds having the correct key type, key id and hasPrivate values`(
        keyString: String, keyType: KeyType, isPrivate: Boolean
    ) = runTest {
        // Importing
        val imported = LocalKey.importJWK(keyString)
        // Checking import success
        assertTrue { imported.isSuccess }
        // Getting key
        val key = imported.getOrThrow()
        // Checking for private key
        assertEquals(isPrivate, key.hasPrivateKey)
        // Checking for key type
        assertEquals(keyType, key.keyType)
//        println("  Checking keyId from thumbprint...")
        assertEquals(key.getThumbprint(), key.getKeyId())
        //TODO: remove
//        println("  Checking JWK export matches input...")
//        assertEquals(
//            Json.parseToJsonElement(keyString), Json.parseToJsonElement(key.exportJWK())
//        )
    }

    @ParameterizedTest
    @MethodSource
    fun `given pem string, when imported then the import succeeds having the correct key type, key id and hasPrivate values`(
        keyString: String, keyType: KeyType, isPrivate: Boolean
    ) = runTest {
        // Importing
        val imported = LocalKey.importPEM(keyString)
        // Checking import success
        assertTrue { imported.isSuccess }
        // Getting key
        val key = imported.getOrThrow()
        // Checking for private key
        assertEquals(isPrivate, key.hasPrivateKey)
        // Checking for key type
        assertEquals(keyType, key.keyType)
        //TODO: remove
//        println("  Checking JWK export matches input...")
//        assertEquals(
//            Json.parseToJsonElement(keyString).jsonObject.filterNot { it.key == "kid" },
//            Json.parseToJsonElement(key.exportJWK()).jsonObject
//        )
    }

    companion object {
        @JvmStatic
        fun `given jwk string, when imported then the import succeeds having the correct key type, key id and hasPrivate values`(): Stream<Arguments> =
            Stream.of(
                // ed25519
                arguments(loadJwk("ed25519.public.json"), KeyType.Ed25519, false),
                // secp256k1
                arguments(loadJwk("secp256k1.public.json"), KeyType.secp256k1, false),
                // secp256r1
                arguments(loadJwk("secp256r1.public.json"), KeyType.secp256r1, false),
                // rsa
                arguments(loadJwk("rsa.public.json"), KeyType.RSA, false),
                // ---private keys---
                // ed25519
                arguments(loadJwk("ed25519.private.json"), KeyType.Ed25519, true),
                // secp256k1
                arguments(loadJwk("secp256k1.private.json"), KeyType.secp256k1, true),
                // secp256r1
                arguments(loadJwk("secp256r1.private.json"), KeyType.secp256r1, true),
                // rsa
                arguments(loadJwk("rsa.private.json"), KeyType.RSA, true),
            )

        @JvmStatic
        fun `given pem string, when imported then the import succeeds having the correct key type, key id and hasPrivate values`(): Stream<Arguments> =
            Stream.of(
                // ed25519 (not supported)
//                arguments(loadPem("ed25519.public.pem"), KeyType.Ed25519, false),
                // secp256k1
                arguments(loadPem("secp256k1.public.pem"), KeyType.secp256k1, false),
                // secp256r1
                arguments(loadPem("secp256r1.public.pem"), KeyType.secp256r1, false),
                // rsa
                arguments(loadPem("rsa.public.pem"), KeyType.RSA, false),
                // ---private keys---
                // ed25519 (not supported)
//                arguments(loadPem("ed25519.private.pem"), KeyType.Ed25519, true),
                // secp256k1
                arguments(loadPem("secp256k1.private.pem"), KeyType.secp256k1, true),
                // secp256r1
                arguments(loadPem("secp256r1.private.pem"), KeyType.secp256r1, true),
                // rsa
                arguments(loadPem("rsa.private.pem"), KeyType.RSA, true),
            )

        private fun loadJwk(filename: String): String = loadKey("jwk/$filename")
        private fun loadPem(filename: String): String = loadKey("pem/$filename")
        private fun loadKey(relativePath: String): String =
            this::class.java.classLoader.getResource(relativePath)!!.path.let { File(it).readText() }
    }
}
