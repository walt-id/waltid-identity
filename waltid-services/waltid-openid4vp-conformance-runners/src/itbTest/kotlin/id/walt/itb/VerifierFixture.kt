package id.walt.itb

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import kotlinx.serialization.json.JsonObject
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.time.Instant
import java.util.Date

/** Per-run certificates with actual signing KeyUsage; no expiring committed certificate fixture. */
internal object VerifierFixture {
    private val root = ECKeyGenerator(Curve.P_256).generate()
    private val leaf = ECKeyGenerator(Curve.P_256).generate()
    private val issuerName = X500Name("CN=ITB synthetic verifier CA")
    private val start = Date.from(Instant.now().minusSeconds(60))
    private val end = Date.from(Instant.now().plusSeconds(86_400))
    private val signer = JcaContentSignerBuilder("SHA256withECDSA").build(root.toECPrivateKey())
    private val rootCertificate = JcaX509v3CertificateBuilder(
        issuerName, BigInteger.ONE, start, end, issuerName, root.toECPublicKey(),
    ).apply {
        addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
    }.build(signer)
    private val leafCertificate = JcaX509v3CertificateBuilder(
        issuerName, BigInteger.TWO, start, end, X500Name("CN=verifier.example.com"), leaf.toECPublicKey(),
    ).apply {
        addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        addExtension(Extension.subjectAlternativeName, false, GeneralNames(GeneralName(GeneralName.dNSName, "verifier.example.com")))
    }.build(signer)

    val trust = ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(listOf(
        X509CertificateUtil.parseCertificatePem(
            "-----BEGIN CERTIFICATE-----\n${Base64.encode(rootCertificate.encoded)}\n-----END CERTIFICATE-----"
        )
    )))

    fun sign(payload: JsonObject): String = JWSObject(
        JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("oauth-authz-req+jwt"))
            .x509CertChain(listOf(Base64.encode(leafCertificate.encoded))).build(),
        Payload(payload.toString()),
    ).apply { sign(ECDSASigner(leaf)) }.serialize()
}
