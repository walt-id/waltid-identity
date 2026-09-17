package id.walt.mdoc.proximity

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.verify
import id.walt.x509.CertificateDer
import id.walt.x509.validatedMdocReaderAuthenticationCertificatePath
import id.walt.x509.validateRicalSignerCertificateChain
import id.walt.x509.validateRicalSignerCertificateProfile
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlin.time.Clock
import kotlin.time.Instant

/** Concrete RICAL COSE-signature, signer-profile, and explicit-provider-root validator. */
class X509RicalSignatureValidator(
    acceptedCertificatePolicyOids: Set<String>,
    private val now: () -> Instant = { Clock.System.now() },
) : RicalSignatureValidator {
    private val acceptedCertificatePolicyOids = acceptedCertificatePolicyOids.toSet()

    init {
        require(this.acceptedCertificatePolicyOids.isNotEmpty())
        require(this.acceptedCertificatePolicyOids.none(String::isBlank))
    }

    override suspend fun validate(
        signed: SignedRical,
        trustedProviderRootsDer: List<ImmutableBytes>,
    ): Boolean = try {
        val evaluatedAt = now()
        val signerChain = signed.signerChainDer.map { CertificateDer(it.copy()) }
        val roots = trustedProviderRootsDer.map { CertificateDer(it.copy()) }
        val leaf = signerChain.first()
        validateRicalSignerCertificateProfile(leaf, acceptedCertificatePolicyOids, evaluatedAt)
        validateRicalSignerCertificateChain(leaf, signerChain.drop(1), roots, evaluatedAt)
        val signer = X509CertificateUtil.parseCertificateDerEncoded(ByteString(leaf.bytes.toByteArray()))
        signed.coseSign1.verify(
            signer.restoreSubjectPublicKey(X509CertificateUtil.services.cryptoRuntime),
            RICAL_SIGNATURE_ALGORITHMS,
        )
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
class X509RicalReaderPathValidator(
    private val now: () -> Instant = { Clock.System.now() },
) : RicalReaderPathValidator {
    override suspend fun validate(
        reader: ReaderAuthenticationEvidence,
        rical: Rical,
    ): RicalReaderPathResult {
        val evaluatedAt = now()
        val readerChain = reader.certificateChainDer.map { CertificateDer(it.copy()) }
        if (readerChain.isEmpty()) return RicalReaderPathResult.Invalid
        val ricalCertificates = rical.certificateInfos.associateWith { CertificateDer(it.certificateDer.copy()) }
        // Build to explicit anchors, then retain the highest applicable anchor. A lower
        // CertificateInfo can itself be an anchor without losing its more specific constraints.
        val paths = rical.certificateInfos.filter(RicalCertificateInfo::isTrustAnchor).mapNotNull { info ->
            runCatching {
                validatedMdocReaderAuthenticationCertificatePath(
                    leaf = readerChain.first(),
                    chain = readerChain.drop(1) + ricalCertificates.values,
                    trustAnchors = listOf(ricalCertificates.getValue(info)),
                    now = evaluatedAt,
                )
            }.getOrNull()
        }.distinct()
        if (paths.isEmpty()) return RicalReaderPathResult.NoMatch
        val maximalPaths = paths.filter { path ->
            paths.none { other -> other.size > path.size && other.take(path.size) == path }
        }
        // Different validated routes are not interchangeable constraint or revocation evidence.
        val path = maximalPaths.singleOrNull() ?: return RicalReaderPathResult.Invalid
        val authority = path.drop(1).firstNotNullOfOrNull { certificate ->
            ricalCertificates.entries.singleOrNull { it.value == certificate }?.key
        } ?: return RicalReaderPathResult.NoMatch
        return RicalReaderPathResult.Valid(authority, path.map { ImmutableBytes.of(it.bytes.toByteArray()) })
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
