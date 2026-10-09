package id.walt.mdoc.proximity

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.AuthorityKeyIdentifierExtension.Companion.extensionAuthorityKeyIdentifier
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.cose.Cose
import id.walt.cose.verify
import id.walt.mdoc.proximity.MdocX509CertificateUtil.mdocRicalSignerCertificateUtil
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlin.time.Clock
import id.walt.mdoc.readertrust.MdocReaderAuthenticationCertificateUtil.mdocReaderAuthentication
import id.walt.mdoc.readertrust.ReaderAuthenticationEvidence

/**
 * Concrete RICAL (Reader Identity Certificate Authority List) COSE-signature,
 * signer-profile, and explicit-provider-root validator.
 */
class X509RicalSignatureValidator(
    acceptedCertificatePolicyOids: Collection<String>,
    clock: Clock
) : RicalSignatureValidator {
    private val acceptedCertificatePolicyOids = acceptedCertificatePolicyOids.toSet()
    private val mdocRicalSignerCertificateUtil =
        mdocRicalSignerCertificateUtil(acceptedCertificatePolicyOids, clock)

    init {
        require(this.acceptedCertificatePolicyOids.isNotEmpty())
        require(this.acceptedCertificatePolicyOids.none(String::isBlank))
    }

    override suspend fun validate(
        signed: SignedRical,
        trustedProviderRootsDer: List<ByteString>,
    ): Boolean = try {
        val signerChain =
            signed.signerChainDer.map { X509CertificateUtil.parseCertificateDerEncoded(ByteString(it.toByteArray())) }
        val roots = trustedProviderRootsDer.map {
            X509CertificateUtil.parseCertificateDerEncoded(
                ByteString(it.toByteArray())
            )
        }
        val signer = signerChain.first()
        val validationResult = mdocRicalSignerCertificateUtil.validateCertificateChain(
            signerChain,
            InMemoryTrustStore(roots)
        )
        if (validationResult.valid) {
            signed.coseSign1.verify(
                signer.restoreSubjectPublicKey(X509CertificateUtil.services.cryptoRuntime),
                RICAL_SIGNATURE_ALGORITHMS,
            )
        } else {
            false
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }
}

/**
 * Validates a reader path against the complete active RICAL and selects only its bottom-most
 * matching CertificateInfo for constraint evaluation.
 */
class X509RicalReaderPathValidator(clock: Clock = Clock.System) : RicalReaderPathValidator {

    private val mdocAuthenticationCertificateUtil = mdocReaderAuthentication(clock)

    override suspend fun validate(
        reader: ReaderAuthenticationEvidence,
        rical: Rical,
    ): RicalReaderPathResult {
        val readerChain = reader.certificateChainDer.map {
            X509CertificateUtil.parseCertificateDerEncoded(ByteString(it.toByteArray()))
        }
        if (readerChain.isEmpty()) return RicalReaderPathResult.Invalid
        val ricalCertificates =
            rical.certificateInfos.associateWith {
                X509CertificateUtil.parseCertificateDerEncoded(
                    ByteString(it.certificateDer.toByteArray())
                )
            }
        // Complete the reader chain with RICAL intermediates up to the highest RICAL trust anchor. A lower
        // CertificateInfo can itself be an anchor without losing its more specific constraints.
        val (pathWithoutAnchor, trustAnchor) = runCatching { rical.pathForChain(readerChain) }.getOrElse {
            if (it !is IllegalArgumentException) throw it
            return RicalReaderPathResult.NoMatch
        }
        val valid = runCatching {
            mdocAuthenticationCertificateUtil.validateCertificateChain(
                certificateChain = pathWithoutAnchor,
                trustOverride = InMemoryTrustStore(listOf(trustAnchor))
            ).valid
        }.getOrElse {
            if (it is CancellationException) throw it
            false
        }
        if (!valid) return RicalReaderPathResult.NoMatch
        val path = pathWithoutAnchor + trustAnchor
        val authority = path.drop(1).firstNotNullOfOrNull { certificate ->
            ricalCertificates.entries.singleOrNull {
                it.value.encodedDer == certificate.encodedDer
            }?.key
        } ?: return RicalReaderPathResult.NoMatch
        return RicalReaderPathResult.Valid(
            authority,
            path
        )
    }
}

/**
 * Builds the path from the reader leaf up to the highest RICAL trust anchor. Issuers are looked up by
 * subject DN (disambiguated by AKI/SKI when several certificates share a DN), first among the reader-provided certificates, then among the RICAL certificates.
 *
 * @param chain provided by the reader (any order, must form a single path)
 * @return path without trust anchor, leaf first (provided reader certificates plus intermediate certificates
 * from the RICAL), and the trust anchor
 * @throws IllegalArgumentException if the chain is empty or ambiguous, or does not lead to a RICAL trust anchor
 */
internal fun Rical.pathForChain(chain: Collection<X509Certificate>): Pair<List<X509Certificate>, X509Certificate> {
    val readerCertificates = chain.distinctBy { it.encodedDer }
    require(readerCertificates.isNotEmpty()) { "Reader certificate chain is empty" }

    val ricalCertificates = certificateInfos.map {
        it to X509CertificateUtil.parseCertificateDerEncoded(ByteString(it.certificateDer.toByteArray()))
    }
    val anchorDers =
        ricalCertificates.filter { it.first.isTrustAnchor }.map { it.second.encodedDer }.toSet()

    val issuerDns = readerCertificates.map { it.data.issuerDn }.toSet()
    val leaf = readerCertificates.filter { it.data.subjectDn !in issuerDns }.singleOrNull()
        ?: readerCertificates.singleOrNull()
        ?: throw IllegalArgumentException("Reader certificate chain does not form a single path")

    val candidates =
        (readerCertificates + ricalCertificates.map { it.second }).distinctBy { it.encodedDer }
    val path = mutableListOf(leaf)
    while (true) {
        val current = path.last()
        if (current.data.subjectDn == current.data.issuerDn) break
        val issuer = candidates.firstOrNull { candidate ->
            candidate.data.subjectDn == current.data.issuerDn &&
                    current.data.extensionAuthorityKeyIdentifier?.let { authorityKeyIdentifierExtension ->
                        val candidateSubjectKeyIdExtension =
                            candidate.data.extensionSubjectKeyIdentifier
                        if (candidateSubjectKeyIdExtension == null) {
                            true
                        } else {
                            authorityKeyIdentifierExtension.keyIdentifier?.equals(
                                candidateSubjectKeyIdExtension.keyIdentifier
                            ) ?: false
                        }
                    } ?: true &&
                    path.none { it.encodedDer == candidate.encodedDer }
        } ?: break
        path.add(issuer)
    }

    val anchorIndex = path.indexOfLast { it.encodedDer in anchorDers }
    require(anchorIndex >= 0) { "Reader certificate chain does not lead to a RICAL trust anchor" }
    return path.take(anchorIndex) to path[anchorIndex]
}

// DIS F.3.2 lists EdDSA at the COSE layer, while mandatory Table F.1 requires the
// RICAL signer certificate to contain an EC public key. Keep parsing forward-compatible,
// but only accept the algorithms that can satisfy the mandatory signer profile here.
private val RICAL_SIGNATURE_ALGORITHMS = setOf(
    Cose.Algorithm.ES256,
    Cose.Algorithm.ES384,
    Cose.Algorithm.ES512,
)
