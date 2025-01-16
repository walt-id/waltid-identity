package id.walt.webwallet.service

import TestUtils
import id.walt.webwallet.utils.JsonUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwsDecoderTest {

    private val sut = JwsDecoder()

    @ParameterizedTest
    @MethodSource
    fun payload(data: String, expected: JsonObject) {
        // when
        val result = sut.payload(data)
        // then
        assertNull(JsonUtils.tryGetData(result, "vc"))
        assertEquals(expected = expected, actual = result)
    }

    companion object {
        const val credentialResourcePath = "credential-status/status-list-credential"

        @JvmStatic
        fun payload(): Stream<Arguments> = Stream.of(
            getArgument("revocation-list-with-status-message", "revocation-list-with-status-message", false),
            getArgument("revocation-list-vc-wrapped", "revocation-list-with-status-message", true),
            getArgument("revocation-list-jwt", "revocation-list-with-status-message", true),
        )

        private fun getArgument(filename: String, expected: String, unwrap: Boolean) = Arguments.of(
            TestUtils.loadResource("$credentialResourcePath/$filename.json"),
            Json.decodeFromString<JsonObject>(TestUtils.loadResource("$credentialResourcePath/$expected.json"))
        )
    }
}