package id.walt.mdoc.objects.document

import id.walt.cose.CoseSign1
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.mdoc.objects.MdocsCborSerializer
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.elements.IssuerSignedList
import id.walt.mdoc.objects.elements.NamespacedIssuerSignedListSerializer
import id.walt.mdoc.objects.mso.MobileSecurityObject
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

/**
 * Represents the `IssuerSigned` structure within a `Document`, containing data elements attested to
 * by the issuing authority.
 *
 * This is a critical component for verifying the authenticity and integrity of the credential data.
 * It holds the namespaces with the signed data items and the `issuerAuth` COSE structure,
 * which contains the Mobile Security Object (MSO) and the issuer's signature over it.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (Device retrieval mdoc response)
 *
 * @property namespaces A map where the key is a namespace identifier (e.g., "org.iso.18013.5.1") and
 * the value is a list of all issuer-signed items for that namespace. This field is optional.
 * @property issuerAuth The `COSE_Sign1` structure that contains the MSO as its payload. The MSO holds the
 * digests of all data elements, validity information, and the device's public key. The COSE signature
 * on the MSO is the root of trust for all issuer-signed data.
 */
@ConsistentCopyVisibility
@Serializable
data class IssuerSigned private constructor(
    @SerialName("nameSpaces")
    @Serializable(with = NamespacedIssuerSignedListSerializer::class)
    val namespaces: Map<String, @Contextual IssuerSignedList>? = null,

    @SerialName("issuerAuth")
    val issuerAuth: CoseSign1 // MobileSecurityObject
) {

    /**
     * A convenience function to decode the CBOR payload of the `issuerAuth` signature
     * structure into a [MobileSecurityObject].
     *
     * @return The parsed [MobileSecurityObject].
     * @throws Exception if the payload cannot be decoded.
     */
    fun decodeMobileSecurityObject() =
        issuerAuth.decodeIsoPayload<MobileSecurityObject>()

    /**
     * A utility function to convert the structured, CBOR-oriented `namespaces` map into a
     * developer-friendly `JsonObject`. This is useful for application-level logic that
     * needs to work with the credential data in a standard JSON format.
     *
     * Note: This relies on application-specific serializers ([MdocsCborSerializer])
     * to handle complex data types.
     *
     * @return A [JsonObject] representing all data elements across all namespaces.
     */
    fun namespacesToJson() = buildJsonObject {
        namespaces?.forEach { (namespace, issuerSignedList) ->
            putJsonObject(namespace) {
                issuerSignedList.entries.forEach { wrappedItem ->
                    val item = wrappedItem.value

                    val serialized: JsonElement = MdocsCborSerializer.lookupSerializer(namespace, item.elementIdentifier)
                        ?.runCatching {
                            Json.encodeToJsonElement(this as KSerializer<Any?>, item.elementValue)
                        }?.getOrElse { println("Error encoding with custom serializer: ${it.stackTraceToString()}"); null }
                        ?: item.elementValue.toJsonElement()

                    put(item.elementIdentifier, serialized)
                }

            }
        }
    }

    data class ParsedIssuerAuth(
        val x5c: List<String>,
        val signerKey: Key,
    )

    suspend fun getParsedIssuerAuth(): ParsedIssuerAuth {
        val containedX5c = issuerAuth.unprotected.x5chain
        requireNotNull(containedX5c) { "Missingg x5c X509 certificate chain in Mdocs credential" }

        val convertedX5c = containedX5c.map { Base64.encode(it.rawBytes) }

        val signerKeyCertificate = containedX5c.firstOrNull() ?: throw IllegalArgumentException("Contained x5c X509 certificate chain in Mdocs credentials is empty (no signer element)")
        val signerKey = JWKKey.importFromDerCertificate(signerKeyCertificate.rawBytes)
            .getOrThrow()

        return ParsedIssuerAuth(x5c = convertedX5c, signerKey = signerKey)
    }


    companion object {
        /**
         * The primary factory method for creating an [IssuerSigned] instance.
         * Using a factory method with a private constructor ensures that the object is always
         * instantiated in a controlled and valid state.
         *
         * @param namespacedItems A map of namespace identifiers to lists of [IssuerSignedItem]s.
         * @param issuerAuth The `COSE_Sign1` structure containing the signed MSO.
         * @return A new [IssuerSigned] instance.
         */
        fun fromIssuerSignedItems(
            namespacedItems: Map<String, List<IssuerSignedItem>>,
            issuerAuth: CoseSign1, // MobileSecurityObject
        ): IssuerSigned = IssuerSigned(
            namespaces = namespacedItems.map { (namespace, value) ->
                namespace to IssuerSignedList.fromIssuerSignedItems(value, namespace)
            }.toMap(),
            issuerAuth = issuerAuth
        )
    }
}
