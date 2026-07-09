package id.walt.certificate.x509.validation

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore

class X509CertificateChain private constructor(private val certChainList: List<CertChainEntry>) {

    val size = certChainList.size

    operator fun get(index: Int) = certChainList[index].entry

    companion object {

        suspend fun of(
            trustStore: X509CertificateTrustStore,
            certificateList: Collection<X509Certificate>
        ): X509CertificateChain {

            val subjectToCertMap = certificateList
                .distinctBy { it.fingerprintSha256 }
                .map {
                    CertChainEntry(
                        isTrusted = false,
                        entry = it
                    )
                }
                .associateBy { it.subjectDn }

            val issuerDnToCertMap = subjectToCertMap.values
                .associateBy { it.issuerDn }

            val potentialRoots = subjectToCertMap
                .values
                .filter { it.subjectDn == it.issuerDn || !subjectToCertMap.keys.contains(it.issuerDn) }
                .toList()

            require(potentialRoots.size == 1) {
                "Identified multiple roots in the certificate chain: ${potentialRoots.map { it.subjectDn }}"
            }
            val chainList = mutableListOf<CertChainEntry>()
            val rootOfProvidedCertificateList = potentialRoots.first()
            chainList.add(rootOfProvidedCertificateList)
            do {
                val issuer = chainList.last()
                val cert = issuerDnToCertMap[issuer.subjectDn]
                if (cert != null) {
                    chainList.add(cert)
                } else {
                    break
                }
            } while (true)
            return X509CertificateChain(chainList.toList())
        }
    }

    private data class CertChainEntry(
        val isTrusted: Boolean = false,
        val entry: X509Certificate,
        var parent: CertChainEntry? = null,
    ) {
        val subjectDn: String = entry.data.subjectDn
        val issuerDn: String = entry.data.issuerDn
    }
}