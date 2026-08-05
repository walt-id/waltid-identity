@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.issuance

import id.walt.cose.*
import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.mdoc.credsdata.MdocData
import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.digest.ValueDigestList
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.mso.DeviceKeyInfo
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.objects.mso.Status
import id.walt.mdoc.objects.mso.ValidityInfo
import id.walt.mdoc.schema.MdocsSchemaMappingFunction.toCborElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

object MdocIssuer {

    @Serializable
    data class MdocUniversalIssuanceData(
        val namespaces: Map<String, JsonObject>,
    )


    @Deprecated("Use the crypto2 Key overload with an explicit signature algorithm")
    suspend fun signMsoForIssuerSignedObjects(
        /** Mapped data */
        namespaceIssuerSignedItems: Map<String, List<IssuerSignedItem>>,

        /** Key to use for issuance (credentials will be signed with it) */
        issuerKey: Key,
        issuerCertificate: List<CoseCertificate>,

        /** Key of the device of the holder to embed */
        holderKey: CoseKey,

        /** Doctype of the credential to use */
        docType: String,

        validFrom: Instant? = null,
        validUntil: Instant = Clock.System.now().plus(1.days * 365 * 10),
        status: Status? = null,
        digestAlgorithm: String = "SHA-256",
        /**
         * Optional `x5u` (RFC 9360 label 35) to place in the protected header.
         * Required for ETSI TS 119 472-1 ISO-mdoc QEAA/PuB-EAA (QEAA-6.6.2-02 / PuB-EAA-6.6.3-02).
         */
        protectedHeaderX5u: String? = null,
        /**
         * Optional `x5t` (RFC 9360 label 34) certificate hash to place in the protected header.
         * Required alongside [protectedHeaderX5u] for QEAA/PuB-EAA; digest SHALL be SHA-256.
         */
        protectedHeaderX5t: CoseCertHash? = null
    ): IssuerSigned {
        return signMsoForIssuerSignedObjects(
            namespaceIssuerSignedItems = namespaceIssuerSignedItems,
            issuerCertificate = issuerCertificate,
            holderKey = holderKey,
            docType = docType,
            validFrom = validFrom,
            validUntil = validUntil,
            status = status,
            digestAlgorithm = digestAlgorithm,
            protectedHeaderX5u = protectedHeaderX5u,
            protectedHeaderX5t = protectedHeaderX5t,
            coseSigner = issuerKey.toCoseSigner(),
            coseAlgorithm = requireNotNull(issuerKey.keyType.toCoseAlgorithm()) {
                "Issuer key type has no COSE signing algorithm: ${issuerKey.keyType}"
            },
        )
    }

    suspend fun signMsoForIssuerSignedObjects(
        namespaceIssuerSignedItems: Map<String, List<IssuerSignedItem>>,
        issuerKey: Crypto2Key,
        signatureAlgorithm: Int,
        issuerCertificate: List<CoseCertificate>,
        holderKey: CoseKey,
        docType: String,
        validFrom: Instant? = null,
        validUntil: Instant = Clock.System.now().plus(1.days * 365 * 10),
        status: Status? = null,
        digestAlgorithm: String = "SHA-256",
        protectedHeaderX5u: String? = null,
        protectedHeaderX5t: CoseCertHash? = null,
    ): IssuerSigned = signMsoForIssuerSignedObjects(
        namespaceIssuerSignedItems = namespaceIssuerSignedItems,
        issuerCertificate = issuerCertificate,
        holderKey = holderKey,
        docType = docType,
        validFrom = validFrom,
        validUntil = validUntil,
        status = status,
        digestAlgorithm = digestAlgorithm,
        protectedHeaderX5u = protectedHeaderX5u,
        protectedHeaderX5t = protectedHeaderX5t,
        coseSigner = issuerKey.toCoseSigner(signatureAlgorithm),
        coseAlgorithm = signatureAlgorithm,
    )

    private suspend fun signMsoForIssuerSignedObjects(
        namespaceIssuerSignedItems: Map<String, List<IssuerSignedItem>>,
        issuerCertificate: List<CoseCertificate>,
        holderKey: CoseKey,
        docType: String,
        validFrom: Instant?,
        validUntil: Instant,
        status: Status?,
        digestAlgorithm: String,
        protectedHeaderX5u: String?,
        protectedHeaderX5t: CoseCertHash?,
        coseSigner: CoseSigner,
        coseAlgorithm: Int,
    ): IssuerSigned {

        val valueDigests = namespaceIssuerSignedItems.mapValues { (namespace, issuerSignedItems) ->
            ValueDigestList(issuerSignedItems.map { issuerSignedItem ->
                ValueDigest.fromIssuerSignedItem(issuerSignedItem, namespace, digestAlgorithm)
            })
        }
        val signedTimestamp = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val effectiveValidFrom = if (validFrom != null && validFrom > signedTimestamp)
            Instant.fromEpochSeconds(validFrom.epochSeconds) else signedTimestamp

        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = digestAlgorithm,
            docType = docType,
            valueDigests = valueDigests,
            deviceKeyInfo = DeviceKeyInfo(deviceKey = holderKey),
            validityInfo = ValidityInfo(
                signed = signedTimestamp,
                validFrom = effectiveValidFrom,
                // ISO 18013-5 MSO tdate fields must not include fractional seconds
                validUntil = Instant.fromEpochSeconds(validUntil.epochSeconds)
            ),
            status = status
        )

        try {
            mso.precheck()
        } catch (cause: Throwable) {
            throw IllegalArgumentException("Could not create valid MSO for issued mdoc: ${cause.message}", cause)
        }

        // The MSO is wrapped in a tagged bytestring to become the payload
        val msoBytes = coseCompliantCbor.encodeToByteArray(mso)
        val msoPayload = byteArrayOf(0xd8.toByte(), 24.toByte()) + coseCompliantCbor.encodeToByteArray(ByteArraySerializer(), msoBytes)


        // Create the CoseSign1 object (issuerAuth) with a signature.
        // Per ISO 18013-5 §9.1.2.4 x5chain is carried in the unprotected header. For ETSI QEAA/PuB-EAA
        // (QEAA-6.6.2-02 / PuB-EAA-6.6.3-02) the protected header SHALL additionally carry x5u and x5t,
        // and SHOULD carry x5chain (QEAA-6.6.2-04); we include it in the protected header in that case.
        val hasQualifiedHeaders = protectedHeaderX5u != null || protectedHeaderX5t != null
        val issuerAuth = CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(
                algorithm = coseAlgorithm,
                x5u = protectedHeaderX5u,
                x5t = protectedHeaderX5t,
                x5chain = if (hasQualifiedHeaders) issuerCertificate else null
            ),
            unprotectedHeaders = CoseHeaders(x5chain = issuerCertificate.takeUnless { hasQualifiedHeaders }),
            payload = msoPayload,
            signer = coseSigner
        )

        val issuerSigned = IssuerSigned.fromIssuerSignedItems(
            namespacedItems = namespaceIssuerSignedItems,
            issuerAuth = issuerAuth
        )

        return issuerSigned
    }

    val defaultSchemalessMappingFunction: (
        docType: String, namespace: String, elementIdentifier: String, elementValueJson: JsonElement
    ) -> CborElement? = { _, _, _, elementValueJson ->
        elementValueJson.toCborElement()
    }

    /**
     * Issue mdocs credential that is not defined as type-safe credential
     * (providing raw JSON data)
     */
    @Deprecated("Use the crypto2 Key overload with an explicit signature algorithm")
    suspend fun issueUniversal(
        /** Key to use for issuance (credentials will be signed with it) */
        issuerKey: Key,
        issuerCertificate: List<CoseCertificate>,

        /** Key of the device of the holder to embed */
        holderKey: CoseKey,

        /** Doctype of the credential to use */
        docType: String,

        /** The credential data to issue */
        data: MdocUniversalIssuanceData,
        validFrom: Instant? = null,
        validUntil: Instant = Clock.System.now().plus(1.days * 365 * 10),
        status: Status? = null,
        digestAlgorithm: String = "SHA-256",

        /** Optional x5u (RFC 9360) for the protected header (ETSI QEAA/PuB-EAA). */
        protectedHeaderX5u: String? = null,
        /** Optional x5t (RFC 9360) cert hash for the protected header (ETSI QEAA/PuB-EAA). */
        protectedHeaderX5t: CoseCertHash? = null,

        /** Custom value serialization (null returns are explicitly NOT mapped) */
        valueMappingFunction: (
            docType: String,
            namespace: String,
            elementIdentifier: String,
            elementValueJson: JsonElement
        ) -> CborElement? = defaultSchemalessMappingFunction
    ): IssuerSigned {
        val namespaceIssuerSignedItems = mapUniversalData(docType, data, valueMappingFunction)

        return signMsoForIssuerSignedObjects(
            namespaceIssuerSignedItems = namespaceIssuerSignedItems,
            issuerKey = issuerKey,
            issuerCertificate = issuerCertificate,
            holderKey = holderKey,
            docType = docType,
            validFrom = validFrom,
            validUntil = validUntil,
            status = status,
            digestAlgorithm = digestAlgorithm,
            protectedHeaderX5u = protectedHeaderX5u,
            protectedHeaderX5t = protectedHeaderX5t
        )
    }

    suspend fun issueUniversal(
        issuerKey: Crypto2Key,
        signatureAlgorithm: Int,
        issuerCertificate: List<CoseCertificate>,
        holderKey: CoseKey,
        docType: String,
        data: MdocUniversalIssuanceData,
        validFrom: Instant? = null,
        validUntil: Instant = Clock.System.now().plus(1.days * 365 * 10),
        status: Status? = null,
        digestAlgorithm: String = "SHA-256",
        protectedHeaderX5u: String? = null,
        protectedHeaderX5t: CoseCertHash? = null,
        valueMappingFunction: (
            docType: String,
            namespace: String,
            elementIdentifier: String,
            elementValueJson: JsonElement,
        ) -> CborElement? = defaultSchemalessMappingFunction,
    ): IssuerSigned = signMsoForIssuerSignedObjects(
        namespaceIssuerSignedItems = mapUniversalData(docType, data, valueMappingFunction),
        issuerKey = issuerKey,
        signatureAlgorithm = signatureAlgorithm,
        issuerCertificate = issuerCertificate,
        holderKey = holderKey,
        docType = docType,
        validFrom = validFrom,
        validUntil = validUntil,
        status = status,
        digestAlgorithm = digestAlgorithm,
        protectedHeaderX5u = protectedHeaderX5u,
        protectedHeaderX5t = protectedHeaderX5t,
    )

    @Deprecated("Use the crypto2 Key overload with an explicit signature algorithm")
    suspend fun issueTypesafe(
        /** Key to use for issuance (credentials will be signed with it) */
        issuerKey: Key,
        issuerCertificate: List<CoseCertificate>,

        /** Key of the device of the holder to embed */
        holderKey: CoseKey,

        /** The credential data to issue */
        typesafeData: MdocData,

        validFrom: Instant? = null,

        validUntil: Instant = Clock.System.now().plus(1.days * 365 * 10),
        status: Status? = null,
        digestAlgorithm: String = "SHA-256"
    ): IssuerSigned {
        val namespaceIssuerSignedItems = typesafeData.toNamespaceIssuerSignedItems()

        return signMsoForIssuerSignedObjects(
            namespaceIssuerSignedItems = namespaceIssuerSignedItems,
            issuerKey = issuerKey,
            issuerCertificate = issuerCertificate,
            holderKey = holderKey,
            docType = typesafeData.docType,
            validFrom = validFrom,
            validUntil = validUntil,
            status = status,
            digestAlgorithm = digestAlgorithm
        )
    }

    suspend fun issueTypesafe(
        issuerKey: Crypto2Key,
        signatureAlgorithm: Int,
        issuerCertificate: List<CoseCertificate>,
        holderKey: CoseKey,
        typesafeData: MdocData,
        validFrom: Instant? = null,
        validUntil: Instant = Clock.System.now().plus(1.days * 365 * 10),
        status: Status? = null,
        digestAlgorithm: String = "SHA-256",
    ): IssuerSigned = signMsoForIssuerSignedObjects(
        namespaceIssuerSignedItems = typesafeData.toNamespaceIssuerSignedItems(),
        issuerKey = issuerKey,
        signatureAlgorithm = signatureAlgorithm,
        issuerCertificate = issuerCertificate,
        holderKey = holderKey,
        docType = typesafeData.docType,
        validFrom = validFrom,
        validUntil = validUntil,
        status = status,
        digestAlgorithm = digestAlgorithm,
    )

    private fun mapUniversalData(
        docType: String,
        data: MdocUniversalIssuanceData,
        valueMappingFunction: (
            docType: String,
            namespace: String,
            elementIdentifier: String,
            elementValueJson: JsonElement,
        ) -> CborElement?,
    ): Map<String, List<IssuerSignedItem>> {
        var index = 0u
        return data.namespaces.mapValues { (namespace, namespaceData) ->
            namespaceData.mapNotNull { (elementIdentifier, elementValueJson) ->
                valueMappingFunction(docType, namespace, elementIdentifier, elementValueJson)?.let { mappedValue ->
                    IssuerSignedItem.create(index++, elementIdentifier, mappedValue)
                }
            }
        }
    }

}
