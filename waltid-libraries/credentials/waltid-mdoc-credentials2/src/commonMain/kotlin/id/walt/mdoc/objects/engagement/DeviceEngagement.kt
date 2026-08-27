@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.objects.engagement

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.MdocVersion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborBoolean
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** ISO/IEC 18013-5 DeviceEngagement with exact, typed retrieval methods and permitted extensions. */
@kotlinx.serialization.Serializable(with = DeviceEngagementSerializer::class)
data class DeviceEngagement(
    val version: String,
    val security: DeviceEngagementSecurity,
    val deviceRetrievalMethods: List<DeviceRetrievalMethod>? = null,
    val originInfos: List<CborElement>? = null,
    val capabilities: DeviceEngagementCapabilities? = null,
    val extensions: Map<Long, CborElement> = emptyMap(),
) {
    init {
        val parsedVersion = MdocVersion.parse(version)
        require(parsedVersion.major == 1u) { "Unsupported DeviceEngagement major version" }
        if (parsedVersion.minor <= 1u) {
            require(version == if (originInfos != null || capabilities != null) VERSION_1_1 else VERSION_1_0) {
                "DeviceEngagement version does not match its edition-2 fields"
            }
        }
        require(capabilities == null || originInfos != null) {
            "OriginInfos must be present when DeviceEngagement capabilities are present"
        }
        require(deviceRetrievalMethods == null || deviceRetrievalMethods.isNotEmpty()) {
            "DeviceRetrievalMethods must be non-empty when present"
        }
        require(deviceRetrievalMethods.orEmpty().distinctBy { it.type to it.version }.size == deviceRetrievalMethods.orEmpty().size) {
            "A DeviceRetrievalMethod type/version pair may be advertised only once"
        }
        require(extensions.keys.none { it in setOf(0L, 1L, 2L, 5L, 6L) }) {
            "DeviceEngagement extension collides with a standard field"
        }
    }

    companion object {
        const val VERSION_1_0 = "1.0"
        const val VERSION_1_1 = "1.1"
    }
}

data class DeviceEngagementSecurity(
    val cipherSuite: UInt,
    val eDeviceKey: ByteStringWrapper<CoseKey>,
) {
    init {
        require(cipherSuite == 1u) { "Unsupported DeviceEngagement cipher suite: $cipherSuite" }
        require(eDeviceKey.serialized.isNotEmpty()) { "EDeviceKey must retain its exact encoded COSE_Key bytes" }
        require(eDeviceKey.value.d == null) { "DeviceEngagement must not contain private key material" }
    }
}

data class DeviceEngagementCapabilities(
    val handoverSessionEstablishment: Boolean = false,
    val readerAuthAll: Boolean = false,
    val extendedRequests: Boolean? = null,
    val extensions: Map<Long, CborElement> = emptyMap(),
) {
    init {
        require(extensions.keys.none { it in setOf(2L, 3L, 4L) }) {
            "DeviceEngagement capability extension collides with a standard field"
        }
    }
}

enum class DeviceRetrievalMethodType(val code: UInt) {
    NFC(1u),
    BLE(2u),
    WIFI_AWARE(3u),
}

data class DeviceRetrievalMethod(
    val type: UInt,
    val version: UInt,
    val options: RetrievalOptions,
) {
    init {
        require(version > 0u) { "DeviceRetrievalMethod version must be positive" }
        when (options) {
            is NfcRetrievalOptions -> require(type == DeviceRetrievalMethodType.NFC.code && version == 1u)
            is BleRetrievalOptions -> require(type == DeviceRetrievalMethodType.BLE.code && version == 1u)
            is WifiAwareRetrievalOptions -> require(type == DeviceRetrievalMethodType.WIFI_AWARE.code && version == 1u)
            is UnknownRetrievalOptions -> require(
                type !in DeviceRetrievalMethodType.entries.map { it.code } || version != 1u
            ) { "A supported type/version pair must use its typed retrieval options" }
        }
    }
}

sealed interface RetrievalOptions

data class BleRetrievalOptions(
    val peripheralServerModeSupported: Boolean,
    val centralClientModeSupported: Boolean,
    val peripheralServerModeUuid: ByteArray? = null,
    val centralClientModeUuid: ByteArray? = null,
    val peripheralServerModeDeviceAddress: ByteArray? = null,
    val peripheralServerModePsm: UInt? = null,
    val extensions: Map<UInt, CborElement> = emptyMap(),
) : RetrievalOptions {
    init {
        require(peripheralServerModeSupported || centralClientModeSupported) { "BLE must advertise at least one role" }
        require(peripheralServerModeUuid == null || peripheralServerModeUuid.size == 16) { "BLE UUID must be 16 bytes" }
        require(centralClientModeUuid == null || centralClientModeUuid.size == 16) { "BLE UUID must be 16 bytes" }
        require(peripheralServerModeDeviceAddress == null || peripheralServerModeDeviceAddress.size == 6) {
            "BLE device address must be 6 bytes"
        }
        require(peripheralServerModePsm == null || peripheralServerModeSupported) {
            "BLE L2CAP PSM requires peripheral mode"
        }
        require(extensions.keys.none { it in setOf(0u, 1u, 10u, 11u, 20u, 21u) }) {
            "BLE option extension collides with a standard field"
        }
    }

    override fun equals(other: Any?): Boolean = other is BleRetrievalOptions &&
        peripheralServerModeSupported == other.peripheralServerModeSupported &&
        centralClientModeSupported == other.centralClientModeSupported &&
        peripheralServerModeUuid.contentEquals(other.peripheralServerModeUuid) &&
        centralClientModeUuid.contentEquals(other.centralClientModeUuid) &&
        peripheralServerModeDeviceAddress.contentEquals(other.peripheralServerModeDeviceAddress) &&
        peripheralServerModePsm == other.peripheralServerModePsm && extensions == other.extensions

    override fun hashCode(): Int = listOf(
        peripheralServerModeSupported,
        centralClientModeSupported,
        peripheralServerModeUuid?.contentHashCode(),
        centralClientModeUuid?.contentHashCode(),
        peripheralServerModeDeviceAddress?.contentHashCode(),
        peripheralServerModePsm,
        extensions,
    ).hashCode()
}

data class NfcRetrievalOptions(
    val maximumCommandDataLength: UInt,
    val maximumResponseDataLength: UInt,
    val extensions: Map<UInt, CborElement> = emptyMap(),
) : RetrievalOptions {
    init {
        require(maximumCommandDataLength > 0u && maximumResponseDataLength > 0u) {
            "NFC command and response limits must be positive"
        }
        require(extensions.keys.none { it in setOf(0u, 1u) }) {
            "NFC option extension collides with a standard field"
        }
    }
}

data class WifiAwareRetrievalOptions(
    val passphraseInfo: String? = null,
    val operatingClass: UInt? = null,
    val channelNumber: UInt? = null,
    val supportedBands: ByteArray,
    val extensions: Map<UInt, CborElement> = emptyMap(),
) : RetrievalOptions {
    init {
        require(supportedBands.isNotEmpty()) { "Wi-Fi Aware supported bands must not be empty" }
        require(extensions.keys.none { it in setOf(0u, 1u, 2u, 3u) }) {
            "Wi-Fi Aware option extension collides with a standard field"
        }
    }

    override fun equals(other: Any?): Boolean = other is WifiAwareRetrievalOptions &&
        passphraseInfo == other.passphraseInfo && operatingClass == other.operatingClass &&
        channelNumber == other.channelNumber && supportedBands.contentEquals(other.supportedBands) &&
        extensions == other.extensions

    override fun hashCode(): Int = listOf(
        passphraseInfo,
        operatingClass,
        channelNumber,
        supportedBands.contentHashCode(),
        extensions,
    ).hashCode()
}

data class UnknownRetrievalOptions(val encoded: CborElement) : RetrievalOptions

object DeviceEngagementSerializer : KSerializer<DeviceEngagement> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DeviceEngagement) {
        val fields = linkedMapOf<CborElement, CborElement>()
        fields[CborInteger(0)] = CborString(value.version)
        fields[CborInteger(1)] = value.security.toElement()
        value.deviceRetrievalMethods?.let { methods ->
            fields[CborInteger(2)] = CborArray(methods.map(DeviceRetrievalMethod::toElement))
        }
        value.originInfos?.let { fields[CborInteger(5)] = CborArray(it) }
        value.capabilities?.let { fields[CborInteger(6)] = it.toElement() }
        value.extensions.forEach { (key, extension) -> fields[CborInteger(key)] = extension }
        encoder.encodeSerializableValue(CborElement.serializer(), CborMap(fields))
    }

    override fun deserialize(decoder: Decoder): DeviceEngagement {
        val map = decoder.decodeSerializableValue(CborElement.serializer()) as? CborMap
            ?: throw SerializationException("DeviceEngagement must be a CBOR map")
        val known = setOf(0L, 1L, 2L, 5L, 6L)
        return DeviceEngagement(
            version = map.requiredString(0),
            security = map.required(1).toSecurity(),
            deviceRetrievalMethods = map[2]?.let { element ->
                (element as? CborArray ?: throw SerializationException("DeviceRetrievalMethods must be an array"))
                    .map(CborElement::toRetrievalMethod)
            },
            originInfos = map[5]?.let { (it as? CborArray ?: throw SerializationException("OriginInfos must be an array")).toList() },
            capabilities = map[6]?.toCapabilities(),
            extensions = map.integerExtensions(known),
        )
    }
}

private fun DeviceEngagementSecurity.toElement(): CborElement = CborArray(
    listOf(
        CborInteger(cipherSuite.toULong()),
        CborByteString(eDeviceKey.serialized, 24u),
    )
)

private fun CborElement.toSecurity(): DeviceEngagementSecurity {
    val array = this as? CborArray ?: throw SerializationException("DeviceEngagement Security must be an array")
    if (array.size != 2) throw SerializationException("DeviceEngagement Security must contain two items")
    val encodedKey = (array[1] as? CborByteString)?.also {
        if (24uL !in it.tags) throw SerializationException("EDeviceKeyBytes must use CBOR tag 24")
    }?.toByteArray() ?: throw SerializationException("EDeviceKeyBytes must be a byte string")
    return DeviceEngagementSecurity(
        cipherSuite = array[0].requiredUInt(),
        eDeviceKey = ByteStringWrapper(
            value = coseCompliantCbor.decodeFromByteArray(CoseKey.serializer(), encodedKey),
            serialized = encodedKey,
        ),
    )
}

private fun DeviceEngagementCapabilities.toElement(): CborElement = CborMap(
    buildMap<CborElement, CborElement> {
        if (handoverSessionEstablishment) put(CborInteger(2), CborBoolean(true))
        if (readerAuthAll) put(CborInteger(3), CborBoolean(true))
        extendedRequests?.let { put(CborInteger(4), CborBoolean(it)) }
        extensions.forEach { (key, value) -> put(CborInteger(key), value) }
    }
)

private fun CborElement.toCapabilities(): DeviceEngagementCapabilities {
    val map = this as? CborMap ?: throw SerializationException("Capabilities must be a CBOR map")
    fun declaredTrue(key: Int): Boolean = map[key]?.let {
        (it as? CborBoolean)?.value?.also { value ->
            if (!value) throw SerializationException("Capability $key must be true when present")
        } ?: throw SerializationException("Capability $key must be a boolean")
    } ?: false
    return DeviceEngagementCapabilities(
        handoverSessionEstablishment = declaredTrue(2),
        readerAuthAll = declaredTrue(3),
        extendedRequests = map[4]?.let { (it as? CborBoolean)?.value ?: throw SerializationException("Capability 4 must be a boolean") },
        extensions = map.integerExtensions(setOf(2L, 3L, 4L)),
    )
}

private fun DeviceRetrievalMethod.toElement(): CborElement = CborArray(
    listOf(CborInteger(type.toULong()), CborInteger(version.toULong()), options.toElement())
)

private fun RetrievalOptions.toElement(): CborElement = when (this) {
    is BleRetrievalOptions -> CborMap(buildMap<CborElement, CborElement> {
        put(CborInteger(0), CborBoolean(peripheralServerModeSupported))
        put(CborInteger(1), CborBoolean(centralClientModeSupported))
        peripheralServerModeUuid?.let { put(CborInteger(10), CborByteString(it)) }
        centralClientModeUuid?.let { put(CborInteger(11), CborByteString(it)) }
        peripheralServerModeDeviceAddress?.let { put(CborInteger(20), CborByteString(it)) }
        peripheralServerModePsm?.let { put(CborInteger(21), CborInteger(it.toULong())) }
        extensions.forEach { (key, value) -> put(CborInteger(key.toULong()), value) }
    })
    is NfcRetrievalOptions -> CborMap(buildMap<CborElement, CborElement> {
        put(CborInteger(0), CborInteger(maximumCommandDataLength.toULong()))
        put(CborInteger(1), CborInteger(maximumResponseDataLength.toULong()))
        extensions.forEach { (key, value) -> put(CborInteger(key.toULong()), value) }
    })
    is WifiAwareRetrievalOptions -> CborMap(buildMap<CborElement, CborElement> {
        passphraseInfo?.let { put(CborInteger(0), CborString(it)) }
        operatingClass?.let { put(CborInteger(1), CborInteger(it.toULong())) }
        channelNumber?.let { put(CborInteger(2), CborInteger(it.toULong())) }
        put(CborInteger(3), CborByteString(supportedBands))
        extensions.forEach { (key, value) -> put(CborInteger(key.toULong()), value) }
    })
    is UnknownRetrievalOptions -> encoded
}

private fun CborElement.toRetrievalMethod(): DeviceRetrievalMethod {
    val array = this as? CborArray ?: throw SerializationException("DeviceRetrievalMethod must be an array")
    if (array.size != 3) throw SerializationException("DeviceRetrievalMethod must contain three items")
    val type = array[0].requiredUInt()
    val version = array[1].requiredUInt()
    val options = when {
        version != 1u -> UnknownRetrievalOptions(array[2])
        type == DeviceRetrievalMethodType.NFC.code -> array[2].toNfcOptions()
        type == DeviceRetrievalMethodType.BLE.code -> array[2].toBleOptions()
        type == DeviceRetrievalMethodType.WIFI_AWARE.code -> array[2].toWifiOptions()
        else -> UnknownRetrievalOptions(array[2])
    }
    return DeviceRetrievalMethod(type, version, options)
}

private fun CborElement.toBleOptions(): BleRetrievalOptions {
    val map = this as? CborMap ?: throw SerializationException("BLE options must be a map")
    return BleRetrievalOptions(
        peripheralServerModeSupported = map.requiredBoolean(0),
        centralClientModeSupported = map.requiredBoolean(1),
        peripheralServerModeUuid = map[10]?.requiredBytes(),
        centralClientModeUuid = map[11]?.requiredBytes(),
        peripheralServerModeDeviceAddress = map[20]?.requiredBytes(),
        peripheralServerModePsm = map[21]?.requiredUInt(),
        extensions = map.unsignedExtensions(setOf(0u, 1u, 10u, 11u, 20u, 21u)),
    )
}

private fun CborElement.toNfcOptions(): NfcRetrievalOptions {
    val map = this as? CborMap ?: throw SerializationException("NFC options must be a map")
    return NfcRetrievalOptions(
        maximumCommandDataLength = map.required(0).requiredUInt(),
        maximumResponseDataLength = map.required(1).requiredUInt(),
        extensions = map.unsignedExtensions(setOf(0u, 1u)),
    )
}

private fun CborElement.toWifiOptions(): WifiAwareRetrievalOptions {
    val map = this as? CborMap ?: throw SerializationException("Wi-Fi Aware options must be a map")
    return WifiAwareRetrievalOptions(
        passphraseInfo = map[0]?.let { (it as? CborString)?.value ?: throw SerializationException("Passphrase info must be text") },
        operatingClass = map[1]?.requiredUInt(),
        channelNumber = map[2]?.requiredUInt(),
        supportedBands = map.required(3).requiredBytes(),
        extensions = map.unsignedExtensions(setOf(0u, 1u, 2u, 3u)),
    )
}

private fun CborMap.required(key: Int): CborElement = this[key] ?: throw SerializationException("Missing CBOR map key $key")
private fun CborMap.requiredString(key: Int): String = (required(key) as? CborString)?.value
    ?: throw SerializationException("CBOR map key $key must be text")
private fun CborMap.requiredBoolean(key: Int): Boolean = (required(key) as? CborBoolean)?.value
    ?: throw SerializationException("CBOR map key $key must be a boolean")
private fun CborElement.requiredBytes(): ByteArray = (this as? CborByteString)?.toByteArray()
    ?: throw SerializationException("Expected a CBOR byte string")
private fun CborElement.requiredUInt(): UInt = (this as? CborInteger)?.let {
    if (!it.isPositive || it.absoluteValue > UInt.MAX_VALUE.toULong()) null else it.absoluteValue.toUInt()
} ?: throw SerializationException("Expected an unsigned 32-bit CBOR integer")

private fun CborMap.integerExtensions(known: Set<Long>): Map<Long, CborElement> = entries.associateNotNull { (key, value) ->
    val integer = key as? CborInteger ?: throw SerializationException("Expected an integer CBOR map key")
    val signed = integer.toSignedLong()
    if (signed in known) null else signed to value
}

private fun CborMap.unsignedExtensions(known: Set<UInt>): Map<UInt, CborElement> =
    entries.associateNotNull { (key, value) ->
        val integer = key as? CborInteger
            ?: throw SerializationException("Retrieval option keys must be unsigned integers")
        if (!integer.isPositive || integer.absoluteValue > UInt.MAX_VALUE.toULong()) {
            throw SerializationException("Retrieval option key is outside the unsigned 32-bit range")
        }
        integer.absoluteValue.toUInt().let { if (it in known) null else it.toLong() to value }
    }.mapKeys { it.key.toUInt() }

private fun CborInteger.toSignedLong(): Long {
    require(absoluteValue <= Long.MAX_VALUE.toULong()) { "CBOR map key is outside the supported range" }
    return if (isPositive) absoluteValue.toLong() else -absoluteValue.toLong()
}

private inline fun <K, V, R> Iterable<Map.Entry<K, V>>.associateNotNull(transform: (Map.Entry<K, V>) -> Pair<Long, R>?): Map<Long, R> =
    buildMap { for (entry in this@associateNotNull) transform(entry)?.let { put(it.first, it.second) } }
