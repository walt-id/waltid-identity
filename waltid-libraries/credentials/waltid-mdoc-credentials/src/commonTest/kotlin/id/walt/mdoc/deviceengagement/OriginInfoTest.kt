@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement

import cbor.Cbor
import id.walt.mdoc.dataelement.toDataElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class OriginInfoTest {

    @Test
    fun testHappyPathSerialize() {
        val payloads = listOf(
            OriginInfo(
                category = 5u,
                type = 7u,
                details = mapOf(
                    "asdf" to (5).toDataElement(),
                ),
            ),
            OriginInfo(
                category = 4u,
                type = 11u,
                details = mapOf(
                    "asdf" to (5).toDataElement(),
                    "tit" to "tat".toDataElement(),
                ),
            )
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = OriginInfo.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = OriginInfo.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = OriginInfo.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<OriginInfo>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<OriginInfo>(payload.toCBORHex()),
            )
        }
    }

    @Test
    fun testInvalidPayloads() {
        assertFails {

            OriginInfo(
                category = 5u,
                type = 7u,
                details = emptyMap(),
            )

        }

    }
}