@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement

import cbor.Cbor
import id.walt.mdoc.dataelement.toDataElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderEngagementTest {

    @Test
    fun testHappyPathSerialize() {
        val payloads = listOf(
            ReaderEngagement(),
            ReaderEngagement(
                version = "asdf",
            ),
            ReaderEngagement(
                optional = mapOf(
                    4 to 5.toDataElement(),
                ),
            ),
            ReaderEngagement(
                optional = mapOf(
                    5 to "asdfasdf".toDataElement(),
                    4 to 5.toDataElement(),
                ),
            ),
            ReaderEngagement(
                version = "asdf",
                optional = mapOf(
                    4 to 5.toDataElement(),
                ),
            ),
            ReaderEngagement(
                version = "asdf",
                optional = mapOf(
                    5 to "asdfasdf".toDataElement(),
                    4 to 5.toDataElement(),
                ),
            ),
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = ReaderEngagement.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = ReaderEngagement.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = ReaderEngagement.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<ReaderEngagement>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<ReaderEngagement>(payload.toCBORHex()),
            )
        }
    }
}