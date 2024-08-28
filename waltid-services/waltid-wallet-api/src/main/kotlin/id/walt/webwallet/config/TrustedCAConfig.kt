package id.walt.webwallet.config

import kotlinx.serialization.Serializable
import org.bouncycastle.asn1.x509.BasicConstraints
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@Serializable
data class TrustedCAConfig(
    val pemEncodedTrustedCACertificates: List<String> = emptyList(),
) : WalletConfig() {

    init {
        //add checks here
        val certificateFactory = CertificateFactory.getInstance("X509")
        for (pemCertificate in pemEncodedTrustedCACertificates) {
            val trustedCACertificate = certificateFactory.generateCertificate(
                ByteArrayInputStream(
                    pemCertificate.toByteArray()
                )
            ) as X509Certificate
            //check that the trusted CA certificate is currently valid as this is not
            //checked as part of the x5c certificate path validation
            trustedCACertificate.checkValidity()
            //check that this is actually a CA certificate as per RFC 5280: https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.9
            val certBasicConstraints = BasicConstraints(trustedCACertificate.basicConstraints)
            require(certBasicConstraints.isCA) {
                "Trusted CA certificate loaded from config:\n" +
                        pemCertificate +
                        "\n is actually not a CA certificate. Basic constraints cA flag is set to false.\n"
            }
        }
    }
}
