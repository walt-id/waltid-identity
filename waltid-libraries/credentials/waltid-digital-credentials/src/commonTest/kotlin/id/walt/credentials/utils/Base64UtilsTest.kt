package id.walt.credentials.utils

import id.walt.crypto.utils.Base64Utils.matchesBase64Url
import kotlin.test.Test

class Base64UtilsTest {

    private val testStrings = listOf(
        "" to false, // We consider empty strings invalid
        "YWFh" to true, // Valid (aaa)
        "YWFhYg" to true, // Valid (aaab)
        "YWFhYmE" to true, // Valid (aaaba)
        "YWFhYmFi" to true, // Valid (aaabab)
        "Zm9vYmFy" to true, // Valid (foobar)
        "Zm9vYmFyIQ" to true, // Valid (foobar!) - note the ending Q
        "Zm9vYmFyIQ==" to true, // Valid with padding
        "c3VyZS4uLi4-Pw" to true, // Valid
        "c3VyZS4uLi4_Pw" to true, // Valid
        "c3VyZS4uLi4.?Pw" to false, // Invalid (.?)
        "c3VyZS4uLi4-Pw=" to false, // Invalid: has padding, but its total length (15) is not a multiple of 4
        "Invalid Chars!" to false, // Invalid (chars)
        "Almost=-Valid" to false, // Invalid (padding position)
        "TooMany===Padding" to false, // Invalid (padding count)
        "A" to false, // Invalid (data length % 4 == 1)
        "AB" to true, // Valid (data length % 4 == 2)
        "ABC" to true, // Valid (data length % 4 == 3)
        "ABCD" to true, // Valid (data length % 4 == 0)
        "AB=" to false, // Invalid (padding requires total length % 4 == 0)
        "ABC=" to true, // Valid
        "AB==" to true, // Valid
        "A===" to false, // Invalid (padding count)
        "A=B=" to false // Invalid (padding position)
    )

    @Test
    fun testBase64UrlMatching() {
        testStrings.forEach { (string, expected) ->
            check(string.matchesBase64Url() == expected) { "Invalid Base64 matching result (expected $expected) for: $string" }
        }
    }

}
