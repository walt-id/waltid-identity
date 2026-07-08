package id.walt.openid4vp.clientidprefix

import id.walt.x509.CertificateDer
import id.walt.x509.subjectAlternativeDnsNames

fun extractSanDnsNamesFromDer(der: ByteArray): Result<List<String>> =
    runCatching {
        CertificateDer(der).subjectAlternativeDnsNames
    }
