@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.mso

import cbor.Cbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentifierListInfoTest {

    @Test
    fun testHappyPathSerialize() {

        val payloads = listOf(
            IdentifierListInfo(
                id = ByteArray(5),
                uri = "https://some-example.com",
            ),
            IdentifierListInfo(
                id = ByteArray(0),
                uri = "https://some-example.com",
            ),
            IdentifierListInfo(
                id = ByteArray(5),
                uri = "https://some-example.com",
                certificate = ByteArray(5)
            ),
            IdentifierListInfo(
                id = ByteArray(5),
                uri = "https://some-example.com",
                certificate = ByteArray(0)
            ),
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = IdentifierListInfo.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = IdentifierListInfo.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = IdentifierListInfo.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<IdentifierListInfo>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<IdentifierListInfo>(payload.toCBORHex()),
            )
        }
    }
}