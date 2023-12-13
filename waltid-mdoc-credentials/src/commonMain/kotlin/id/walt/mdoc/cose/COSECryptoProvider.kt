package id.walt.mdoc.cose

/**
 * Crypto provider, that provides COSE signing and verifying on the target platform.
 * The implementatin must support COSE_Sign1, as well as validation of certificate chains against a root CA.
 * Can be implemented by library user, to integrate their own or custom COSE crypto library
 * Default implementations exist for some platforms.
 * @see SimpleCOSECryptoProvider
 */
interface COSECryptoProvider {

  /**
   * Create a COSE_Sign1 signature for the given payload, with an optional keyID parameter.
   * @param payload The payload data to be signed, as byte array
   * @param keyID Optional keyID of the signing key to be used, if required by crypto provider impl
   * @return  COSE_Sign1 structure with headers, payload and signature
   */
  fun sign1(payload: ByteArray, keyID: String? = null): COSESign1

  /**
   * Verify a COSE_Sign1 signature, with an optional keyID parameter
   * @param coseSign1 The COSE_Sign1 structure containing COSE headers, payload and signature
   * @param keyID Optional keyID of the verification key to be used, if required by the crypto provider impl
   * @return True if signature was verified
   */
  fun verify1(coseSign1: COSESign1, keyID: String? = null): Boolean

  /**
   * Verify X509 certificate chain against a root CA, if certificate is present in the COSE_Sign1 unprotected header
   * @param coseSign1 The COSE_Sign1 structure containing COSE headers, payload and signature
   * @param keyID Optional keyID of the key pair, if required by the crypto provider impl
   * @return True if certificate chain was validated
   */
  fun verifyX5Chain(coseSign1: COSESign1, keyID: String? = null): Boolean
}