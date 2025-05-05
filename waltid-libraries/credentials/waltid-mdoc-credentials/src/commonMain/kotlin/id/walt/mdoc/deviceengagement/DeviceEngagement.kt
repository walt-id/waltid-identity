package id.walt.mdoc.deviceengagement

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.deviceengagement.retrieval.methods.device.DeviceRetrievalMethod
import id.walt.mdoc.deviceengagement.retrieval.methods.server.ServerRetrievalMethods
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = DeviceEngagementSerializer::class)
data class DeviceEngagement(
    val security: Security,
    val deviceRetrievalMethods: List<DeviceRetrievalMethod>? = null,
    val serverRetrievalMethods: ServerRetrievalMethods? = null,
    val protocolInfo: AnyDataElement? = null,
    val originInfos: List<OriginInfo>? = null, //key 5
    val capabilities: Capabilities? = null, //key 6
    val optional: Map<Int, AnyDataElement> = emptyMap(),
) {

    /**
     * Version: the version of the device engagement structure. When key 5 or 6 are present, the version
     * shall be “1.1”, otherwise the version shall be “1.0”.
     * */
    val version: String = if (originInfos != null && capabilities != null) "1.1" else "1.0"

    init {

        /**
         * If the ServerRetrievalMethods is present, the map shall contain webApi, oidc, or
         * both.
         * */
        serverRetrievalMethods?.let {
            require(serverRetrievalMethods.webAPI != null || serverRetrievalMethods.oidc != null) {
                "When the ServerRetrievalMethods structure is defined in a DeviceEngagement structure, it must contain " +
                        "a definition for either webApi, oidc, or both"
            }
        }

        /**
         * If Capabilities is present, OriginInfos shall be
         * present.
         * */
        capabilities?.let {
            require(originInfos != null) {
                "When the Capabilities structure is defined in a DeviceEngagement structure, the OriginInfos list must be present"
            }
        }

        deviceRetrievalMethods?.let {
            require(deviceRetrievalMethods.isNotEmpty()) {
                "DeviceRetrievalMethods should have at least one entry (method) when specified as part of the DeviceEngagement " +
                        "structure, otherwise, it should not be included in the structure."
            }

            /**
             * A DeviceRetrievalMethod with a particular combination of type and version shall only
             * be present once.
             * */
            require(
                it.map { deviceRetrievalMethod ->
                    deviceRetrievalMethod.type to deviceRetrievalMethod.version
                }.distinct().size == 1
            ) {
                "A DeviceRetrievalMethod with a particular combination of type and version shall only be present once, input " +
                        "list was found to contain at least one duplicate"
            }
        }

        require(optional.keys.none { it in reservedCBORMapIntKeys })
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceEngagement) return false

        if (version != other.version) return false
        if (security != other.security) return false
        if (deviceRetrievalMethods != other.deviceRetrievalMethods) return false
        if (serverRetrievalMethods != other.serverRetrievalMethods) return false
        if (protocolInfo != other.protocolInfo) return false
        if (originInfos != other.originInfos) return false
        if (capabilities != other.capabilities) return false

        //for some reason the following shenanigans is needed otherwise we get false
        //when comparing two identical (in content) instances. Something fishy going
        //on with the equals() method of the DataElement 🤷
        if (optional.size != other.optional.size) return false

        optional.entries.forEach { curEntry ->
            if (!other.optional.containsKey(curEntry.key)) return false

            if (other.optional[curEntry.key]!!.toCBORHex() != curEntry.value.toCBORHex()) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + security.hashCode()
        result = 31 * result + (deviceRetrievalMethods?.hashCode() ?: 0)
        result = 31 * result + (serverRetrievalMethods?.hashCode() ?: 0)
        result = 31 * result + (protocolInfo?.hashCode() ?: 0)
        result = 31 * result + (originInfos?.hashCode() ?: 0)
        result = 31 * result + (capabilities?.hashCode() ?: 0)

        val optionalHash = optional.entries.fold(0) { acc, (key, value) ->
            acc + (key.hashCode() * 31 + value.hashCode())
        }
        result = 31 * result + optionalHash

        return result
    }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey(0), version.toDataElement())
            put(MapKey(1), security.toListElement())
            deviceRetrievalMethods?.let {
                put(MapKey(2), ListElement(it.map { it.toListElement() }))
            }
            serverRetrievalMethods?.let {
                put(MapKey(3), serverRetrievalMethods.toMapElement())
            }
            protocolInfo?.let {
                put(MapKey(4), protocolInfo)
            }
            originInfos?.let {
                put(MapKey(5), ListElement(it.map { it.toMapElement() }))
            }
            capabilities?.let {
                put(MapKey(6), capabilities.toMapElement())
            }
            optional.takeIf { it.isNotEmpty() }?.entries?.forEach { (key, value) ->
                put(MapKey(key), value)
            }
        }
    )

    /**
     * Serialize to CBOR data
     */
    fun toCBOR() = toMapElement().toCBOR()

    /**
     * Serialize to CBOR hex string
     */
    fun toCBORHex() = toMapElement().toCBORHex()

    companion object {

        private val reservedCBORMapIntKeys = setOf(0, 1, 2, 3, 4, 5, 6)

        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DeviceEngagement>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DeviceEngagement>(cbor)

        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(element: MapElement): DeviceEngagement {
            require(element.value.containsKey(MapKey(0))) {
                "DeviceEngagement CBOR map must contain key 0 for the value of the version field"
            }
            require(element.value.containsKey(MapKey(1))) {
                "DeviceEngagement CBOR map must contain key 1 for the value of the Security structure"
            }
            return DeviceEngagement(
                security = Security.fromListElement((element.value[MapKey(1)] as ListElement)),
                deviceRetrievalMethods = element.value[MapKey(2)]?.let {
                    (it as ListElement).value.map { DeviceRetrievalMethod.fromListElement(it as ListElement) }
                },
                serverRetrievalMethods = element.value[MapKey(3)]?.let {
                    ServerRetrievalMethods.fromMapElement(it as MapElement)
                },
                protocolInfo = element.value[MapKey(4)],
                originInfos = element.value[MapKey(5)]?.let {
                    (it as ListElement).value.map { OriginInfo.fromMapElement(it as MapElement) }
                },
                capabilities = element.value[MapKey(6)]?.let {
                    Capabilities.fromMapElement(it as MapElement)
                },
                optional = element
                    .value
                    .filter {
                        it.key.int !in reservedCBORMapIntKeys
                    }.takeIf {
                        it.isNotEmpty()
                    }?.entries?.associate { it.key.int to it.value } ?: emptyMap(),
            )
        }

    }

}

internal object DeviceEngagementSerializer : KSerializer<DeviceEngagement> {

    override val descriptor = buildClassSerialDescriptor("DeviceEngagement")

    override fun serialize(encoder: Encoder, value: DeviceEngagement) {
        encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
    }

    override fun deserialize(decoder: Decoder): DeviceEngagement {
        return DeviceEngagement.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
    }
}
