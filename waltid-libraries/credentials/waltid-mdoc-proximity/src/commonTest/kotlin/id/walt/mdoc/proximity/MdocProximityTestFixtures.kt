@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.KeyAuthorization
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal suspend fun CryptoRuntime.generateMdocTestKey(id: String, usages: Set<KeyUsage>): Key =
    generateSoftwareKey(GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), usages))

internal suspend fun CryptoRuntime.issueMdocTestDocument(
    holderKey: Key,
    keyAuthorizations: KeyAuthorization? = null,
): Document {
    val issuerKey = generateMdocTestKey(
        "issuer-${holderKey.id.value}",
        setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    )
    val holderPublic = (holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
    val certificate = X509CertificateUtil.createSelfSignedCertificate(
        issuerKey,
        SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
    ) { subjectDn = "CN=mdoc test issuer" }
    val issuerSigned = MdocIssuer.issueUniversal(
        issuerKey = issuerKey,
        signatureAlgorithm = Cose.Algorithm.ES256,
        issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
        holderKey = holderPublic,
        docType = "org.example.mdoc",
        data = MdocIssuer.MdocUniversalIssuanceData(
            namespaces = mapOf(
                "org.example" to JsonObject(mapOf("given_name" to JsonPrimitive("Jane")))
            )
        ),
        keyAuthorizations = keyAuthorizations,
    )
    return Document("org.example.mdoc", issuerSigned)
}
