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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
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

        validFrom: Instant = Clock.System.now(),
        validUntil: Instant = Clock.System.now().plus(1.days * 365 * 10),
        status: Status? = null,
        digestAlgorithm: String = "SHA-256"
    ): IssuerSigned {
        val coseSigner = issuerKey.toCoseSigner()

        val valueDigests = namespaceIssuerSignedItems.mapValues { (namespace, issuerSignedItems) ->
            ValueDigestList(issuerSignedItems.map { issuerSignedItem ->
                ValueDigest.fromIssuerSignedItem(issuerSignedItem, namespace, digestAlgorithm)
            })
        }

        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = digestAlgorithm,
            docType = docType,
            valueDigests = valueDigests,
            deviceKeyInfo = DeviceKeyInfo(deviceKey = holderKey),
            validityInfo = ValidityInfo(
                signed = Clock.System.now(),
                validFrom = validFrom,
                validUntil = validUntil
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


        // Create the CoseSign1 object (issuerAuth) with a signature
        val issuerAuth = CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(algorithm = issuerKey.keyType.toCoseAlgorithm()),
            unprotectedHeaders = CoseHeaders(x5chain = issuerCertificate), // Placeholder for cert chain
            payload = msoPayload,
            signer = coseSigner
        )

        val issuerSigned = IssuerSigned.fromIssuerSignedItems(
            namespacedItems = namespaceIssuerSignedItems,
            issuerAuth = issuerAuth
        )

        return issuerSigned
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

        validFrom: Instant = Clock.System.now(),
        validUntil: Instant = Clock.System.now().plus(1.days * 365 * 10),
        status: Status? = null,
        digestAlgorithm: String = "SHA-256",

        /** Custom value serialization (null returns are explicitly NOT mapped) */
        valueMappingFunction: (
            docType: String,
            namespace: String,
            elementIdentifier: String,
            elementValueJson: JsonElement
        ) -> Any?
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
            digestAlgorithm = digestAlgorithm
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

        validFrom: Instant = Clock.System.now(),
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

