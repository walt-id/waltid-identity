import TestUtils.loadJwk
import TestUtils.loadPem
import TestUtils.loadResource
import TestUtils.loadResourceBytes
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.LocalKeyMetadata
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalKeyTest {
    private val payload = JsonObject(
        mapOf(
            "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
            "iss" to JsonPrimitive("http://localhost:3000"),
            "aud" to JsonPrimitive("TOKEN"),
        )
    )

    @ParameterizedTest
    @MethodSource
    fun getPublicKeyRepresentation(keyFile: String) = runTest {
        val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
        val publicBytes = key.getPublicKeyRepresentation()
    }

    @ParameterizedTest
    @MethodSource
    fun getPublicKey(keyFile: String, publicFile: String) = runTest {
        val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
        val public = KeySerialization.deserializeKey(publicFile).getOrThrow()
        val publicKey = key.getPublicKey()
        assertTrue(!publicKey.hasPrivateKey)
        assertEquals(public.getKeyId(), publicKey.getKeyId())
        assertEquals(public.getThumbprint(), publicKey.getThumbprint())
        assertEquals(public.exportJWK(), publicKey.exportJWK())
    }

    @ParameterizedTest
    @MethodSource
    fun getKeyType(keyFile: String, type: KeyType) = runTest {
        val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
        assertEquals(type, key.keyType)
    }

    @ParameterizedTest
    @MethodSource
    fun getHasPrivateKey(keyFile: String, isPrivate: Boolean) = runTest {
        val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
        assertEquals(isPrivate, key.hasPrivateKey)
    }
    fun getKeyId() = runTest {}
    fun getThumbprint() = runTest {}
    @ParameterizedTest
    @ValueSource(strings = ["ed25519.private.json", "secp256k1.private.json", "secp256r1.private.json", "rsa.private.json"])
    @Disabled // not implemented
    fun signRaw(keyFile: String) = runTest {
        val key = KeySerialization.deserializeKey(loadResource("serialized/$keyFile")).getOrThrow()
        val signature = key.signRaw(payload.toString().encodeToByteArray())
        val verificationResult = key.getPublicKey().verifyRaw(signature as ByteArray)
        assertTrue(verificationResult.isSuccess)
        assertEquals(payload.toString().encodeToByteArray(), verificationResult.getOrThrow())
    }

    @ParameterizedTest
    @ValueSource(strings = ["ed25519.private.json", "secp256k1.private.json", "secp256r1.private.json", "rsa.private.json"])
    fun signJws(keyFile: String) = runTest {
        val key = KeySerialization.deserializeKey(loadResource("serialized/$keyFile")).getOrThrow()
        val signature = key.signJws(payload.toString().encodeToByteArray())
        val verificationResult = key.getPublicKey().verifyJws(signature)
        assertTrue(verificationResult.isSuccess)
        assertEquals(payload, verificationResult.getOrThrow())
    }
    fun verifyRaw() = runTest {}
    fun verifyJws() = runTest {}
    @ParameterizedTest
    @MethodSource
    fun exportJWK(keyFile: String, jwkFile: String) = runTest {
        val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
        val export = key.exportJWK()
        assertEquals(jwkFile.replace("\\s".toRegex(), ""), export)
    }
    @ParameterizedTest
    @MethodSource
    fun exportJWKObject(keyFile: String, jwkFile: String) = runTest {
        val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
        val export = key.exportJWKObject()
        assertEquals(jwkFile.replace("\\s".toRegex(), ""), export.toString())
    }
    @ParameterizedTest
    @MethodSource
    @Disabled // not implemented
    fun exportPEM(keyFile: String, pemFile: String) = runTest {
        val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
        val export = key.exportPEM()
        assertEquals(pemFile, export)
    }

    companion object {
        @JvmStatic
        fun getPublicKeyRepresentation(): Stream<Arguments> = Stream.of(

        )
        @JvmStatic
        fun getPublicKey(): Stream<Arguments> = Stream.of(
            arguments(loadResource("serialized/ed25519.private.json"), loadResource("serialized/ed25519.public.json")),
            arguments(loadResource("serialized/secp256k1.private.json"), loadResource("serialized/secp256k1.public.json")),
            arguments(loadResource("serialized/secp256r1.private.json"), loadResource("serialized/secp256r1.public.json")),
            arguments(loadResource("serialized/rsa.private.json"), loadResource("serialized/rsa.public.json")),
            // public
            arguments(loadResource("serialized/ed25519.public.json"), loadResource("serialized/ed25519.public.json")),
            arguments(loadResource("serialized/secp256k1.public.json"), loadResource("serialized/secp256k1.public.json")),
            arguments(loadResource("serialized/secp256r1.public.json"), loadResource("serialized/secp256r1.public.json")),
            arguments(loadResource("serialized/rsa.public.json"), loadResource("serialized/rsa.public.json")),
        )
        @JvmStatic
        fun getKeyType(): Stream<Arguments> = Stream.of(
            arguments(loadResource("serialized/ed25519.private.json"), KeyType.Ed25519),
            arguments(loadResource("serialized/secp256k1.private.json"), KeyType.secp256k1),
            arguments(loadResource("serialized/secp256r1.private.json"), KeyType.secp256r1),
            arguments(loadResource("serialized/rsa.private.json"), KeyType.RSA),
            // public
            arguments(loadResource("serialized/ed25519.public.json"), KeyType.Ed25519),
            arguments(loadResource("serialized/secp256k1.public.json"), KeyType.secp256k1),
            arguments(loadResource("serialized/secp256r1.public.json"), KeyType.secp256r1),
            arguments(loadResource("serialized/rsa.public.json"), KeyType.RSA),
        )

        @JvmStatic
        fun getHasPrivateKey(): Stream<Arguments> = Stream.of(
            arguments(loadResource("serialized/ed25519.private.json"), true),
            arguments(loadResource("serialized/secp256k1.private.json"), true),
            arguments(loadResource("serialized/secp256r1.private.json"), true),
            arguments(loadResource("serialized/rsa.private.json"), true),
            // public
            arguments(loadResource("serialized/ed25519.public.json"), false),
            arguments(loadResource("serialized/secp256k1.public.json"), false),
            arguments(loadResource("serialized/secp256r1.public.json"), false),
            arguments(loadResource("serialized/rsa.public.json"), false),
        )

        @JvmStatic
        fun exportJWK(): Stream<Arguments> = Stream.of(
            arguments(loadResource("serialized/ed25519.private.json"), loadJwk("ed25519.private.json")),
            arguments(loadResource("serialized/secp256k1.private.json"), loadJwk("secp256k1.private.json")),
            arguments(loadResource("serialized/secp256r1.private.json"), loadJwk("secp256r1.private.json")),
            arguments(loadResource("serialized/rsa.private.json"), loadJwk("rsa.private.json")),
            // public
            arguments(loadResource("serialized/ed25519.public.json"), loadJwk("ed25519.public.json")),
            arguments(loadResource("serialized/secp256k1.public.json"), loadJwk("secp256k1.public.json")),
            arguments(loadResource("serialized/secp256r1.public.json"), loadJwk("secp256r1.public.json")),
            arguments(loadResource("serialized/rsa.public.json"), loadJwk("rsa.public.json")),
        )

        @JvmStatic
        fun exportJWKObject(): Stream<Arguments> = Stream.of(
            arguments(loadResource("serialized/ed25519.private.json"), loadJwk("ed25519.private.json")),
            arguments(loadResource("serialized/secp256k1.private.json"), loadJwk("secp256k1.private.json")),
            arguments(loadResource("serialized/secp256r1.private.json"), loadJwk("secp256r1.private.json")),
            arguments(loadResource("serialized/rsa.private.json"), loadJwk("rsa.private.json")),
            // public
            arguments(loadResource("serialized/ed25519.public.json"), loadJwk("ed25519.public.json")),
            arguments(loadResource("serialized/secp256k1.public.json"), loadJwk("secp256k1.public.json")),
            arguments(loadResource("serialized/secp256r1.public.json"), loadJwk("secp256r1.public.json")),
            arguments(loadResource("serialized/rsa.public.json"), loadJwk("rsa.public.json")),
        )

        @JvmStatic
        fun exportPEM(): Stream<Arguments> = Stream.of(
            arguments(loadResource("serialized/ed25519.private.json"), loadPem("ed25519.private.pem")),
            arguments(loadResource("serialized/secp256k1.private.json"), loadPem("secp256k1.private.pem")),
            arguments(loadResource("serialized/secp256r1.private.json"), loadPem("secp256r1.private.pem")),
            arguments(loadResource("serialized/rsa.private.json"), loadPem("rsa.private.pem")),
            // public
            arguments(loadResource("serialized/ed25519.public.json"), loadPem("ed25519.public.pem")),
            arguments(loadResource("serialized/secp256k1.public.json"), loadPem("secp256k1.public.pem")),
            arguments(loadResource("serialized/secp256r1.public.json"), loadPem("secp256r1.public.pem")),
            arguments(loadResource("serialized/rsa.public.json"), loadPem("rsa.public.pem")),
        )
    }
}

fun main() = runBlocking{
    val key = KeySerialization.deserializeKey(loadResource("serialized/ed25519.public.json")).getOrThrow()
    val bytes = key.getPublicKeyRepresentation()
//    File("waltid-crypto/src/jvmTest/resources/public-bytes/ed25519.txt").writeBytes(bytes)
    val raw = loadResource("public-bytes/ed25519.txt").decodeBase64Bytes()
    val rawKey = LocalKey.importRawPublicKey(KeyType.Ed25519, raw, LocalKeyMetadata())
    println(key.exportJWK())
    println(rawKey.exportJWK())
}