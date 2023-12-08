package id.walt.mdoc.cose

interface AsyncCOSECryptoProvider {
  suspend fun sign1(payload: ByteArray, keyID: String? = null): COSESign1
}