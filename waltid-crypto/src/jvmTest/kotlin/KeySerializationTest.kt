import TestUtils.loadJwk
import TestUtils.loadResource
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.LocalKey
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

class KeySerializationTest {

    @ParameterizedTest
    @MethodSource
    fun `given key, when serializing it then the serialization result contains the correct type and key-string`(
        filename: String, type: String
    ) = runTest {
        // given
        val keyString = loadJwk(filename)
        val key = LocalKey.importJWK(keyString).getOrThrow()
        // when
        val serialized = KeySerialization.serializeKey(key)
        val decoded = Json.decodeFromString<JsonObject>(serialized)
        // then
        assertEquals(type, decoded["type"]!!.jsonPrimitive.content)
        assertEquals(keyString.replace("\\s".toRegex(), ""), decoded["jwk"]!!.jsonPrimitive.content)
    }

    @ParameterizedTest
    @MethodSource
    fun `given a serialized key string, when deserializing then the result has the correct class type`(filename: String, clazz: KClass<Key>) =
        runTest {
            // given
            val serialized = loadResource("serialized/$filename")
            // when
            val key = KeySerialization.deserializeKey(serialized).getOrThrow()
            // then
            assertEquals(clazz.java.simpleName, key::class.java.simpleName)
        }

    companion object {
        @JvmStatic
        fun `given key, when serializing it then the serialization result contains the correct type and key-string`(): Stream<Arguments> = Stream.of(
            arguments("ed25519.private.json", "local"),
            arguments("secp256k1.private.json", "local"),
            arguments("secp256r1.private.json", "local"),
            arguments("rsa.private.json", "local"),
            // public
            arguments("ed25519.public.json", "local"),
            arguments("secp256k1.public.json", "local"),
            arguments("secp256r1.public.json", "local"),
            arguments("rsa.public.json", "local"),
        )

        @JvmStatic
        fun `given a serialized key string, when deserializing then the result has the correct class type`(): Stream<Arguments> = Stream.of (
            arguments("ed25519.private.json", LocalKey::class),
            arguments("secp256k1.private.json", LocalKey::class),
            arguments("secp256r1.private.json", LocalKey::class),
            arguments("rsa.private.json", LocalKey::class),
            // public
            arguments("ed25519.public.json", LocalKey::class),
            arguments("secp256k1.public.json", LocalKey::class),
            arguments("secp256r1.public.json", LocalKey::class),
            arguments("rsa.public.json", LocalKey::class),
        )
    }


}