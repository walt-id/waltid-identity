package id.walt.mdoc.docrequest

import id.walt.mdoc.cose.COSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.mdocauth.DeviceAuthentication
import id.walt.mdoc.readerauth.ReaderAuthentication
import kotlinx.serialization.Serializable

/**
 * MDoc request data structure, containing requested items and reader authentication
 * @param itemsRequest  CBOR-encoded items request
 * @param readerAuth  COSE Sign1 structure of reader authentication
 */
@Serializable
data class MDocRequest internal constructor(
  val itemsRequest: EncodedCBORElement,
  val readerAuth: COSESign1? = null
) {
  private var _decodedItemsRequest: ItemsRequest? = null

  /**
   * Decoded items request
   */
  val decodedItemsRequest
    get() = _decodedItemsRequest ?: itemsRequest.decode<ItemsRequest>().also {
      _decodedItemsRequest = it
    }

  /**
   * Namespace keys of requested items
   */
  val nameSpaces
    get() = decodedItemsRequest.nameSpaces.value.keys.map { it.str }

  /**
   * Requested doc type
   */
  val docType
    get() = decodedItemsRequest.docType.value

  /**
   * Convenience method to read requested items for a given namespace
   * @param nameSpace The name space of the requested items
   * @return  Map containing key and intent-to-retain flag of each requested item
   */
  fun getRequestedItemsFor(nameSpace: String): Map<String, Boolean> {
    return (decodedItemsRequest.nameSpaces.value[MapKey(nameSpace)] as? MapElement)?.value?.map {
      Pair(it.key.str, (it.value as BooleanElement).value)
    }?.toMap() ?: mapOf()
  }

  private fun getReaderSignedPayload(readerAuthentication: ReaderAuthentication) = EncodedCBORElement(readerAuthentication.toDE()).toCBOR()

  /**
   * Verify mdoc read request, according to the given parameters
   * @param verificationParams  Verification parameters to apply
   * @param cryptoProvider  COSE Crypto provider, for verification of reader authentication
   * @return  True if request was verified
   */
  fun verify(verificationParams: MDocRequestVerificationParams, cryptoProvider: COSECryptoProvider): Boolean {
    return (!verificationParams.requiresReaderAuth ||
            readerAuth?.let {
              val readerAuthentication = verificationParams.readerAuthentication?.let { getReaderSignedPayload(it) } ?: throw Exception("No reader authentication payload given")
              cryptoProvider.verify1(it.attachPayload(readerAuthentication), verificationParams.readerKeyId)
            } ?: false) &&
        (verificationParams.allowedToRetain == null || nameSpaces.all { ns ->
          getRequestedItemsFor(ns).all { reqItem ->
            !reqItem.value || // intent to retain is false
            (verificationParams.allowedToRetain[ns]?.contains(reqItem.key) ?: false) }
        })
  }

  /**
   * Convert to CBOR map element
   */
  fun toMapElement() = buildMap {
    put(MapKey("itemsRequest"), itemsRequest)
    readerAuth?.let {
      put(MapKey("readerAuth"), it.toDE())
    }
  }.toDE()
}