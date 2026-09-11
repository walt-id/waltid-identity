@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.proximity

import id.walt.mdoc.objects.engagement.BleCentralMode
import id.walt.mdoc.objects.engagement.BlePeripheralMode
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborBoolean
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborFloat
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborNull
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.CborUndefined

/** Typed proximity copies of mutable shared DTOs; authoritative wire encodings remain separate. */
internal fun DeviceRetrievalMethod.snapshot(): DeviceRetrievalMethod = when (this) {
    is DeviceRetrievalMethod.Nfc -> copy(extensions = extensions.mapValues { it.value.snapshot() })
    is DeviceRetrievalMethod.Ble -> copy(
        peripheralMode = peripheralMode?.let { BlePeripheralMode(it.uuid.copyOf(), it.deviceAddress?.copyOf(), it.psm) },
        centralMode = centralMode?.let { BleCentralMode(it.uuid.copyOf()) },
        extensions = extensions.mapValues { it.value.snapshot() },
    )
    is DeviceRetrievalMethod.WifiAware -> DeviceRetrievalMethod.WifiAware(
        passphraseInfo, operatingClass, channelNumber, supportedBands.copyOf(), extensions.mapValues { it.value.snapshot() },
    )
    is DeviceRetrievalMethod.Unknown -> copy(encodedOptions = encodedOptions.snapshot())
}

private fun CborElement.snapshot(): CborElement {
    val tags = tags.toULongArray()
    return when (this) {
        is CborArray -> CborArray(map { it.snapshot() }, *tags)
        is CborMap -> CborMap(entries.associate { it.key.snapshot() to it.value.snapshot() }, *tags)
        is CborByteString -> CborByteString(toByteArray(), *tags)
        is CborBoolean -> CborBoolean(value, *tags)
        is CborFloat -> CborFloat(value, *tags)
        is CborInteger -> CborInteger(absoluteValue, isPositive, *tags)
        is CborString -> CborString(value, *tags)
        is CborNull -> CborNull(*tags)
        is CborUndefined -> CborUndefined(*tags)
    }
}
