@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.mso

import cbor.Cbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusListInfoTest {

    @Test
    fun testHappyPathSerialize() {
        val payloads = listOf(
            StatusListInfo(
                index = 5U,
                uri = "https://some-example.com",
            ),
            StatusListInfo(
                index = 5U,
                uri = "https://some-example.com",
                certificate = ByteArray(5)
            ),
            StatusListInfo(
                index = 5U,
                uri = "https://some-example.com",
                certificate = ByteArray(0)
            ),
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = StatusListInfo.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = StatusListInfo.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = StatusListInfo.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<StatusListInfo>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<StatusListInfo>(payload.toCBORHex()),
            )
        }
    }
}