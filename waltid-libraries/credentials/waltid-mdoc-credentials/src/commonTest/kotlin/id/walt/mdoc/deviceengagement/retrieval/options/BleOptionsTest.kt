@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement.retrieval.options

import cbor.Cbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class BleOptionsTest {
    
    @Test
    fun testHappyPathSerialize() {
        val payloads = listOf(
            BleOptions(
                supportMDocPeripheralServerMode = true,
                supportMDocCentralClientMode = false,
            ),
            BleOptions(
                supportMDocPeripheralServerMode = true,
                supportMDocCentralClientMode = false,
                mdocCentralClientModeUUID = ByteArray(7),
            ),
            BleOptions(
                supportMDocPeripheralServerMode = true,
                supportMDocCentralClientMode = false,
                mdocCentralClientModeUUID = ByteArray(7),
                mdocPeripheralServerModeUUID = ByteArray(9),
            ),
            BleOptions(
                supportMDocPeripheralServerMode = true,
                supportMDocCentralClientMode = false,
                mdocCentralClientModeUUID = ByteArray(7),
                mdocPeripheralServerModeUUID = ByteArray(9),
                mdocPeripheralServerModeL2CAPPSM = 7U,
            ),
            BleOptions(
                supportMDocPeripheralServerMode = true,
                supportMDocCentralClientMode = false,
                mdocCentralClientModeUUID = ByteArray(7),
                mdocPeripheralServerModeUUID = ByteArray(9),
                mdocPeripheralServerModeL2CAPPSM = 7U,
                mdocPeripheralServerModeDeviceAddress = ByteArray(11),
            ),
        )
        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = BleOptions.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = BleOptions.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = BleOptions.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<BleOptions>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<BleOptions>(payload.toCBORHex()),
            )
        }
    }
}