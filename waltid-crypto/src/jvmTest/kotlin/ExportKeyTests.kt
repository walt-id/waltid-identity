import TestUtils.loadJwk
import TestUtils.loadPem
import TestUtils.loadResource
import id.walt.crypto.keys.KeySerialization
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

class ExportKeyTests {

    @ParameterizedTest
    @MethodSource
    fun `given key, when exporting jwk then the result is a valid jwk string`(keyFile: String, jwkFile: String) =
        runTest {
            // given
            val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
            // when
            val export = key.exportJWK()
            // then
            assertEquals(jwkFile.replace("\\s".toRegex(), ""), export)
        }

    @ParameterizedTest
    @MethodSource
    fun `given key, when exporting JsonObject then the result is a valid jwk string`(keyFile: String, jwkFile: String) = runTest {
        // given
        val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
        // when
        val export = key.exportJWKObject()
        // then
        assertEquals(jwkFile.replace("\\s".toRegex(), ""), export.toString())
    }

    @ParameterizedTest
    @MethodSource
    @Disabled // not implemented
    fun `given key, when exporting pem then the result is a valid pem string`(keyFile: String, pemFile: String) =
        runTest {
            // given
            val key = KeySerialization.deserializeKey(keyFile).getOrThrow()
            // when
            val export = key.exportPEM()
            // then
            assertEquals(pemFile, export)
        }

    companion object {
        @JvmStatic
        fun `given key, when exporting jwk then the result is a valid jwk string`(): Stream<Arguments> = Stream.of(
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
        fun `given key, when exporting JsonObject then the result is a valid jwk string`(): Stream<Arguments> = Stream.of(
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
        fun `given key, when exporting pem then the result is a valid pem string`(): Stream<Arguments> = Stream.of(
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