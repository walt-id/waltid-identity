@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement.retrieval.methods

import cbor.Cbor
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.deviceengagement.retrieval.methods.device.DeviceRetrievalMethodType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DeviceRetrievalMethodTypeTest {

    @Test
    fun testHappyPathSerialize() {
        val payloads = listOf(
            DeviceRetrievalMethodType.BLE,
            DeviceRetrievalMethodType.NFC,
            DeviceRetrievalMethodType.WIFI
        )
        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = DeviceRetrievalMethodType.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = DeviceRetrievalMethodType.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = DeviceRetrievalMethodType.fromNumberElement(payload.toNumberElement())
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<DeviceRetrievalMethodType>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<DeviceRetrievalMethodType>(payload.toCBORHex()),
            )

        }
    }

    @Test
    fun testInvalidPayloads() {
        val payloads = listOf(
            (5u).toDataElement(),
            "asdf".toDataElement(),
            ByteArray(7).toDataElement(),
        )

        payloads.forEach { payload ->

            assertFails {
                DeviceRetrievalMethodType.fromCBOR(payload.toCBOR())
            }

            assertFails {
                DeviceRetrievalMethodType.fromCBORHex(payload.toCBORHex())
            }

            assertFails {
                Cbor.decodeFromByteArray<DeviceRetrievalMethodType>(payload.toCBOR())
            }

            assertFails {
                Cbor.decodeFromHexString<DeviceRetrievalMethodType>(payload.toCBORHex())
            }
        }
    }
}