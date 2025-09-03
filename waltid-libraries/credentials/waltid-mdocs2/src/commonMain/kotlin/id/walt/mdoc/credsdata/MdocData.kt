@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.credsdata

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel

/**
 * A sealed interface representing the typed data payload of an mdoc.
 * Specific document types like mDL and Photo ID will implement this interface.
 */
sealed interface MdocData

typealias DocumentType = String
typealias NameSpace = String
typealias DataElementIdentifier = String
typealias DataElementValue = @Serializable kotlinx.serialization.json.JsonElement
typealias DigestID = Long
typealias Digest = ByteArray

/**
 * Wrapper for a map of namespaces to their respective data elements.
 */
@Serializable
data class CredentialData(
    val issuerSigned: Map<NameSpace, List<IssuerSignedItem>>,
    val deviceSigned: Map<NameSpace, Map<DataElementIdentifier, DataElementValue>>,
)

/**
 * Represents the issuer-signed portion of an mdoc document.
 *
 * @property nameSpaces A map of namespaces to their corresponding issuer-signed items.
 * @property issuerAuth The issuer's signature over the Mobile Security Object (MSO).
 */
@Serializable
data class IssuerSigned(
    val nameSpaces: Map<NameSpace, List<IssuerSignedItem>>? = null,
    val issuerAuth: id.walt.cose.CoseSign1,
)

/**
 * Represents a single issuer-signed data element.
 *
 * @property digestID The ID used to match this item to a digest in the MSO.
 * @property random A random value to prevent collisions and pre-computation attacks.
 * @property elementIdentifier The identifier for the data element (e.g., "family_name").
 * @property elementValue The actual value of the data element.
 */
@Serializable
data class IssuerSignedItem(
    val digestID: DigestID,
    val random: ByteArray,
    val elementIdentifier: DataElementIdentifier,
    val elementValue: DataElementValue,
)

/**
 * A wrapper for device-signed namespaces, used because the `nameSpaces` map in `DeviceSigned` is inside a tagged bytestring.
 */
@Serializable
data class DeviceNameSpaces(
    @SerialName("nameSpaces") val nameSpaces: Map<NameSpace, Map<DataElementIdentifier, DataElementValue>>,
)

/**
 * Represents a map of Digest IDs to their corresponding digest values.
 */
typealias Digests = Map<DigestID, Digest>

/**
 * Represents a list of authorized data element identifiers.
 */
typealias AuthorizedDataElements = List<DataElementIdentifier>


/**
 * Custom serializer for dates that need to be tagged as `full-date` (CBOR tag 1004).
 * This uses `@CborLabel` which is the correct annotation for tagging a surrounding element.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class FullDateSurrogate(@CborLabel(1004) val date: String)
