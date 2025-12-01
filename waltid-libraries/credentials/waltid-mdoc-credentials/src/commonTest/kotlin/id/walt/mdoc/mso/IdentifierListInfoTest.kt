@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.mso

import cbor.Cbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

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

    @Test
    fun invalidPayloadsAsHexStrings() {

        listOf(
            "bf6375726964616c6c6fff", // { "uri": "allo"}
            "bf62696464616c6c6fff", //{ "id": "allo"}
            "bf626964646b6174696375726964616c6c6fff", // { "id": "kati", "uri": "allo"}
            "bf62696464616c6c6f6375726905ff", // { "id": "allo", "uri": 5}
        ).forEach {
            assertFails {
                IdentifierListInfo.fromCBORHex(it)
            }.let { throwable ->
                assertIs<IllegalArgumentException>(throwable)
            }
        }
    }
}