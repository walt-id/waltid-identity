package id.walt.credentials

import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertEquals

data class SampleData(
    val sha256: String,
    val disclosure: String,
    val contents: String,
)

fun computeDigest(disclosureBase64Url: String): String {
    val bytes = disclosureBase64Url.toByteArray(Charsets.US_ASCII)
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
}

fun encoded(rawJson: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(rawJson.toByteArray(StandardCharsets.UTF_8))

class Rfc9901SampleTest {
    //@ParameterizedTest
    @MethodSource("rfcExamples")
    fun givenDisclosureVerifyHashUsingWaltHashed(testCase: SampleData) =
        runTest {
            val sut = SdJwtSelectiveDisclosure(Json.parseToJsonElement(testCase.contents).jsonArray, testCase.contents)

            assertEquals(testCase.sha256, sut.asHashed())
        }

    //@ParameterizedTest
    @MethodSource("rfcExamples")
    fun givenDisclosureVerifyHashUsingWaltHashed2(testCase: SampleData) =
        runTest {
            val sut = SdJwtSelectiveDisclosure(Json.parseToJsonElement(testCase.contents).jsonArray, testCase.contents)

            assertEquals(testCase.sha256, sut.asHashed2())
        }
    @ParameterizedTest
    @MethodSource("rfcExamples")
    fun givenDisclosureVerifyHashUsingWaltHashed3(testCase: SampleData) =
        runTest {
            val sut = SdJwtSelectiveDisclosure(Json.parseToJsonElement(testCase.contents).jsonArray, testCase.contents)

            assertEquals(testCase.sha256, sut.asHashed3())
        }

    @ParameterizedTest
    @MethodSource("rfcExamples")
    fun givenDisclosureCalculateHashUsingUsAscii(testCase: SampleData) =
        runTest {
            assertEquals(testCase.sha256, computeDigest(testCase.disclosure))
        }

    @ParameterizedTest
    @MethodSource("rfcExamples")
    fun givenContentsVerifyDisclosureUsingWalt(testCase: SampleData) =
        runTest {
            assertEquals(testCase.disclosure, encoded(testCase.contents))
        }

    @ParameterizedTest
    @MethodSource("rfcExamples")
    fun givenContentsVerifyDisclosure(testCase: SampleData) =
        runTest {
            assertEquals(testCase.disclosure, encoded(testCase.contents))
        }

    @ParameterizedTest
    @MethodSource("rfcExamples")
    fun verifyComplete(testCase: SampleData) =
        runTest {
            assertEquals(testCase.sha256, computeDigest(encoded(testCase.contents)))
        }

    companion object {
        /**
         * Values from "A.1. Simple Structured SD-JWT" in "Selective Disclosure for JSON Web Tokens" RFC 9901
         *
         * https://datatracker.ietf.org/doc/rfc9901/
         */
        @JvmStatic
        fun rfcExamples(): Stream<SampleData> =
            Stream.of(
                SampleData(
                    sha256 = "X6ZAYOII2vPN40V7xExZwVwz7yRmLNcVwt5DL8RLv4g",
                    disclosure = "WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgInN1YiIsICI2YzVjMGE0OS1iNTg5LTQzMWQtYmFlNy0yMTkxMjJhOWVjMmMiXQ",
                    contents = """["2GLC42sKQveCfGfryNRN9w", "sub", "6c5c0a49-b589-431d-bae7-219122a9ec2c"]""",
                ),
                SampleData(
                    sha256 = "ommFAicVT8LGHCB0uywx7fYuo3MHYKO15cz-RZEYM5Q",
                    disclosure = "WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAiXHU1OTJhXHU5MGNlIl0",
                    contents = """["eluV5Og3gSNII8EYnsxA_A", "given_name", "\u592a\u90ce"]""",
                ),
                SampleData(
                    sha256 = "Kuet1yAa0HIQvYnOVd59hcViO9Ug6J2kSfqYRBeowvE",
                    disclosure = "WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgImVtYWlsIiwgIlwidW51c3VhbCBlbWFpbCBhZGRyZXNzXCJAZXhhbXBsZS5qcCJd",
                    contents = """["eI8ZWm9QnKPpNPeNenHdhQ", "email", "\"unusual email address\"@example.jp"]""",
                ),
                SampleData(
                    sha256 = "MMldOFFzB2d0umlmpTIaGerhWdU_PpYfLvKhh_f_9aY",
                    disclosure = "WyJ5eXRWYmRBUEdjZ2wyckk0QzlHU29nIiwgImJpcnRoZGF0ZSIsICIxOTQwLTAxLTAxIl0",
                    contents = """["yytVbdAPGcgl2rI4C9GSog", "birthdate", "1940-01-01"]""",
                ),
            )
    }
}
