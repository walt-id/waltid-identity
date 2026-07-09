@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.issuance

import id.walt.cose.*
import id.walt.crypto.keys.Key
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
        val coseSigner = issuerKey.toCoseSigner()

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

        runCatching {
            mso.precheck()
        }.onFailure { ex ->
            throw IllegalArgumentException("Could not create valid MSO for issued mdoc: ${ex.message}", ex)
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
                algorithm = issuerKey.keyType.toCoseAlgorithm(),
                x5u = protectedHeaderX5u,
                x5t = protectedHeaderX5t,
                x5chain = if (hasQualifiedHeaders) issuerCertificate else null
            ),
            unprotectedHeaders = CoseHeaders(x5chain = issuerCertificate),
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
        var idx = 0u
        val namespaceIssuerSignedItems = data.namespaces.mapValues { (namespace, namespaceData) ->
            namespaceData.mapNotNull { (elementIdentifier, elementValueJson) ->
                val mappedValue = valueMappingFunction(docType, namespace, elementIdentifier, elementValueJson)
                if (mappedValue != null) {
                    IssuerSignedItem.create(idx++, elementIdentifier, mappedValue)
                } else null
            }
        }

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

}

