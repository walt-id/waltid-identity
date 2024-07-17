package id.walt.issuer.utils

import COSE.AlgorithmID
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.sdjwt.SimpleJWTCryptoProvider
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

object LspPotentialInteropEvent {
  val POTENTIAL_ROOT_CA_CERT = "-----BEGIN CERTIFICATE-----\n" +
      "MIIBQzCB66ADAgECAgjbHnT+6LsrbDAKBggqhkjOPQQDAjAYMRYwFAYDVQQDDA1NRE9DIFJPT1QgQ1NQMB4XDTI0MDUwMjEzMTMzMFoXDTI0MDUwMzEzMTMzMFowFzEVMBMGA1UEAwwMTURPQyBST09UIENBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWP0sG+CkjItZ9KfM3sLF+rLGb8HYCfnlsIH/NWJjiXkTx57ryDLYfTU6QXYukVKHSq6MEebvQPqTJT1blZ/xeKMgMB4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwIDRwAwRAIgWM+JtnhdqbTzFD1S3byTvle0n/6EVALbkKCbdYGLn8cCICOoSETqwk1oPnJEEPjUbdR4txiNqkHQih8HKAQoe8t5\n" +
      "-----END CERTIFICATE-----\n"
  val POTENTIAL_ROOT_CA_PRIV = "-----BEGIN PRIVATE KEY-----\n" +
      "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCBXPx4eVTypvm0pQkFdqVXlORn+YIFNb+Hs5xvmG3EM8g==\n" +
      "-----END PRIVATE KEY-----\n"
  val POTENTIAL_ROOT_CA_PUB = "-----BEGIN PUBLIC KEY-----\n" +
      "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWP0sG+CkjItZ9KfM3sLF+rLGb8HYCfnlsIH/NWJjiXkTx57ryDLYfTU6QXYukVKHSq6MEebvQPqTJT1blZ/xeA==\n" +
      "-----END PUBLIC KEY-----\n"
  val POTENTIAL_ISSUER_CERT = "-----BEGIN CERTIFICATE-----\n" +
      "MIIBRzCB7qADAgECAgg57ch6mnj5KjAKBggqhkjOPQQDAjAXMRUwEwYDVQQDDAxNRE9DIFJPT1QgQ0EwHhcNMjQwNTAyMTMxMzMwWhcNMjUwNTAyMTMxMzMwWjAbMRkwFwYDVQQDDBBNRE9DIFRlc3QgSXNzdWVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gaMgMB4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwCgYIKoZIzj0EAwIDSAAwRQIhAI5wBBAA3ewqIwslhuzFn4rNFW9dkz2TY7xeImO7CraYAiAYhai1NzJ6abAiYg8HxcRdYpO4bu2Sej8E6CzFHK34Yw==\n" +
      "-----END CERTIFICATE-----"
  val POTENTIAL_ISSUER_PUB = "-----BEGIN PUBLIC KEY-----\n" +
      "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gQ==\n" +
      "-----END PUBLIC KEY-----\n"
  val POTENTIAL_ISSUER_PRIV = "-----BEGIN PRIVATE KEY-----\n" +
      "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAoniTdVyXlKP0x+rius1cGbYyg+hjf8CT88hH8SCwWFA==\n" +
      "-----END PRIVATE KEY-----\n"
  val POTENTIAL_ISSUER_KEY_ID = "potential-lsp-issuer-key-01"
  val POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO = loadPotentialIssuerKeys()
  val POTENTIAL_JWT_CRYPTO_PROVIDER = SimpleJWTCryptoProvider(
    JWSAlgorithm.ES256,
    ECDSASigner(ECKey.parseFromPEMEncodedObjects(POTENTIAL_ISSUER_PRIV + POTENTIAL_ISSUER_PUB).toECKey()), ECDSAVerifier(
      ECKey.parseFromPEMEncodedObjects(POTENTIAL_ISSUER_PUB).toECKey())
  )
  val POTENTIAL_ISSUER_KEY_JWK = ECKey.parseFromPEMEncodedObjects(POTENTIAL_ISSUER_PRIV + POTENTIAL_ISSUER_PUB).toJSONString()

  fun readKeySpec(pem: String): ByteArray {
    val publicKeyPEM = pem
      .replace("-----BEGIN PUBLIC KEY-----", "")
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace(System.lineSeparator().toRegex(), "")
      .replace("-----END PUBLIC KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")

    return Base64.getDecoder().decode(publicKeyPEM)
  }

  fun loadPotentialIssuerKeys(): COSECryptoProviderKeyInfo {
    val factory = CertificateFactory.getInstance("X.509")
    val rootCaCert = (factory.generateCertificate(POTENTIAL_ROOT_CA_CERT.byteInputStream())) as X509Certificate
    val issuerCert = (factory.generateCertificate(POTENTIAL_ISSUER_CERT.byteInputStream())) as X509Certificate
    val issuerPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(readKeySpec(POTENTIAL_ISSUER_PUB)))
    val issuerPriv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(readKeySpec(POTENTIAL_ISSUER_PRIV)))
    return COSECryptoProviderKeyInfo(POTENTIAL_ISSUER_KEY_ID, AlgorithmID.ECDSA_256, issuerPub, issuerPriv, listOf(issuerCert), listOf(rootCaCert))
  }
}
