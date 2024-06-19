import TestUtils.loadJwkLocal
import TestUtils.loadPemLocal
import TestUtils.loadResource
import TestUtils.loadResourceBytes
import TestUtils.loadSerializedLocal
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalJWKKeyAndDidManagementTest {
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
        println(publicBytes)
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

    @ParameterizedTest
    @ValueSource(strings = ["ed25519.private.json", "secp256k1.private.json", "secp256r1.private.json", "rsa.private.json"])
    @Disabled // not implemented
    fun signRaw(keyFile: String) = runTest {
        val key = KeySerialization.deserializeKey(loadSerializedLocal(keyFile)).getOrThrow()
        val signature = key.signRaw(payload.toString().encodeToByteArray())
        val verificationResult = key.getPublicKey().verifyRaw(signature as ByteArray)
        assertTrue(verificationResult.isSuccess)
        assertEquals(payload.toString().encodeToByteArray(), verificationResult.getOrThrow())
    }

    @ParameterizedTest
    @ValueSource(strings = ["ed25519.private.json", "secp256k1.private.json", "secp256r1.private.json", "rsa.private.json"])
    fun signJws(keyFile: String) = runTest {
        val key = KeySerialization.deserializeKey(loadSerializedLocal(keyFile)).getOrThrow()
        val signature = key.signJws(payload.toString().encodeToByteArray())
        val verificationResult = key.getPublicKey().verifyJws(signature)
        assertTrue(verificationResult.isSuccess)
        assertEquals(payload, verificationResult.getOrThrow())
    }

    @ParameterizedTest
    @MethodSource
    fun getKeyId(keyFile: String, keyId: String) = runTest {
        val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
        assertEquals(keyId, key.getKeyId())
    }

    fun getThumbprint() = runTest {}
    fun verifyRaw() = runTest {}

    @ParameterizedTest
    @MethodSource
    fun verifyJws(keyFile: String, signature: String) = runTest {
        println("-- Verifying JWS")

        val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
        println("Key: ($key)")
        println(key.exportJWK())

        println("Verifying signature: $signature")

        val verificationResult = key.verifyJws(signature)
        println("Result: $verificationResult")

        verificationResult.getOrThrow()

        assertTrue(verificationResult.isSuccess)
        assertEquals(payload, verificationResult.getOrThrow())
    }

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
            arguments(loadSerializedLocal("ed25519.private.json"), loadResourceBytes("public-bytes/ed25519.bin")),
            arguments(loadSerializedLocal("secp256k1.private.json"), loadResourceBytes("public-bytes/secp256k1.bin")),
            arguments(loadSerializedLocal("secp256r1.private.json"), loadResourceBytes("public-bytes/secp256r1.bin")),
            arguments(loadSerializedLocal("rsa.private.json"), loadResourceBytes("public-bytes/rsa.bin")),
            // public
            arguments(loadSerializedLocal("ed25519.public.json"), loadResourceBytes("public-bytes/ed25519.bin")),
            arguments(loadSerializedLocal("secp256k1.public.json"), loadResourceBytes("public-bytes/secp256k1.bin")),
            arguments(loadSerializedLocal("secp256r1.public.json"), loadResourceBytes("public-bytes/secp256r1.bin")),
            arguments(loadSerializedLocal("rsa.public.json"), loadResourceBytes("public-bytes/rsa.bin")),
        )

        @JvmStatic
        fun getPublicKey(): Stream<Arguments> = Stream.of(
            arguments(loadSerializedLocal("ed25519.private.json"), loadSerializedLocal("ed25519.public.json")),
            arguments(loadSerializedLocal("secp256k1.private.json"), loadSerializedLocal("secp256k1.public.json")),
            arguments(loadSerializedLocal("secp256r1.private.json"), loadSerializedLocal("secp256r1.public.json")),
            arguments(loadSerializedLocal("rsa.private.json"), loadSerializedLocal("rsa.public.json")),
            // public
            arguments(loadSerializedLocal("ed25519.public.json"), loadSerializedLocal("ed25519.public.json")),
            arguments(loadSerializedLocal("secp256k1.public.json"), loadSerializedLocal("secp256k1.public.json")),
            arguments(loadSerializedLocal("secp256r1.public.json"), loadSerializedLocal("secp256r1.public.json")),
            arguments(loadSerializedLocal("rsa.public.json"), loadSerializedLocal("rsa.public.json")),
        )

        @JvmStatic
        fun getKeyType(): Stream<Arguments> = Stream.of(
            arguments(loadSerializedLocal("ed25519.private.json"), KeyType.Ed25519),
            arguments(loadSerializedLocal("secp256k1.private.json"), KeyType.secp256k1),
            arguments(loadSerializedLocal("secp256r1.private.json"), KeyType.secp256r1),
            arguments(loadSerializedLocal("rsa.private.json"), KeyType.RSA),
            // public
            arguments(loadSerializedLocal("ed25519.public.json"), KeyType.Ed25519),
            arguments(loadSerializedLocal("secp256k1.public.json"), KeyType.secp256k1),
            arguments(loadSerializedLocal("secp256r1.public.json"), KeyType.secp256r1),
            arguments(loadSerializedLocal("rsa.public.json"), KeyType.RSA),
        )

        @JvmStatic
        fun getHasPrivateKey(): Stream<Arguments> = Stream.of(
            arguments(loadSerializedLocal("ed25519.private.json"), true),
            arguments(loadSerializedLocal("secp256k1.private.json"), true),
            arguments(loadSerializedLocal("secp256r1.private.json"), true),
            arguments(loadSerializedLocal("rsa.private.json"), true),
            // public
            arguments(loadSerializedLocal("ed25519.public.json"), false),
            arguments(loadSerializedLocal("secp256k1.public.json"), false),
            arguments(loadSerializedLocal("secp256r1.public.json"), false),
            arguments(loadSerializedLocal("rsa.public.json"), false),
        )

        @JvmStatic
        fun getKeyId(): Stream<Arguments> = Stream.of(
            arguments(loadSerializedLocal("ed25519.private.json"), "DJ-kT9JRDYwlJfYjhmJgNwIcuaU6RcSuR8eHIwbsnHQ"),
            arguments(loadSerializedLocal("secp256k1.private.json"), "PIv-EHegS0qL__8D4t36kULI8vzBsH4yBshhmY036yA"),
            arguments(loadSerializedLocal("secp256r1.private.json"), "LtObdob_k1-dw59-MwdA7auJUJCsqGQ7x2-ufXcB6gY"),
            arguments(loadSerializedLocal("rsa.private.json"), "288WlRQvku-zrHFmvcAW86jnTF3qsMoEUKEbteI2K4A"),
            // public
            arguments(loadSerializedLocal("ed25519.public.json"), "DJ-kT9JRDYwlJfYjhmJgNwIcuaU6RcSuR8eHIwbsnHQ"),
            arguments(loadSerializedLocal("secp256k1.public.json"), "PIv-EHegS0qL__8D4t36kULI8vzBsH4yBshhmY036yA"),
            arguments(loadSerializedLocal("secp256r1.public.json"), "LtObdob_k1-dw59-MwdA7auJUJCsqGQ7x2-ufXcB6gY"),
            arguments(loadSerializedLocal("rsa.public.json"), "288WlRQvku-zrHFmvcAW86jnTF3qsMoEUKEbteI2K4A"),
        )

        @JvmStatic
        fun verifyJws(): Stream<Arguments> = Stream.of(
            arguments(loadSerializedLocal("ed25519.public.json"), loadResource("signatures/ed25519.txt")),
            arguments(loadSerializedLocal("secp256k1.public.json"), loadResource("signatures/secp256k1.txt")),
            arguments(loadSerializedLocal("secp256r1.public.json"), loadResource("signatures/secp256r1.txt")),
            arguments(loadSerializedLocal("rsa.public.json"), loadResource("signatures/rsa.txt")),
        )

        @JvmStatic
        fun exportJWK(): Stream<Arguments> = Stream.of(
            arguments(loadSerializedLocal("ed25519.private.json"), loadJwkLocal("ed25519.private.json")),
            arguments(loadSerializedLocal("secp256k1.private.json"), loadJwkLocal("secp256k1.private.json")),
            arguments(loadSerializedLocal("secp256r1.private.json"), loadJwkLocal("secp256r1.private.json")),
            arguments(loadSerializedLocal("rsa.private.json"), loadJwkLocal("rsa.private.json")),
            // public
            arguments(loadSerializedLocal("ed25519.public.json"), loadJwkLocal("ed25519.public.json")),
            arguments(loadSerializedLocal("secp256k1.public.json"), loadJwkLocal("secp256k1.public.json")),
            arguments(loadSerializedLocal("secp256r1.public.json"), loadJwkLocal("secp256r1.public.json")),
            arguments(loadSerializedLocal("rsa.public.json"), loadJwkLocal("rsa.public.json")),
        )

        @JvmStatic
        fun exportJWKObject(): Stream<Arguments> = Stream.of(
            arguments(loadSerializedLocal("ed25519.private.json"), loadJwkLocal("ed25519.private.json")),
            arguments(loadSerializedLocal("secp256k1.private.json"), loadJwkLocal("secp256k1.private.json")),
            arguments(loadSerializedLocal("secp256r1.private.json"), loadJwkLocal("secp256r1.private.json")),
            arguments(loadSerializedLocal("rsa.private.json"), loadJwkLocal("rsa.private.json")),
            // public
            arguments(loadSerializedLocal("ed25519.public.json"), loadJwkLocal("ed25519.public.json")),
            arguments(loadSerializedLocal("secp256k1.public.json"), loadJwkLocal("secp256k1.public.json")),
            arguments(loadSerializedLocal("secp256r1.public.json"), loadJwkLocal("secp256r1.public.json")),
            arguments(loadSerializedLocal("rsa.public.json"), loadJwkLocal("rsa.public.json")),
        )

        @JvmStatic
        fun exportPEM(): Stream<Arguments> = Stream.of(
            arguments(loadSerializedLocal("ed25519.private.json"), loadPemLocal("ed25519.private.pem")),
            arguments(loadSerializedLocal("secp256k1.private.json"), loadPemLocal("secp256k1.private.pem")),
            arguments(loadSerializedLocal("secp256r1.private.json"), loadPemLocal("secp256r1.private.pem")),
            arguments(loadSerializedLocal("rsa.private.json"), loadPemLocal("rsa.private.pem")),
            // public
            arguments(loadSerializedLocal("ed25519.public.json"), loadPemLocal("ed25519.public.pem")),
            arguments(loadSerializedLocal("secp256k1.public.json"), loadPemLocal("secp256k1.public.pem")),
            arguments(loadSerializedLocal("secp256r1.public.json"), loadPemLocal("secp256r1.public.pem")),
            arguments(loadSerializedLocal("rsa.public.json"), loadPemLocal("rsa.public.pem")),
        )
    }
}
