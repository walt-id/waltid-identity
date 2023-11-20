import TestUtils.loadJwk
import TestUtils.loadPem
import id.walt.crypto.keys.JvmLocalKeyCreator
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmLocalKeyCreatorTest {

    private val sut = JvmLocalKeyCreator

    @ParameterizedTest
    @EnumSource(KeyType::class)
    fun generate(type: KeyType) = runTest {
        val key = sut.generate(type)
        assertEquals(type, key.keyType)
    }

    fun importRawPublicKey() = runTest {

    }

    @ParameterizedTest
    @MethodSource
    fun importJWK(keyFile: String, type: KeyType, isPrivate: Boolean) = runTest {
        val keyResult = sut.importJWK(keyFile)
        val key = keyResult.getOrThrow()
        assertTrue(keyResult.isSuccess)
        assertEquals(type, key.keyType)
        assertEquals(isPrivate, key.hasPrivateKey)
        assertEquals(key.getThumbprint(), key.getKeyId())
    }

    @ParameterizedTest
    @MethodSource
    fun importPEM(keyFile: String, type: KeyType, isPrivate: Boolean) = runTest {
        val keyResult = sut.importPEM(keyFile)
        val key = keyResult.getOrThrow()
        assertTrue(keyResult.isSuccess)
        assertEquals(type, key.keyType)
        assertEquals(isPrivate, key.hasPrivateKey)
        assertEquals(key.getThumbprint(), key.getKeyId())
    }

    companion object {
        @JvmStatic
        fun importJWK(): Stream<Arguments> = Stream.of(
            // ed25519
            Arguments.arguments(loadJwk("ed25519.private.json"), KeyType.Ed25519, true),
            // secp256k1
            Arguments.arguments(loadJwk("secp256k1.private.json"), KeyType.secp256k1, true),
            // secp256r1
            Arguments.arguments(loadJwk("secp256r1.private.json"), KeyType.secp256r1, true),
            // rsa
            Arguments.arguments(loadJwk("rsa.private.json"), KeyType.RSA, true),
            // public
            // ed25519
            Arguments.arguments(loadJwk("ed25519.public.json"), KeyType.Ed25519, false),
            // secp256k1
            Arguments.arguments(loadJwk("secp256k1.public.json"), KeyType.secp256k1, false),
            // secp256r1
            Arguments.arguments(loadJwk("secp256r1.public.json"), KeyType.secp256r1, false),
            // rsa
            Arguments.arguments(loadJwk("rsa.public.json"), KeyType.RSA, false),
        )

        @JvmStatic
        fun importPEM(): Stream<Arguments> = Stream.of(
            // ed25519 (not supported)
//                arguments(loadPem("ed25519.private.pem"), KeyType.Ed25519, true),
            // secp256k1
            Arguments.arguments(loadPem("secp256k1.private.pem"), KeyType.secp256k1, true),
            // secp256r1
            Arguments.arguments(loadPem("secp256r1.private.pem"), KeyType.secp256r1, true),
            // rsa
            Arguments.arguments(loadPem("rsa.private.pem"), KeyType.RSA, true),
            // public
            // ed25519 (not supported)
//                arguments(loadPem("ed25519.public.pem"), KeyType.Ed25519, false),
            // secp256k1
            Arguments.arguments(loadPem("secp256k1.public.pem"), KeyType.secp256k1, false),
            // secp256r1
            Arguments.arguments(loadPem("secp256r1.public.pem"), KeyType.secp256r1, false),
            // rsa
            Arguments.arguments(loadPem("rsa.public.pem"), KeyType.RSA, false),
        )
    }
}