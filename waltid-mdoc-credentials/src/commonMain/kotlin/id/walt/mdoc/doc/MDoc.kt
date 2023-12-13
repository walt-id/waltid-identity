package id.walt.mdoc.doc

import cbor.Cbor
import id.walt.mdoc.cose.COSECryptoProvider
import id.walt.mdoc.cose.COSEMac0
import id.walt.mdoc.dataelement.EncodedCBORElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.StringElement
import id.walt.mdoc.devicesigned.DeviceAuth
import id.walt.mdoc.devicesigned.DeviceSigned
import id.walt.mdoc.docrequest.MDocRequest
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.mdoc.issuersigned.IssuerSignedItem
import id.walt.mdoc.mdocauth.DeviceAuthentication
import id.walt.mdoc.mso.MSO
import korlibs.crypto.HMAC
import kotlinx.datetime.Clock
import kotlinx.serialization.*

/**
 * MDoc data structure containing doc type, issuer signed items, device signed items and errors, if any.
 * Provides high-level methods and properties to access nested objects and values.
 * To create/issue and sign an MDoc, use MDocBuilder
 * @see MDocBuilder
 */
@Serializable(with = MDocSerializer::class)
data class MDoc(
    val docType: StringElement,
    val issuerSigned: IssuerSigned,
    val deviceSigned: DeviceSigned?,
    val errors: MapElement? = null
) {
    var _mso: MSO? = null

    /**
     * Signed Mobile Security Object, contained in issuer authentication data
     */
    val MSO
        get() = _mso ?: issuerSigned.issuerAuth?.payload?.let { data ->
            EncodedCBORElement.fromEncodedCBORElementData(data).decode<MSO>()
        }?.also { _mso = it }

    /**
     * All namespaces of issuer signed items
     */
    val nameSpaces
        get() = issuerSigned.nameSpaces?.keys ?: setOf()

    /**
     * Get issuer signed items from the given item namespace
     * @param nameSpace The namespace to get items from
     * @return All items in the given namespace
     */
    fun getIssuerSignedItems(nameSpace: String): List<IssuerSignedItem> {
        return issuerSigned.nameSpaces?.get(nameSpace)?.map {
            it.decode<IssuerSignedItem>()
        }?.toList() ?: listOf()
    }

    /**
     * Verify issuer signed items against item digests in MSO (tamper check)
     */
    fun verifyIssuerSignedItems(): Boolean {
        val mso = MSO ?: throw Exception("No MSO object found on this mdoc")

        return issuerSigned.nameSpaces?.all { nameSpace ->
            mso.verifySignedItems(nameSpace.key, nameSpace.value)
        } ?: true                                                       // 3.
    }

    /**
     * Validate issuer certificate chain
     * @param cryptoProvider The crypto provider implementation to use for the certificate validation
     * @param keyID Optional key ID of the key pair to use, if crypto provider requires it
     */
    fun verifyCertificate(cryptoProvider: COSECryptoProvider, keyID: String? = null): Boolean {
        return issuerSigned.issuerAuth?.let {
            cryptoProvider.verifyX5Chain(it, keyID)
        } ?: throw Exception("No issuer auth found on document")
    }

    /**
     * Verify validity, based on validity info given in the MSO
     */
    fun verifyValidity(): Boolean {
        val mso = MSO ?: throw Exception("No MSO object found on this mdoc")
        return mso.validityInfo.validFrom.value <= Clock.System.now()      && // 5.2
        mso.validityInfo.validUntil.value >= Clock.System.now()               // 5.3
    }

    /**
     * Verify doc type against doc type given in the MSO
     */
    fun verifyDocType(): Boolean {
        val mso = MSO ?: throw Exception("No MSO object found on this mdoc")
        return mso.docType == docType                                         // 4.
    }

    /**
     * Verify issuer signature
     * @param cryptoProvider The crypto provider implementation to use for the verification
     * @param keyID Optional key ID of the key to use, if crypto provider requires it
     */
    fun verifySignature(cryptoProvider: COSECryptoProvider, keyID: String? = null): Boolean {
        return issuerSigned.issuerAuth?.let {
            cryptoProvider.verify1(it, keyID)
        } ?: throw Exception("No issuer auth found on document")
    }

    /**
     * Verify device signature
     * @param deviceAuthentication Device authentication structure, that represents the payload signed by the device
     * @param cryptoProvider The crypto provider implementation to use for the verification
     * @param keyID Optional key ID of the key to use, if crypto provider requires it
     */
    fun verifyDeviceSignature(deviceAuthentication: DeviceAuthentication, cryptoProvider: COSECryptoProvider, keyID: String?): Boolean {
        val deviceSignature = deviceSigned?.deviceAuth?.deviceSignature ?: throw Exception("No device signature found on MDoc")
        return cryptoProvider.verify1(
            deviceSignature.attachPayload(getDeviceSignedPayload(deviceAuthentication)),
            keyID
        )
    }

    /**
     * Verify device MAC
     * @param deviceAuthentication Device authentication structure, that represents the payload signed by the device
     * @param ephemeralMACKey   Ephemeral key used for creating the MAC, as negotiated during session establishment
     */
    fun verifyDeviceMAC(deviceAuthentication: DeviceAuthentication, ephemeralMACKey: ByteArray): Boolean {
        val deviceMac = deviceSigned?.deviceAuth?.deviceMac ?: throw Exception("No device MAC found on MDoc")
        return deviceMac.attachPayload(getDeviceSignedPayload(deviceAuthentication)).verify(ephemeralMACKey)
    }

    private fun verifyDeviceSigOrMac(verificationParams: MDocVerificationParams, cryptoProvider: COSECryptoProvider): Boolean {
        val mdocDeviceAuth = deviceSigned?.deviceAuth ?: throw Exception("MDoc has no device authentication")
        val deviceAuthenticationPayload = verificationParams.deviceAuthentication ?: throw Exception("No device authentication payload given, for check of device signature or MAC")
        return if(mdocDeviceAuth.deviceMac != null) {
            verifyDeviceMAC(
                deviceAuthenticationPayload,
                verificationParams.ephemeralMacKey ?: throw Exception("No ephemeral MAC key given, for check of device MAC")
            )
        } else if(mdocDeviceAuth.deviceSignature != null) {
            verifyDeviceSignature(
                deviceAuthenticationPayload,
                cryptoProvider, verificationParams.deviceKeyID
            )
        } else throw Exception("MDoc device auth has neither MAC nor signature")
    }

    /**
     * Verify the mdoc based on the verification types and parameters given, using the given COSE cryto provider
     * @param verificationParams Verification parameters, defining verification types and other params
     * @param cryptoProvider The crypto provider implementation to use for the verification
     */
    fun verify(verificationParams: MDocVerificationParams, cryptoProvider: COSECryptoProvider): Boolean {
        // check points 1-5 of ISO 18013-5: 9.3.1
        return VerificationType.all.all { type ->
            !verificationParams.verificationTypes.has(type) || when(type) {
                VerificationType.VALIDITY -> verifyValidity()
                VerificationType.DOC_TYPE -> verifyDocType()
                VerificationType.CERTIFICATE_CHAIN -> verifyCertificate(cryptoProvider, verificationParams.issuerKeyID)
                VerificationType.ITEMS_TAMPER_CHECK -> verifyIssuerSignedItems()
                VerificationType.ISSUER_SIGNATURE -> verifySignature(cryptoProvider, verificationParams.issuerKeyID)
                VerificationType.DEVICE_SIGNATURE -> verifyDeviceSigOrMac(verificationParams, cryptoProvider)
            }
        }
    }

    private fun selectDisclosures(mDocRequest: MDocRequest): IssuerSigned {
        return IssuerSigned(
            issuerSigned.nameSpaces?.mapValues { entry ->
                val requestedItems = mDocRequest.getRequestedItemsFor(entry.key)
                entry.value.filter { encodedItem ->
                    requestedItems.containsKey(encodedItem.decode<IssuerSignedItem>().elementIdentifier.value)
                }
            },
            issuerSigned.issuerAuth
        )
    }

    private fun getDeviceSignedPayload(deviceAuthentication: DeviceAuthentication) = EncodedCBORElement(deviceAuthentication.toDE()).toCBOR()

    /**
     * Present this mdoc to reader, using device signature
     * @param mDocRequest The request containing requested items, etc. for selective disclosure
     * @param deviceAuthentication The device authentication structure, which represents the payload to signed by the device
     * @param cryptoProvider The crypto provider implementation to use for the signing
     * @param keyID Optional key ID of the key to use, if crypto provider requires it
     * @return MDoc with device-signed data containing the created signature
     */
    fun presentWithDeviceSignature(mDocRequest: MDocRequest, deviceAuthentication: DeviceAuthentication, cryptoProvider: COSECryptoProvider, keyID: String? = null): MDoc {
        val coseSign1 = cryptoProvider.sign1(getDeviceSignedPayload(deviceAuthentication), keyID).detachPayload()
        return MDoc(
            docType,
            selectDisclosures(mDocRequest),
            DeviceSigned(EncodedCBORElement(MapElement(mapOf())), DeviceAuth(deviceSignature = coseSign1))
        )
    }

    /**
     * Present this mdoc to reader, using device MAC
     * @param mDocRequest The request containing requested items, etc. for selective disclosure
     * @param deviceAuthentication The device authentication structure, which represents the payload to be signed by the device
     * @param ephemeralMACKey   Ephemeral key used for creating the MAC, as negotiated during session establishment
     * @return MDoc with device-signed data containing the created MAC
     */
    fun presentWithDeviceMAC(mDocRequest: MDocRequest, deviceAuthentication: DeviceAuthentication, ephemeralMACKey: ByteArray): MDoc {
        val coseMac0 = COSEMac0.createWithHMAC256(getDeviceSignedPayload(deviceAuthentication), ephemeralMACKey).detachPayload()
        return MDoc(
            docType,
            selectDisclosures(mDocRequest),
            DeviceSigned(EncodedCBORElement(MapElement(mapOf())), DeviceAuth(coseMac0)))
    }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("docType"), docType)
            put(MapKey("issuerSigned"), issuerSigned.toMapElement())
            deviceSigned?.let {
                put(MapKey("deviceSigned"), it.toMapElement())
            }
            errors?.let {
                put(MapKey("errors"), it)
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
        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<MDoc>(cbor)
        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<MDoc>(cbor)

        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(mapElement: MapElement) = MDoc(
            mapElement.value[MapKey("docType")] as? StringElement ?: throw SerializationException("No docType property found on object"),
            (mapElement.value[MapKey("issuerSigned")] as? MapElement)?.let { IssuerSigned.fromMapElement(it) } ?: throw SerializationException("No issuerSigned property found on object"),
            (mapElement.value[MapKey("deviceSigned")] as? MapElement)?.let { DeviceSigned.fromMapElement(it) },
            mapElement.value[MapKey("errors")] as? MapElement
        )
    }
}