import TestUtils.loadJwk
import TestUtils.loadPem
import TestUtils.loadResource
import TestUtils.loadResourceBytes
import id.walt.crypto.keys.JvmLocalKeyCreator
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKeyMetadata
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
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

    @ParameterizedTest
    @MethodSource
    fun importRawPublicKey(
        raw: ByteArray, type: KeyType, publicFile: String, importRawAssertions: importRawAssertions
    ) = runTest {
        val publicKey = KeySerialization.deserializeKey(publicFile).getOrThrow()
        val rawKey = sut.importRawPublicKey(type, raw, LocalKeyMetadata())
        importRawAssertions(publicKey.exportJWKObject(), rawKey.exportJWKObject())
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
        fun importRawPublicKey(): Stream<Arguments> = Stream.of(
            // ed25519
            arguments(loadResourceBytes("public-bytes/ed25519.txt"), KeyType.Ed25519, loadResource("serialized/ed25519.public.json"), ed25519RawAssertions),
            // secp256k1 (throwing Invalid point encoding 0x30)
//            arguments(loadResourceBytes("public-bytes/secp256k1.txt"), KeyType.secp256k1, loadResource("serialized/secp256k1.public.json")),
            // secp256r1 (throwing Invalid point encoding 0x30)
//            arguments(loadResourceBytes("public-bytes/secp256r1.txt"), KeyType.secp256r1, loadResource("serialized/secp256r1.public.json")),
            // rsa (not implemented)
//            arguments(loadResourceBytes("public-bytes/rsa.txt"), KeyType.RSA, loadResource("serialized/rsa.public.json")),
        )
        @JvmStatic
        fun importJWK(): Stream<Arguments> = Stream.of(
            // ed25519
            arguments(loadJwk("ed25519.private.json"), KeyType.Ed25519, true),
            // secp256k1
            arguments(loadJwk("secp256k1.private.json"), KeyType.secp256k1, true),
            // secp256r1
            arguments(loadJwk("secp256r1.private.json"), KeyType.secp256r1, true),
            // rsa
            arguments(loadJwk("rsa.private.json"), KeyType.RSA, true),
            // public
            // ed25519
            arguments(loadJwk("ed25519.public.json"), KeyType.Ed25519, false),
            // secp256k1
            arguments(loadJwk("secp256k1.public.json"), KeyType.secp256k1, false),
            // secp256r1
            arguments(loadJwk("secp256r1.public.json"), KeyType.secp256r1, false),
            // rsa
            arguments(loadJwk("rsa.public.json"), KeyType.RSA, false),
        )

        @JvmStatic
        fun importPEM(): Stream<Arguments> = Stream.of(
            // ed25519 (not supported)
//                arguments(loadPem("ed25519.private.pem"), KeyType.Ed25519, true),
            // secp256k1
            arguments(loadPem("secp256k1.private.pem"), KeyType.secp256k1, true),
            // secp256r1
            arguments(loadPem("secp256r1.private.pem"), KeyType.secp256r1, true),
            // rsa
            arguments(loadPem("rsa.private.pem"), KeyType.RSA, true),
            // public
            // ed25519 (not supported)
//                arguments(loadPem("ed25519.public.pem"), KeyType.Ed25519, false),
            // secp256k1
            arguments(loadPem("secp256k1.public.pem"), KeyType.secp256k1, false),
            // secp256r1
            arguments(loadPem("secp256r1.public.pem"), KeyType.secp256r1, false),
            // rsa
            arguments(loadPem("rsa.public.pem"), KeyType.RSA, false),
        )

        private val ed25519RawAssertions: importRawAssertions = { expected, actual ->
            assertEquals(
                expected.jsonObject["kty"]!!.jsonPrimitive.content,
                actual.jsonObject["kty"]!!.jsonPrimitive.content
            )
            assertEquals(
                expected.jsonObject["crv"]!!.jsonPrimitive.content,
                actual.jsonObject["crv"]!!.jsonPrimitive.content
            )
            assertEquals(
                expected.jsonObject["x"]!!.jsonPrimitive.content,
                actual.jsonObject["x"]!!.jsonPrimitive.content
            )
        }
    }
}
internal typealias importRawAssertions = (expected: JsonObject, actual: JsonObject) -> Unit