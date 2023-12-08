package id.walt.mdoc

import COSE.AlgorithmID
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate

/**
 * Simple COSE crypto key info object for a given private and public key pair. For verification only, private key can be omitted.
 * @param keyID ID of this key info object
 * @param algorithmID Signing algorithm ID, e.g.: ECDSA_256
 * @param publicKey Public key for COSE verification
 * @param privateKey Private key for COSE signing
 * @param x5Chain certificate chain, including intermediate and signing key certificates, but excluding root CA certificate!
 * @param trustedRootCA enforce trusted root CA, if not publicly known, for certificate path validation
 */
data class COSECryptoProviderKeyInfo(
  val keyID: String,
  val algorithmID: AlgorithmID,
  val publicKey: PublicKey,
  val privateKey: PrivateKey? = null,
  val x5Chain: List<X509Certificate> = listOf(),
  val trustedRootCAs: List<X509Certificate> = listOf()
)
