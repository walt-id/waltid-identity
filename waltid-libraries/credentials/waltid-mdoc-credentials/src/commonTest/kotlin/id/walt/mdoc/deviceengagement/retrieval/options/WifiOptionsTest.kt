@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.deviceengagement.retrieval.options

import cbor.Cbor
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.toDataElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class WifiOptionsTest {

    @Test
    fun testHappyPathSerialize() {
        val bandInfo = ByteArray(5) { (it * 3).toByte() }
        val passPhrase = "some-pass"
        val payloads = listOf(
            WifiOptions(
                bandInfoSupportedBands = bandInfo,
            ),
            WifiOptions(
                passPhrase = passPhrase,
                bandInfoSupportedBands = bandInfo,
            ),
            WifiOptions(
                channelInfoOperatingClass = 5u,
                bandInfoSupportedBands = bandInfo,
            ),
            WifiOptions(
                channelInfoChannelNumber = 5u,
                bandInfoSupportedBands = bandInfo,
            ),
            WifiOptions(
                passPhrase = passPhrase,
                channelInfoOperatingClass = 7u,
                bandInfoSupportedBands = bandInfo,
            ),
            WifiOptions(
                passPhrase = passPhrase,
                channelInfoChannelNumber = 9u,
                bandInfoSupportedBands = bandInfo,
            ),
            WifiOptions(
                passPhrase = passPhrase,
                channelInfoOperatingClass = 7u,
                channelInfoChannelNumber = 9u,
                bandInfoSupportedBands = bandInfo,
            ),
        )
        payloads.forEach { payload ->
            assertEquals(
                expected = payload,
                actual = WifiOptions.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = WifiOptions.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = WifiOptions.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<WifiOptions>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<WifiOptions>(payload.toCBORHex()),
            )
        }
    }

    @Test
    fun testInvalidPayloads() {
        val payloads = listOf<DataElement>(
            MapElement(
              buildMap {
                  put(MapKey(0), "asdf".toDataElement())
              }
            ),
            MapElement(
                buildMap {
                    put(MapKey(1), "asdf".toDataElement())
                    put(MapKey(3), "asdf".encodeToByteArray().toDataElement())
                }
            )
        )
        payloads.forEach { payload ->

            assertFails {
                WifiOptions.fromCBOR(payload.toCBOR())
            }

            assertFails {
                WifiOptions.fromCBORHex(payload.toCBORHex())
            }

            assertFails {
                Cbor.decodeFromByteArray<WifiOptions>(payload.toCBOR())
            }

            assertFails {
                Cbor.decodeFromHexString<WifiOptions>(payload.toCBORHex())
            }
        }
    }
}