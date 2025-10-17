package id.walt.openid4vp.clientidprefix

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

actual fun extractSanDnsNamesFromDer(der: ByteArray): Result<List<String>> {
    return runCatching {
        val certFactory = CertificateFactory.getInstance("X.509")
        val cert = certFactory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate

        val dnsNames = mutableListOf<String>()
        cert.subjectAlternativeNames?.forEach { san ->
            // GeneralName SAN entry: index 0 is type, index 1 is value.
            // dNSName type is 2.
            if (san.size == 2 && san[0] == 2) {
                dnsNames.add(san[1].toString())
            }
        }
        dnsNames
    }
}
