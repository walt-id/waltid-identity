@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement.retrieval.methods

import cbor.Cbor
import id.walt.mdoc.deviceengagement.retrieval.methods.server.ServerRetrievalInformation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerRetrievalInformationTest {

    @Test
    fun testHappyPathSerialize() {
        val payloads = listOf(
            ServerRetrievalInformation(
                issuerURL = "https://example.com",
                serverRetrievalToken = "some-random-token",
            ),
            ServerRetrievalInformation(
                version = 5U,
                issuerURL = "https://example-123.com",
                serverRetrievalToken = "some-random-token-123",
            ),
        )
        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = ServerRetrievalInformation.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = ServerRetrievalInformation.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = ServerRetrievalInformation.fromListElement(payload.toListElement())
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<ServerRetrievalInformation>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<ServerRetrievalInformation>(payload.toCBORHex()),
            )

        }
    }
}