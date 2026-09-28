package id.walt.mdoc.proximity

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.cose.Cose
import id.walt.cose.verify
import id.walt.mdoc.proximity.MdocX509CertificateUtil.mdocRicalSignerCertificateUtil
import id.walt.mdoc.proximity.MdocX509CertificateUtil.modocReaderAuthentication
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlin.time.Clock

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
        trustedProviderRootsDer: List<ImmutableBytes>,
    ): Boolean = try {
        val signerChain =
            signed.signerChainDer.map { X509CertificateUtil.parseCertificateDerEncoded(ByteString(it.copy())) }
        val roots = trustedProviderRootsDer.map {
            X509CertificateUtil.parseCertificateDerEncoded(
                ByteString(it.copy())
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

    private val mdocAuthenticationCertificateUtil = modocReaderAuthentication(clock)

    override suspend fun validate(
        reader: ReaderAuthenticationEvidence,
        rical: Rical,
    ): RicalReaderPathResult {
        val readerChain = reader.certificateChainDer.map {
            X509CertificateUtil.parseCertificateDerEncoded(ByteString(it.copy()))
        }
        if (readerChain.isEmpty()) return RicalReaderPathResult.Invalid
        val ricalCertificates =
            rical.certificateInfos.associateWith {
                X509CertificateUtil.parseCertificateDerEncoded(
                    ByteString(it.certificateDer.copy())
                )
            }
        // Build to explicit anchors, then retain the highest applicable anchor. A lower
        // CertificateInfo can itself be an anchor without losing its more specific constraints.
        val paths =
            rical.certificateInfos.filter(RicalCertificateInfo::isTrustAnchor).mapNotNull { info ->
                runCatching {
                    val trustAnchor = ricalCertificates.getValue(info)
                    val validationResult =
                        mdocAuthenticationCertificateUtil.validateCertificateChain(
                            certificateChain = readerChain,
                            trustOverride = InMemoryTrustStore(listOf(trustAnchor))
                        )
                    if (validationResult.valid) {
                        if (readerChain.map { it.encodedDer }.toSet()
                                .contains(trustAnchor.encodedDer)
                        ) {
                            readerChain
                        } else {
                            readerChain + trustAnchor
                        }
                    } else {
                        null
                    }
                }.getOrNull()
            }.distinct()
        if (paths.isEmpty()) return RicalReaderPathResult.NoMatch
        val maximalPaths = paths.filter { path ->
            paths.none { other -> other.size > path.size && other.take(path.size) == path }
        }
        // Different validated routes are not interchangeable constraint or revocation evidence.
        val path = maximalPaths.singleOrNull() ?: return RicalReaderPathResult.Invalid
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

// DIS F.3.2 lists EdDSA at the COSE layer, while mandatory Table F.1 requires the
// RICAL signer certificate to contain an EC public key. Keep parsing forward-compatible,
// but only accept the algorithms that can satisfy the mandatory signer profile here.
private val RICAL_SIGNATURE_ALGORITHMS = setOf(
    Cose.Algorithm.ES256,
    Cose.Algorithm.ES384,
    Cose.Algorithm.ES512,
)
