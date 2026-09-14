@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.edition2.document

import id.walt.cose.CoseSign1
import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import id.walt.mdoc.objects.edition2.elements.IssuerSignedItem
import id.walt.mdoc.objects.edition2.elements.IssuerSignedList
import id.walt.mdoc.objects.edition2.elements.NamespacedIssuerSignedListSerializer
import id.walt.mdoc.objects.mso.MobileSecurityObject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents the `IssuerSigned` structure within a `Document`, containing data elements attested to
 * by the issuing authority.
 *
 * This is a critical component for verifying the authenticity and integrity of the credential data.
 * It holds the namespaces with the signed data items and the `issuerAuth` COSE structure,
 * which contains the Mobile Security Object (MSO) and the issuer's signature over it.
 *
 * @see ISO/IEC 18013-5, IssuerSigned CDDL
 *
 * @property namespaces A map where the key is a namespace identifier (e.g., "org.iso.18013.5.1") and
 * the value is a list of all issuer-signed items for that namespace. This field is optional.
 * @property issuerAuth The `COSE_Sign1` structure that contains the MSO as its payload. The MSO holds the
 * digests of all data elements, validity information, and the device's public key. The COSE signature
 * on the MSO is the root of trust for all issuer-signed data.
 * @property extensions Unrecognized IssuerSigned fields retained for wire round trips.
 */
@ConsistentCopyVisibility
@Serializable(with = IssuerSignedSerializer::class)
data class IssuerSigned internal constructor(
    @SerialName("nameSpaces")
    @Serializable(with = NamespacedIssuerSignedListSerializer::class)
    val namespaces: Map<String, IssuerSignedList>? = null,

    @SerialName("issuerAuth")
    val issuerAuth: CoseSign1, // MobileSecurityObject
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        requireNoExtensionCollisions(extensions, ISSUER_SIGNED_FIELDS, "IssuerSigned")
    }

    /**
     * A convenience function to decode the CBOR payload of the `issuerAuth` signature
     * structure into a [MobileSecurityObject].
     *
     * @return The parsed [MobileSecurityObject].
     * @throws Exception if the payload cannot be decoded.
     */
    fun decodeMobileSecurityObject(): MobileSecurityObject = issuerAuth.decodeIsoPayload()

    companion object {
        /**
         * Creates issuer-signed data from newly constructed items. For received signed bytes,
         * use [fromIssuerSignedLists] to preserve their original representation.
         *
         * @param namespacedItems A map of namespace identifiers to lists of [IssuerSignedItem]s.
         * @param issuerAuth The `COSE_Sign1` structure containing the signed MSO.
         * @return A new [IssuerSigned] instance.
         */
        fun fromIssuerSignedItems(
            namespacedItems: Map<String, List<IssuerSignedItem>>,
            issuerAuth: CoseSign1, // MobileSecurityObject
            extensions: Map<String, CborElement> = emptyMap(),
        ): IssuerSigned = IssuerSigned(
            namespaces = namespacedItems.map { (namespace, value) ->
                namespace to IssuerSignedList.fromIssuerSignedItems(value)
            }.toMap(),
            issuerAuth = issuerAuth,
            extensions = extensions,
        )

        /**
         * Creates an [IssuerSigned] using existing namespace wrappers so received
         * `IssuerSignedItemBytes` survive selective disclosure without re-encoding.
         */
        fun fromIssuerSignedLists(
            namespaces: Map<String, IssuerSignedList>,
            issuerAuth: CoseSign1,
            extensions: Map<String, CborElement> = emptyMap(),
        ): IssuerSigned = IssuerSigned(
            namespaces = namespaces.takeIf { it.isNotEmpty() },
            issuerAuth = issuerAuth,
            extensions = extensions,
        )
    }
}

object IssuerSignedSerializer : KSerializer<IssuerSigned> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: IssuerSigned) {
        val fields = linkedMapOf<String, CborElement>()
        value.namespaces?.let {
            fields["nameSpaces"] = it.toCborElement(NamespacedIssuerSignedListSerializer)
        }
        fields["issuerAuth"] = value.issuerAuth.toCborElement(CoseSign1.serializer())
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): IssuerSigned {
        val fields = decoder.decodeTextMap("IssuerSigned")
        return IssuerSigned(
            namespaces = fields["nameSpaces"]?.fromCborElement(NamespacedIssuerSignedListSerializer),
            issuerAuth = fields["issuerAuth"]?.fromCborElement(CoseSign1.serializer())
                ?: throw SerializationException("IssuerSigned issuerAuth is required"),
            extensions = fields.extensionsExcluding(ISSUER_SIGNED_FIELDS),
        )
    }
}

private val ISSUER_SIGNED_FIELDS = setOf("nameSpaces", "issuerAuth")
