import id.walt.mdoc.encoding.validateMdocCbor
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MdocCborValidationTest {
    @Test
    fun acceptsUnambiguousContainersWithoutReencoding() {
        listOf(
            "a2616101616202", // definite map
            "bf616202616101ff", // reversed indefinite map
            "82a1616101a1616102", // same key in separate maps
            "a20101181802", // distinct integer keys
            "a16161420102", // opaque bytes are not decoded as CBOR
            "d81844a1616101", // embedded CBOR
            "7f61616162ff", // indefinite text
            "5f41014102ff", // indefinite byte string
            "f93c00", // floating point
        ).forEach { validateMdocCbor(it.hexToByteArray()) }
    }

    @Test
    fun rejectsDuplicateKeysBeforeMapsCanOverwriteThem() {
        listOf(
            "a2616101616102", // definite map
            "bf616101616102ff", // indefinite map
            "a20101180102", // integer key encoded with different widths
            "81a2616101616102", // nested map
            "d81847a2616101616102", // duplicates inside tag 24
        ).forEach { encoded ->
            assertFailsWith<IllegalArgumentException>(encoded) { validateMdocCbor(encoded.hexToByteArray()) }
        }
    }

    @Test
    fun rejectsTruncationTrailingDataAndMalformedContainers() {
        listOf("", "a1", "a16161", "0000", "ff", "bf6161ff", "d81800", "5f6161ff", "9f").forEach { encoded ->
            assertFailsWith<IllegalArgumentException>(encoded) { validateMdocCbor(encoded.hexToByteArray()) }
        }
    }

    @Test
    fun rejectsExcessiveNesting() {
        assertFailsWith<IllegalArgumentException> { validateMdocCbor(("81".repeat(65) + "00").hexToByteArray()) }
    }
}
