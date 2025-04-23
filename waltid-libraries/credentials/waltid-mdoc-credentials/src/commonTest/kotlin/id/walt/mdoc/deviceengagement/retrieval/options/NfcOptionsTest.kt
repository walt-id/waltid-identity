@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement.retrieval.options

import cbor.Cbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class NfcOptionsTest {
    
    @Test
    fun testHappyPathSerialize() {
        val payloads = listOf(
            NfcOptions(
                commandDataFieldMaxLength = 5u,
                responseDataFieldMaxLength = 5u,
            ),
            NfcOptions(
                commandDataFieldMaxLength = 4u,
                responseDataFieldMaxLength = 5u,
            ),
            NfcOptions(
                commandDataFieldMaxLength = 5u,
                responseDataFieldMaxLength = 7u,
            ),
        )
        payloads.forEach { payload ->
            assertEquals(
                expected = payload,
                actual = NfcOptions.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = NfcOptions.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = NfcOptions.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<NfcOptions>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<NfcOptions>(payload.toCBORHex()),
            )
        }

    }
}