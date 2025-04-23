@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement.retrieval.methods

import cbor.Cbor
import id.walt.mdoc.deviceengagement.retrieval.methods.server.ServerRetrievalInformation
import id.walt.mdoc.deviceengagement.retrieval.methods.server.ServerRetrievalMethods
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerRetrievalMethodsTest {

    @Test
    fun testHappyPathSerialize() {
        val payloads = listOf(
            ServerRetrievalMethods(),
            ServerRetrievalMethods(
                webAPI = ServerRetrievalInformation(
                    issuerURL = "https://example-web-api.com",
                    serverRetrievalToken = "some-web-api-token",
                )
            ),
            ServerRetrievalMethods(
                oidc = ServerRetrievalInformation(
                    issuerURL = "https://example-oidc.com",
                    serverRetrievalToken = "some-oidc-token",
                )
            ),
            ServerRetrievalMethods(
                webAPI = ServerRetrievalInformation(
                    issuerURL = "https://example-web-api.com",
                    serverRetrievalToken = "some-web-api-token",
                ),
                oidc = ServerRetrievalInformation(
                    issuerURL = "https://example-oidc.com",
                    serverRetrievalToken = "some-oidc-token",
                ),
            ),
        )
        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = ServerRetrievalMethods.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = ServerRetrievalMethods.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = ServerRetrievalMethods.fromMapElement(payload.toMapElement())
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<ServerRetrievalMethods>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<ServerRetrievalMethods>(payload.toCBORHex()),
            )

        }
    }
}