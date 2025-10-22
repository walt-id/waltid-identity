package id.walt.credentials.utils

import id.walt.crypto.utils.HexUtils.matchesHex
import kotlin.test.Test

class HexUtilsTest {

    private val testStrings = listOf(
        "" to false, // false (empty)
        "0" to false, // false (odd length)
        "0a" to true, // true (lowercase)
        "0A" to true, // true (uppercase)
        "aB" to false, // false (mixed case)
        "Ab" to false, // false (mixed case)
        "deadbeef" to true, // true (lowercase)
        "DEADBEEF" to true, // true (uppercase)
        "DeadBeef" to false, // false (mixed case)
        "123456" to true, // true (only digits)
        "123abc" to true, // true (lowercase)
        "123ABC" to true, // true (uppercase)
        "123aBc" to false, // false (mixed case)
        "123AbC" to false, // false (mixed case)
        "0xDEADBEEF" to false, // false (contains 'x')
        "Hello" to false, // false (contains invalid chars)
        "12345" to false, // false (odd length)
        "12345G" to false, // false (contains 'G')
    )

    @Test
    fun testHexMatching() {
        testStrings.forEach { (hex, expected) ->
            check(hex.matchesHex() == expected) { "Invalid hex matching for: $expected" }
        }
    }



}
