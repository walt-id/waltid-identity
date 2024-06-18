import TestUtils.loadJwkLocal
import TestUtils.loadSerializedLocal
import TestUtils.loadSerializedTse
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.tse.TSEKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.test.assertEquals

class KeySerializationTests {

    @ParameterizedTest
    @MethodSource
    fun `given key, when serializing it then the serialization result contains the correct type and key-string`(
        keyFile: String, type: String
    ) = runTest {
        // given
        val key = JWKKey.importJWK(keyFile).getOrThrow()
        // when
        val serialized = KeySerialization.serializeKey(key)
        val decoded = Json.decodeFromString<JsonObject>(serialized)
        // then
        assertEquals(type, decoded["type"]!!.jsonPrimitive.content)
        assertEquals(keyFile.replace("\\s".toRegex(), ""), decoded["jwk"]!!.jsonPrimitive.content)
    }

    @ParameterizedTest
    @MethodSource
    fun `given a serialized key string, when deserializing then the result has the correct class type`(
        serialized: String,
        clazz: KClass<Key>
    ) =
        runTest {
            // when
            val key = KeySerialization.deserializeKey(serialized).getOrThrow()
            // then
            assertEquals(clazz.java.simpleName, key::class.java.simpleName)
        }

    companion object {
        @JvmStatic
        fun `given key, when serializing it then the serialization result contains the correct type and key-string`(): Stream<Arguments> =
            Stream.of(
                arguments(loadJwkLocal("ed25519.private.json"), "jwk"),
                arguments(loadJwkLocal("secp256k1.private.json"), "jwk"),
                arguments(loadJwkLocal("secp256r1.private.json"), "jwk"),
                arguments(loadJwkLocal("rsa.private.json"), "jwk"),
                // public
                arguments(loadJwkLocal("ed25519.public.json"), "jwk"),
                arguments(loadJwkLocal("secp256k1.public.json"), "jwk"),
                arguments(loadJwkLocal("secp256r1.public.json"), "jwk"),
                arguments(loadJwkLocal("rsa.public.json"), "jwk"),
            )

        @JvmStatic
        fun `given a serialized key string, when deserializing then the result has the correct class type`(): Stream<Arguments> = Stream.of(
            arguments(loadSerializedLocal("ed25519.private.json"), JWKKey::class),
            arguments(loadSerializedLocal("secp256k1.private.json"), JWKKey::class),
            arguments(loadSerializedLocal("secp256r1.private.json"), JWKKey::class),
            arguments(loadSerializedLocal("rsa.private.json"), JWKKey::class),
            // public
            arguments(loadSerializedLocal("ed25519.public.json"), JWKKey::class),
            arguments(loadSerializedLocal("secp256k1.public.json"), JWKKey::class),
            arguments(loadSerializedLocal("secp256r1.public.json"), JWKKey::class),
            arguments(loadSerializedLocal("rsa.public.json"), JWKKey::class),
            //// tse
            // arguments(loadSerializedTse("ed25519.private.json"), TSEKey::class), // FIXME: cannot access key in TSE when pre-serialized
            arguments(loadSerializedTse("ed25519.public.json"), TSEKey::class),
        )
    }


}
