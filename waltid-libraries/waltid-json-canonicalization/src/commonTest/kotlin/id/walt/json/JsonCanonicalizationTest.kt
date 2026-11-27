package id.walt.json

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonCanonicalizationTest {
    // RFC 8785 Appendix B, IEEE 754 bit patterns and their independently specified JSON representations.
    private val appendixBVectors = mapOf(
        "0000000000000000" to "0", "8000000000000000" to "0",
        "0000000000000001" to "5e-324", "8000000000000001" to "-5e-324",
        "7fefffffffffffff" to "1.7976931348623157e+308", "ffefffffffffffff" to "-1.7976931348623157e+308",
        "4340000000000000" to "9007199254740992", "c340000000000000" to "-9007199254740992",
        "4430000000000000" to "295147905179352830000",
        "44b52d02c7e14af5" to "9.999999999999997e+22", "44b52d02c7e14af6" to "1e+23",
        "44b52d02c7e14af7" to "1.0000000000000001e+23",
        "444b1ae4d6e2ef4e" to "999999999999999700000", "444b1ae4d6e2ef4f" to "999999999999999900000",
        "444b1ae4d6e2ef50" to "1e+21", "3eb0c6f7a0b5ed8c" to "9.999999999999997e-7",
        "3eb0c6f7a0b5ed8d" to "0.000001", "41b3de4355555553" to "333333333.3333332",
        "41b3de4355555554" to "333333333.33333325", "41b3de4355555555" to "333333333.3333333",
        "41b3de4355555556" to "333333333.3333334", "41b3de4355555557" to "333333333.33333343",
        "becbf647612f3696" to "-0.0000033333333333333333", "43143ff3c1cb0959" to "1424953923781206.2",
    )

    @Test
    fun formatsTheIndependentRfc8785AppendixBBinary64Numbers() {
        val format = jsonNumberFormatter()
        for ((bits, expected) in appendixBVectors) {
            assertEquals(expected, format(Double.fromBits(bits.toULong(16).toLong())), bits)
        }
    }

    @Test
    fun canonicalizesTheIndependentRfc8785AppendixBJsonNumbers() {
        // JsonPrimitive(Double) snapshots platform Double.toString(), which can lose the intended bits on Native.
        // Feed exact JSON text to test parsing and canonicalization independently of that constructor.
        for ((bits, expected) in appendixBVectors) {
            assertEquals(expected, JsonCanonicalization.getCanonicalString(expected), bits)
        }
        assertEquals("295147905179352830000", JsonCanonicalization.getCanonicalString("295147905179352830000"))
        assertEquals("100000000000000000000", JsonCanonicalization.getCanonicalString("1e20"))
        assertEquals("0.000001", JsonCanonicalization.getCanonicalString("1e-6"))
        assertEquals("0", JsonCanonicalization.getCanonicalString("-0"))
        assertEquals("""{"n":295147905179352830000}""",
            JsonCanonicalization.getCanonicalString("""{"n":295147905179352830000}"""))
    }

    @Test
    fun sortsNestedPropertiesAndPreservesArrayOrder() {
        assertEquals("""{"a":{"c":1,"d":2},"z":[2,1]}""",
            JsonCanonicalization.getCanonicalString("""{"z":[2,1],"a":{"d":2,"c":1}}"""))
    }

    @Test
    fun rejectsDuplicatePropertiesInvalidUnicodeAndNonFiniteNumbers() {
        for (json in listOf("""{"a":1,"a":2}""", """{"a":1,"\u0061":2}""", """{"nested":{"a":1,"a":2}}""")) {
            assertFailsWith<IllegalArgumentException> { JsonCanonicalization.getCanonicalString(json) }
        }
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.canonicalize(JsonPrimitive("\uDEAD")) }
        for (number in listOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            assertFailsWith<IllegalArgumentException> { JsonCanonicalization.canonicalize(JsonPrimitive(number)) }
        }
    }
}
