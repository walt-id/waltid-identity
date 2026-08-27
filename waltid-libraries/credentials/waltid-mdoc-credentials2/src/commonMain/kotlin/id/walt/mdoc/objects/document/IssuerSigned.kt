@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.document

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toSerializedJsonElement
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.objects.MdocsCborSerializer
import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.elements.IssuerSignedList
import id.walt.mdoc.objects.elements.NamespacedIssuerSignedListSerializer
import id.walt.mdoc.objects.mso.MobileSecurityObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.*
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import id.walt.crypto2.keys.Key as Crypto2Key

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
    val namespaces: Map<String, @Contextual IssuerSignedList>? = null,

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
    fun decodeMobileSecurityObject() = runCatching {
        issuerAuth.decodeIsoPayload<MobileSecurityObject>()
    }.getOrElse { ex ->
        log.trace(ex) { "Unable to parse MSO with decodeMobileSecurityObject(), MSO is: ${issuerAuth.payload?.toHexString()}" }
        throw IllegalArgumentException("Unable to parse MSO (mobile security object) of IssuerSigned: ${ex.message}", ex)
    }

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
                        ?: item.elementValue.toSerializedJsonElement()

                    if (serialized != JsonNull)
                        put(item.elementIdentifier, serialized)
                }
            }
        }
    }

    @Deprecated("Use ParsedIssuerAuthCrypto2")
    data class ParsedIssuerAuth(
        val x5c: List<String>,
        val signerKey: Key,
    )

    data class ParsedIssuerAuthCrypto2(
        val x5c: List<String>,
        val signerKey: Crypto2Key,
    )

    @Deprecated("Use getParsedIssuerAuthCrypto2()")
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getParsedIssuerAuth(): ParsedIssuerAuth {
        // Per ISO 18013-5 §9.1.2.4: x5chain SHALL be in the unprotected header.
        // Readers SHOULD also support x5chain in the protected header (for backwards compat / future).
        val containedX5c = containedX5c()

        val convertedX5c = containedX5c.map { Base64.encode(it.rawBytes) }

        val signerKeyCertificate = containedX5c.firstOrNull()
            ?: throw IllegalArgumentException("Contained x5c X509 certificate chain in Mdocs credentials is empty (no signer element)")
        val signerKey = JWKKey.importFromDerCertificate(signerKeyCertificate.rawBytes)
            .getOrThrow()

        return ParsedIssuerAuth(x5c = convertedX5c, signerKey = signerKey)
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getParsedIssuerAuthCrypto2(): ParsedIssuerAuthCrypto2 {
        val containedX5c = containedX5c()
        val convertedX5c = containedX5c.map { Base64.encode(it.rawBytes) }
        val signerCertificate = containedX5c.firstOrNull()
            ?: throw IllegalArgumentException("Contained x5c X509 certificate chain in Mdocs credentials is empty (no signer element)")

        val cert = X509CertificateUtil.parseCertificateDerEncoded(ByteString(signerCertificate.rawBytes))
        return ParsedIssuerAuthCrypto2(
            x5c = convertedX5c,
            signerKey = cert.restoreSubjectPublicKey(crypto2Runtime)
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun containedX5c() = issuerAuth.unprotected.x5chain
        ?: runCatching {
            // Fall back to protected header if unprotected has no x5chain
            coseCompliantCbor.decodeFromByteArray<CoseHeaders>(issuerAuth.protected).x5chain
        }.getOrNull()
        ?: throw IllegalArgumentException("Missing x5c X509 certificate chain in Mdocs credential")


    companion object {
        val log = KotlinLogging.logger { }
        private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())

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
            extensions: Map<String, CborElement> = emptyMap(),
        ): IssuerSigned = IssuerSigned(
            namespaces = namespacedItems.map { (namespace, value) ->
                namespace to IssuerSignedList.fromIssuerSignedItems(value, namespace)
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
