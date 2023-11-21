import TestUtils.loadJwkLocal
import TestUtils.loadPemLocal
import TestUtils.loadResourceBytes
import TestUtils.loadSerializedLocal
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
            arguments(loadResourceBytes("public-bytes/ed25519.bin"), KeyType.Ed25519, loadSerializedLocal("ed25519.public.json"), ed25519RawAssertions),
            // secp256k1 (throwing Invalid point encoding 0x30)
//            arguments(loadResourceBytes("public-bytes/secp256k1.bin"), KeyType.secp256k1, loadSerializedLocal("secp256k1.public.json")),
            // secp256r1 (throwing Invalid point encoding 0x30)
//            arguments(loadResourceBytes("public-bytes/secp256r1.bin"), KeyType.secp256r1, loadSerializedLocal("secp256r1.public.json")),
            // rsa (not implemented)
//            arguments(loadResourceBytes("public-bytes/rsa.bin"), KeyType.RSA, loadSerializedLocal("rsa.public.json")),
        )
        @JvmStatic
        fun importJWK(): Stream<Arguments> = Stream.of(
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
        fun importPEM(): Stream<Arguments> = Stream.of(
            // ed25519 (not supported)
//                arguments(loadPem("ed25519.private.pem"), KeyType.Ed25519, true),
            // secp256k1
            arguments(loadPemLocal("secp256k1.private.pem"), KeyType.secp256k1, true),
            // secp256r1
            arguments(loadPemLocal("secp256r1.private.pem"), KeyType.secp256r1, true),
            // rsa
            arguments(loadPemLocal("rsa.private.pem"), KeyType.RSA, true),
            // public
            // ed25519 (not supported)
//                arguments(loadPem("ed25519.public.pem"), KeyType.Ed25519, false),
            // secp256k1
            arguments(loadPemLocal("secp256k1.public.pem"), KeyType.secp256k1, false),
            // secp256r1
            arguments(loadPemLocal("secp256r1.public.pem"), KeyType.secp256r1, false),
            // rsa
            arguments(loadPemLocal("rsa.public.pem"), KeyType.RSA, false),
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