package id.walt.mdoc.doc

import id.walt.mdoc.cose.AsyncCOSECryptoProvider
import id.walt.mdoc.cose.COSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.AnyDataElement
import id.walt.mdoc.dataelement.EncodedCBORElement
import id.walt.mdoc.dataelement.StringElement
import id.walt.mdoc.devicesigned.DeviceSigned
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.mdoc.issuersigned.IssuerSignedItem
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.MSO
import id.walt.mdoc.mso.ValidityInfo
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * MDoc builder, that provides high-level methods to create/issue and sign mdoc documents
 * @param docType The doc type of the mdoc to create
 */
class MDocBuilder(val docType: String) {
  val nameSpacesMap = mutableMapOf<String, MutableList<IssuerSignedItem>>()

  /**
   * Add item(s) to be signed by issuer, in the given namespace
   * @param nameSpace The namespace to which the items get added
   * @param item  List of items to add to the given namespace
   */
  fun addIssuerSignedItems(nameSpace: String, vararg item: IssuerSignedItem): MDocBuilder {
    nameSpacesMap.getOrPut(nameSpace) { mutableListOf() }.addAll(item)
    return this
  }

  /**
   * Convenience method to create issuer signed item, with random salt and auto-detection of next digest ID
   * @param nameSpace The namespace in which this item gets created
   * @param elementIdentifier Item identifier/key
   * @param elementValue Item value
   */
  fun addItemToSign(nameSpace: String, elementIdentifier: String, elementValue: AnyDataElement): MDocBuilder {
    val items = nameSpacesMap.getOrPut(nameSpace) { mutableListOf() }
    items.add(
      IssuerSignedItem.createWithRandomSalt(
        items.maxOfOrNull { it.digestID.value.toLong().toUInt() }?.plus(1u) ?: 0u,
        elementIdentifier,
        elementValue
      )
    )
    return this
  }

  /**
   * Build this mdoc using the provided issuer and device authentication data.
   * To issue and sign mdoc documents, use the sign method instead
   * @see sign
   * @see signAsync
   */
  fun build(issuerAuth: COSESign1?, deviceSigned: DeviceSigned? = null): MDoc {
    return MDoc(
      StringElement(docType),
      IssuerSigned(nameSpacesMap.mapValues { it.value.map { item -> EncodedCBORElement(item.toMapElement()) } }, issuerAuth),
      deviceSigned
    )
  }

  /**
   * Build and sign the mdoc document
   * @param validityInfo Validity information of this issued document
   * @param deviceKeyInfo Info of device key, to which this document is bound (holder key)
   * @param cryptoProvider COSE crypto provider impl to use for signing this document
   * @param keyID ID of the key to use for signing, if required by crypto provider
   */
  @OptIn(ExperimentalSerializationApi::class)
  suspend fun signAsync(validityInfo: ValidityInfo,
                        deviceKeyInfo: DeviceKeyInfo, cryptoProvider: AsyncCOSECryptoProvider, keyID: String? = null): MDoc {
    val mso = MSO.createFor(nameSpacesMap, deviceKeyInfo, docType, validityInfo)
    val issuerAuth = cryptoProvider.sign1(mso.toMapElement().toEncodedCBORElement().toCBOR(), keyID)
    return build(issuerAuth)
  }

  /**
   * Build and sign the mdoc document
   * @param validityInfo Validity information of this issued document
   * @param deviceKeyInfo Info of device key, to which this document is bound (holder key)
   * @param cryptoProvider COSE crypto provider impl to use for signing this document
   * @param keyID ID of the key to use for signing, if required by crypto provider
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun sign(validityInfo: ValidityInfo,
           deviceKeyInfo: DeviceKeyInfo, cryptoProvider: COSECryptoProvider, keyID: String? = null): MDoc {
    val mso = MSO.createFor(nameSpacesMap, deviceKeyInfo, docType, validityInfo)
    val issuerAuth = cryptoProvider.sign1(mso.toMapElement().toEncodedCBORElement().toCBOR(), keyID)
    return build(issuerAuth)
  }
}
