@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package id.walt.proximity.physical

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.profileDocumentSignerCertificate
import id.walt.cose.*
import id.walt.credentials.CredentialParser
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.wallet2.data.*
import id.walt.wallet2.stores.inmemory.*
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*


internal data class PhysicalCredentialFixture(val wallet: Wallet, val runtime: CryptoRuntime,
    val root: ByteArray, val untrustedRoot: ByteArray) {
    companion object {
        const val NAMESPACE = "org.iso.18013.5.1"
        const val DOC_TYPE = "org.iso.18013.5.1.mDL"
        suspend fun create(): PhysicalCredentialFixture {
            val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
            suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
                id = KeyId(id), spec = KeySpec.Ec(EcCurve.P256), usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)))
            val holder = key("peer-holder")
            val issuer = key("peer-issuer")
            val root = key("peer-root")
            val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
            val rootCertificate = X509CertificateUtil.createSelfSignedCertificate(root, algorithm) {
                subjectDn = "CN=Independent peer test root"
            }
            val untrustedRoot = X509CertificateUtil.createSelfSignedCertificate(key("untrusted-root"), algorithm) {
                subjectDn = "CN=Untrusted peer test root"
            }
            val signer = X509CertificateUtil.createCertificate(root, rootCertificate, algorithm) {
                profileDocumentSignerCertificate(crlDistributionPointUri = "https://issuer.example/crl",
                    issuerUri = "https://issuer.example", subjectKey = issuer, subjectDnCountryCode = "AT",
                    subjectDnOrganizationName = "Synthetic test", subjectDnCommonName = "Peer document signer")
            }
            val issued = MdocIssuer.issueUniversal(issuerKey = issuer, signatureAlgorithm = Cose.Algorithm.ES256,
                issuerCertificate = listOf(CoseCertificate(signer.encodedDer.toByteArray())),
                holderKey = (requireNotNull(holder.capabilities.publicKeyExporter).exportPublicKey() as EncodedKey.Jwk).toCoseKey(),
                docType = DOC_TYPE, data = MdocIssuer.MdocUniversalIssuanceData(mapOf(NAMESPACE to JsonObject(mapOf(
                    "given_name" to JsonPrimitive("Ada"), "family_name" to JsonPrimitive("Lovelace"),
                )))))
            val wallet = Wallet("independent-peer", keyStores = listOf(InMemoryKeyStore().also { it.addCrypto2Key(holder) }),
                credentialStores = listOf(InMemoryCredentialStore()))
            val encoded = coseCompliantCbor.encodeToByteArray(Document.serializer(), Document(DOC_TYPE, issued)).encodeToBase64Url()
            wallet.addCredential(wallet.withImportedHolderKeyBinding(StoredCredential("peer-mdl", CredentialParser.detectAndParse(encoded).second)))
            return PhysicalCredentialFixture(wallet, runtime, rootCertificate.encodedDer.toByteArray(), untrustedRoot.encodedDer.toByteArray())
        }

    }
}
