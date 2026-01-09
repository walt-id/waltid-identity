import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class JcaUtilsTest {

    @ParameterizedTest
    @MethodSource("pemFiles")
    @DisplayName("parsePEMEncodedJcaPublicKey should accept known PEM public keys")
    fun parsePEMEncodedJcaPublicKeyDoesNotThrow(pemFile: String) {
        val pemContent = TestUtils.loadResource("pem/$pemFile")
        assertDoesNotThrow {
            parsePEMEncodedJcaPublicKey(pemContent)
        }
    }

    companion object {

        @JvmStatic
        fun pemFiles(): Stream<String> = Stream.of(
            "ed25519.public.pem",
            "rsa.public.pem",
            "secp256k1.public.pem",
            "secp256r1.public.pem",
        )

    }
}