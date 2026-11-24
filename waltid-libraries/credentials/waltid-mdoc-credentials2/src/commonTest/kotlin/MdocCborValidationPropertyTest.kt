@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.validateMdocCbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborBoolean
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborFloat
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborNull
import kotlinx.serialization.cbor.CborString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class MdocCborValidationPropertyTest {
    private val random = Random(1608)
    private val encodings = listOf(true, false).map { definite ->
        Cbor(coseCompliantCbor) { useDefiniteLengthEncoding = definite }
    }

    private fun value(depth: Int = 0): CborElement = when (random.nextInt(if (depth < 4) 8 else 6)) {
        0 -> CborNull()
        1 -> CborBoolean(random.nextBoolean())
        2 -> CborInteger(random.nextLong())
        3 -> CborFloat(random.nextDouble(-1e100, 1e100))
        4 -> CborString(listOf("", "ASCII", "a\u0000b", "\u00e9\u6f22\ud83d\ude00")[random.nextInt(4)])
        5 -> CborByteString(random.nextBytes(random.nextInt(65)))
        6 -> CborArray(List(random.nextInt(5)) { value(depth + 1) })
        else -> CborMap((0 until random.nextInt(5)).associate { CborString("key-$it") to value(depth + 1) })
    }

    @Test
    fun acceptsGeneratedValidValuesInBothLengthEncodingsWithoutMutation() {
        repeat(2_000) {
            val value = value()
            encodings.forEach { encoding ->
                val encoded = encoding.encodeToByteArray(CborElement.serializer(), value)
                val original = encoded.copyOf()
                validateMdocCbor(encoded)
                assertContentEquals(original, encoded)
            }
        }
    }

    @Test
    fun rejectsEveryTruncationOfGeneratedCompleteValues() {
        repeat(200) {
            val encoded = encodings[it % encodings.size].encodeToByteArray(CborElement.serializer(), value())
            for (size in encoded.indices) {
                assertFailsWith<IllegalArgumentException>("sample $it prefix $size") {
                    validateMdocCbor(encoded.copyOf(size))
                }
            }
        }
    }

    @Test
    fun arbitrarySmallInputsCannotCrashOrEscapeTheInputErrorContract() {
        repeat(20_000) {
            val bytes = random.nextBytes(random.nextInt(129))
            try {
                validateMdocCbor(bytes)
            } catch (_: IllegalArgumentException) {
                // Rejection is expected; implementation faults and other exception types fail this test.
            }
        }
    }

    @Test
    fun acceptsBoundaryScalarsAndTaggedValues() {
        listOf(
            "1bffffffffffffffff", "3bffffffffffffffff", "f97c00", "f9fc00", "f97e00",
            "a11bffffffffffffffff00", "a13bffffffffffffffff00", "c06161", "d86401",
            "d818435f40ff", "d8185f42a161426101ff",
        ).forEach { validateMdocCbor(it.hexToByteArray()) }
    }

    @Test
    fun rejectsDuplicateKeysAcrossStringChunkingVariants() {
        listOf("a26161017f6161ff02", "a24161015f4161ff02", "a20100180101", "a22000380001").forEach {
            assertFailsWith<IllegalArgumentException> { validateMdocCbor(it.hexToByteArray()) }
        }
    }
}
