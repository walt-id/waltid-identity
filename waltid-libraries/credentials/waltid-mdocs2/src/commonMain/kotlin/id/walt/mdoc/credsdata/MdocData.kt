@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.credsdata

import kotlinx.serialization.ExperimentalSerializationApi

//
///**
// * Abstract interface for different mdoc document types.
// */
sealed interface MdocData {

}
//
//typealias NameSpace = String
//typealias DigestID = Long
//typealias DataElementIdentifier = String
//typealias DocumentType = String
//
//typealias IssuerNameSpaces = Map<NameSpace, List<IssuerSignedItem>>
//
///**
// * Represents the issuer-signed portion of an mdoc document.
// *
// * @property nameSpaces A map of namespaces to their corresponding issuer-signed items.
// * @property issuerAuth The issuer's signature over the Mobile Security Object (MSO).
// */
//@Serializable
//data class IssuerSigned(
//    @SerialName("nameSpaces")
//    @Serializable(with = NamespacedIssuerSignedListSerializer::class)
//    val namespaces: Map<String, @Contextual IssuerSignedList>? = null,
//    val issuerAuth: CoseSign1
//) {
//    fun fromIssuerSignedItems(
//        namespacedItems: Map<String, List<IssuerSignedItem>>,
//        issuerAuth: CoseSign1,
//    ): IssuerSigned = IssuerSigned(
//        namespaces = namespacedItems.map { (namespace, value) ->
//            namespace to IssuerSignedList.fromIssuerSignedItems(value, namespace)
//        }.toMap(),
//        issuerAuth = issuerAuth,
//    )
//}
//
///**
// * Custom serializer for IssuerSignedItem to handle the #6.24(bstr) wrapping.
// */
//object IssuerSignedItemSerializer : KSerializer<IssuerSignedItem> {
//    @Serializable
//    @JvmInline
//    private value class IssuerSignedItemBytesSurrogate(@CborLabel(24) val bytes: ByteArray)
//
//    @Serializable
//    private class IssuerSignedItemSurrogate(
//        val digestID: Long,
//        val random: ByteArray,
//        val elementIdentifier: String,
//        val elementValue: JsonElement
//    )
//
//    private val valueSerializer = IssuerSignedItemBytesSurrogate.serializer()
//    private val dataSerializer = IssuerSignedItemSurrogate.serializer()
//
//    override val descriptor: SerialDescriptor = dataSerializer.descriptor
//
//    override fun serialize(encoder: Encoder, value: IssuerSignedItem) {
//        val surrogate = IssuerSignedItemSurrogate(value.digestID, value.random, value.elementIdentifier, value.elementValue)
//        val bytes = MdocCbor.encodeToByteArray(dataSerializer, surrogate)
//        encoder.encodeSerializableValue(valueSerializer, IssuerSignedItemBytesSurrogate(bytes))
//    }
//
//    override fun deserialize(decoder: Decoder): IssuerSignedItem {
//        val surrogateBytes = decoder.decodeSerializableValue(valueSerializer)
//        val surrogate = MdocCbor.decodeFromByteArray(dataSerializer, surrogateBytes.bytes)
//        return IssuerSignedItem(surrogate.digestID, surrogate.random, surrogate.elementIdentifier, surrogate.elementValue)
//    }
//}
//
//
//
///**
// * Wrapper for a map of namespaces to their respective data elements.
// */
//@Serializable
//data class CredentialData(
//    val issuerSigned: Map<NameSpace, List<IssuerSignedItem>>,
//    val deviceSigned: Map<NameSpace, Map<DataElementIdentifier, DataElementValue>>,
//)
//
///**
// * A wrapper for device-signed namespaces, used because the `nameSpaces` map in `DeviceSigned` is inside a tagged bytestring.
// */
//@Serializable
//data class DeviceNameSpaces(
//    @SerialName("nameSpaces") val nameSpaces: Map<NameSpace, Map<DataElementIdentifier, DataElementValue>>,
//)
//
///**
// * Represents a map of Digest IDs to their corresponding digest values.
// */
//typealias Digests = Map<DigestID, Digest>
//typealias Digest = ByteArray
//
///**
// * Represents a list of authorized data element identifiers.
// */
//typealias AuthorizedDataElements = List<DataElementIdentifier>
//
//
///**
// * Custom serializer for dates that need to be tagged as `full-date` (CBOR tag 1004).
// * This uses `@CborLabel` which is the correct annotation for tagging a surrounding element.
// */
//@Serializable
//@OptIn(ExperimentalSerializationApi::class)
//private data class FullDateSurrogate(@CborLabel(1004) val date: String)
