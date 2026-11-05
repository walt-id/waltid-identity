package id.walt.verifier2.mdocs

import id.walt.cose.CoseCertificate
import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.cose.coseCompliantCbor
import id.walt.credentials.CredentialParser
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.x509.GenericX509CertificateBuilder
import id.walt.x509.GenericX509CertificateProfileData
import id.walt.x509.X509DistinguishedName
import id.walt.x509.X509KeyUsage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

private val holder by lazy { runBlocking { JWKKey.generate(KeyType.secp256r1) } }
private val issuer by lazy {
    runBlocking {
        val rootKey = JWKKey.generate(KeyType.secp256r1)
        val signer = JWKKey.generate(KeyType.secp256r1)
        val rootName = X509DistinguishedName("Proximity test IACA", country = "AT")
        val builder = GenericX509CertificateBuilder()
        val root = builder.build(
            GenericX509CertificateProfileData(subjectName = rootName, isCertificateAuthority = true, keyUsage = setOf(X509KeyUsage.KeyCertSign)),
            rootKey.getPublicKey(), rootKey,
        )
        val leaf = builder.build(
            GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName("Proximity test DS", country = "AT"), issuerName = rootName,
                keyUsage = setOf(X509KeyUsage.DigitalSignature), extendedKeyUsageOids = setOf("1.0.18013.5.1.2"),
            ), signer.getPublicKey(), rootKey,
        )
        signer to listOf(CoseCertificate(leaf.certificateDer.bytes.toByteArray()), CoseCertificate(root.certificateDer.bytes.toByteArray()))
    }
}

internal fun releaseHolderKey(): JWKKey = holder

internal fun mdlFixtureCredential(credentialData: String) = runBlocking {
    val data = Json.decodeFromString<JsonObject>(credentialData)
    val docType = data.getValue("docType").jsonPrimitive.content
    val (signer, chain) = issuer
    val issued = MdocIssuer.issueUniversal(
        issuerKey = signer, issuerCertificate = chain, holderKey = holder.getPublicKey().getCosePublicKey(), docType = docType,
        data = MdocIssuer.MdocUniversalIssuanceData(data.filterValues { it is JsonObject }.mapValues { it.value as JsonObject }),
        validUntil = Clock.System.now() + 30.days,
    )
    CredentialParser.handleMdocs(Base64.UrlSafe.encode(coseCompliantCbor.encodeToByteArray(Document(docType, issued))), base64 = true).second
}
