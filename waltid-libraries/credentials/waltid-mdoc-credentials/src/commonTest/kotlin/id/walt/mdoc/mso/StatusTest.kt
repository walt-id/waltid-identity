@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.mso

import cbor.Cbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusTest {

    @Test
    fun testHappyPathSerialize() {

        val payloads = listOf(
            Status(),
            Status(
                identifierList = IdentifierListInfo(
                    id = ByteArray(5),
                    uri = "https://some-example.com",
                ),
            ),
            Status(
                statusList = StatusListInfo(
                    index = 5U,
                    uri = "https://some-example.com",
                    certificate = ByteArray(5)
                ),
            ),
            Status(
                identifierList = IdentifierListInfo(
                    id = ByteArray(5),
                    uri = "https://some-example.com",
                ),
                statusList = StatusListInfo(
                    index = 5U,
                    uri = "https://some-example.com",
                    certificate = ByteArray(5)
                ),
            ),
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = Status.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Status.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = Status.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<Status>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<Status>(payload.toCBORHex()),
            )
        }
    }
}