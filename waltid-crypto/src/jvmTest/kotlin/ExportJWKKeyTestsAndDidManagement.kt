import TestUtils.loadJwkLocal
import TestUtils.loadPemLocal
import TestUtils.loadSerializedLocal
import id.walt.crypto.keys.KeySerialization
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

class ExportJWKKeyTestsAndDidManagement {

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
        fun `given key, when exporting JsonObject then the result is a valid jwk string`(): Stream<Arguments> = Stream.of(
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
        fun `given key, when exporting pem then the result is a valid pem string`(): Stream<Arguments> = Stream.of(
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
