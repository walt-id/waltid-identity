package id.walt.certificate.x509.validation

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import kotlinx.io.bytestring.ByteString

/**
 * A certificate chain ordered root first: index 0 is the certificate closest to the trust anchor (the
 * self-issued root, or the topmost certificate whose issuer is not in the chain), and index
 * [size] - 1 is the leaf.
 *
 * The chain is built from the leaf up to the root. A certificate is considered issued by another one if
 * its issuer DN equals the other's subject DN and, when both are present, its authority key identifier
 * equals the other's subject key identifier. Signatures and trust are checked by the validators, not here.
 *
 * Each certificate also remembers where it appeared in the list it was built from, so callers can
 * relate a position back to the input (for example an `x5chain`, which is leaf first).
 */
class X509CertificateChain private constructor(
    private val certChainList: List<InternalCertChainEntry>,
    private val providedWithoutDuplicates: Boolean,
) {

    val size: Int = certChainList.size

    /** Certificate at [index] in root-first order. */
    operator fun get(index: Int): X509Certificate = certChainList[index].entry

    fun getEntry(index: Int): CertChainEntry = certChainList[index]

    /** Certificates in root-first order. */
    val certificates: List<X509Certificate> get() = certChainList.map { it.entry }

    /**
     * `true` if the certificates were provided exactly once each, leaf first and each followed by its
     * issuer — the order required by e.g. `x5chain`/`x5c`.
     */
    val isProvidedLeafFirst: Boolean
        get() = providedWithoutDuplicates &&
                certChainList.indices.all { certChainList[it].indexInProvidedChain == size - 1 - it }

    companion object {

        /**
         * Orders [certificateList] (any order; exact duplicates are ignored) into a single chain, starting
         * at the one leaf (the certificate that issued no other) and following each certificate to its issuer.
         *
         * @throws IllegalArgumentException if the list is empty, a certificate has several possible issuers,
         * the chain branches (a certificate issued several others), there is no or several top
         * certificates, or the list contains certificates that are not part of the one chain.
         */
        fun of(certificateList: Collection<X509Certificate>): X509CertificateChain {
            require(certificateList.isNotEmpty()) { "Certificate chain is empty" }

            val entries = certificateList
                .withIndex()
                .distinctBy { it.value.fingerprintSha256 }
                .map { (index, certificate) ->
                    InternalCertChainEntry(
                        entry = certificate,
                        indexInProvidedChain = index,
                    )
                }

            val issuerOf: Map<InternalCertChainEntry, InternalCertChainEntry?> = entries.associateWith { entry ->
                if (entry.isSelfIssued) return@associateWith null
                val candidates = entries.filter { it !== entry && entry.isIssuedBy(it) }
                require(candidates.size <= 1) {
                    "Ambiguous issuer in the certificate chain: '${entry.subjectDn}' may have been issued by " +
                            "several certificates with subject '${entry.issuerDn}'"
                }
                candidates.singleOrNull()
            }

            entries.forEach { entry ->
                val issued = entries.filter { issuerOf[it] === entry }
                require(issued.size <= 1) {
                    "Certificate chain branches: multiple certificates issued by '${entry.subjectDn}': " +
                            "${issued.map { it.subjectDn }}"
                }
            }

            val tops = entries.filter { issuerOf[it] == null }
            require(tops.isNotEmpty()) {
                "No root found in the certificate chain: the issuer links form a cycle"
            }
            require(tops.size == 1) {
                "Identified multiple roots in the certificate chain: ${tops.map { it.subjectDn }}"
            }

            // Without branching and with a single top, the one path ends in a single leaf; any other
            // certificates form cycles and are reported below.
            val issuers = issuerOf.values.filterNotNull().toSet()
            val leaf = entries.single { it !in issuers }

            val leafFirst = mutableListOf(leaf)
            while (true) {
                val issuer = issuerOf.getValue(leafFirst.last()) ?: break
                if (issuer in leafFirst) break
                leafFirst.add(issuer)
            }
            require(leafFirst.size == entries.size) {
                "Not all certificates could be added to chain of trust: " +
                        "${(entries - leafFirst.toSet()).map { it.subjectDn }}"
            }
            return X509CertificateChain(
                leafFirst.asReversed().toList(),
                providedWithoutDuplicates = entries.size == certificateList.size,
            )
        }
    }

    interface CertChainEntry {
        val entry: X509Certificate
        val indexInProvidedChain: Int?
    }

    private class InternalCertChainEntry(
        override val entry: X509Certificate,

        /**
         * Position in the provided certificate list (0 = first element, the leaf for a leaf-first
         * chain); `null` for a certificate that was not provided.
         */
        override val indexInProvidedChain: Int?,
    ) : CertChainEntry {
        val subjectDn: String = entry.data.subjectDn
        val issuerDn: String = entry.data.issuerDn
        val subjectKeyId: ByteString? = entry.data.extensionSubjectKeyIdentifier?.keyIdentifier
        val authorityKeyId: ByteString? = entry.data.extensionAuthorityKeyIdentifier?.keyIdentifier

        /** Issued by its own key; a same-DN certificate with a different key (e.g. a key rollover) is not. */
        val isSelfIssued: Boolean = isIssuedBy(this)

        /**
         * Whether [issuer] may have issued this certificate: the DNs link and the key identifiers do not
         * contradict each other. Key identifiers are compared only when both are present and non-empty.
         */
        fun isIssuedBy(issuer: InternalCertChainEntry): Boolean {
            if (issuerDn != issuer.subjectDn) return false
            val aki = authorityKeyId?.takeIf { it.size > 0 } ?: return true
            val ski = issuer.subjectKeyId?.takeIf { it.size > 0 } ?: return true
            return aki == ski
        }
    }
}
