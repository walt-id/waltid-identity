@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement

import cbor.Cbor
import id.walt.mdoc.dataelement.EncodedCBORElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class SecurityTest {
    
    @Test
    fun testHappyPathSerialize() {

        val payloads = listOf(
            Security(
                cipherSuite = 1,
                eDeviceKeyBytes = EncodedCBORElement(ByteArray(5)),
            ),
            Security(
                cipherSuite = 5,
                eDeviceKeyBytes = EncodedCBORElement(ByteArray(9)),
            ),
            Security(
                cipherSuite = 5,
                eDeviceKeyBytes = EncodedCBORElement(ByteArray(0)),
            )


        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = Security.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Security.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = Security.fromListElement(payload.toListElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<Security>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<Security>(payload.toCBORHex()),
            )
        }
    }
}