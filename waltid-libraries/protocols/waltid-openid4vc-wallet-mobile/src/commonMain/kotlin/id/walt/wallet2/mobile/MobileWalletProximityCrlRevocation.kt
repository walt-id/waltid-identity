package id.walt.wallet2.mobile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.revocation.CertificateRevocationListVerifier
import id.walt.certificate.x509.revocation.CrlCertificateStatus
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

/** Certificates whose CRL status the hosting application requires. */
public enum class ProximityCrlScope {
    /** Check the reader certificate against its direct issuer's CRL. */
    ReaderCertificate,

    /** Also check issuing authorities, including the terminal self-signed authority. */
    ReaderCertificateAndIssuingAuthorities,
}

/** A complete DER CRL retrieved by the application's network/cache policy. */
public sealed interface ProximityCrlFetchResult {
    /**
     * A retrieved CRL whose signature, scope and freshness are checked by the SDK.
     *
     * @property crlDerBase64Url Unpadded Base64URL-encoded DER CRL.
     */
    public data class Available(public val crlDerBase64Url: String) : ProximityCrlFetchResult

    /** No complete CRL could be retrieved within the application's transport policy. */
    public data object Unavailable : ProximityCrlFetchResult
}

/** Application-owned transport for explicit CRL distribution-point requests. */
public fun interface ProximityCrlFetcher {
    /**
     * Fetch at most [maximumBytes] from [url]. Apply application timeout, redirect and destination
     * policy before returning. The SDK neither opens connections nor retains a cache between calls.
     */
    public suspend fun fetch(url: String, maximumBytes: Int): ProximityCrlFetchResult
}

/**
 * Reader-certificate CRL evaluator for [ProximityReaderRevocationPolicy.Check].
 *
 * [issuerCertificatesDerBase64Url] supplies the public issuer certificates needed to follow the
 * authenticated reader chain. They are lookup material and do not establish trust. With authority
 * checking selected, provide the path through its terminal self-signed authority; each checked
 * certificate must have an applicable HTTP(S) CRL distribution point.
 *
 * The SDK verifies complete direct v2 CRLs, including CA revocation, with ECDSA/RSA PKCS#1 and
 * SHA-256/384/512. Unsupported CRL forms, absent status, invalid signatures and stale responses
 * remain indeterminate. A verified revocation takes precedence over an unavailable status elsewhere
 * in the selected chain. The application continues to own fetching and certificate-path trust.
 */
public class ProximityCrlRevocationEvaluator internal constructor(
    issuerCertificatesDerBase64Url: List<String>,
    private val scope: ProximityCrlScope,
    private val fetcher: ProximityCrlFetcher,
    private val now: () -> Instant,
) : ProximityReaderRevocationEvaluator {
    private val issuers: List<X509Certificate>
    private val verifier = CertificateRevocationListVerifier()

    init {
        require(issuerCertificatesDerBase64Url.size in 1..MAX_CHAIN_LENGTH)
        issuers = issuerCertificatesDerBase64Url.toList().map(::parseCrlCertificate)
            .distinctBy { it.encodedDer }
    }

    @Throws(IllegalArgumentException::class)
    public constructor(
        issuerCertificatesDerBase64Url: List<String>,
        scope: ProximityCrlScope,
        fetcher: ProximityCrlFetcher,
    ) : this(issuerCertificatesDerBase64Url, scope, fetcher, { Clock.System.now() })

    override suspend fun evaluate(
        evidence: ProximityReaderEvidence,
    ): ProximityCertificateRevocationResult = try {
        require(evidence.certificateChainDerBase64Url.size in 1..MAX_CHAIN_LENGTH)
        val supplied = evidence.certificateChainDerBase64Url.toList().map(::parseCrlCertificate)
        val path = resolvePath(supplied.first(), (supplied + issuers).distinctBy { it.encodedDer })
        val fetched = mutableMapOf<String, ByteString?>()
        val verified = mutableListOf<Pair<X509Certificate, List<CrlCertificateStatus.Good>>>()
        for ((index, pair) in path.pairs.withIndex()) {
            val (certificate, issuer) = pair
            val good = mutableListOf<CrlCertificateStatus.Good>()
            for (url in certificate.crlUrls()) {
                val crl = if (url in fetched) fetched[url] else fetchCrl(url).also { fetched[url] = it }
                if (crl == null) continue
                when (val status = verifier.verify(crl, certificate, issuer, now())) {
                    is CrlCertificateStatus.Good -> good += status
                    is CrlCertificateStatus.Revoked -> return ProximityCertificateRevocationResult.Revoked(
                        if (index == 0) "Reader certificate is revoked" else "Reader certificate authority is revoked",
                    )
                    is CrlCertificateStatus.Indeterminate -> Unit
                }
            }
            verified += issuer to good
        }
        // A previously checked CRL or its issuer may expire while another fetch is suspended.
        val completedAt = now()
        val current = verified.all { (issuer, crls) ->
            completedAt >= issuer.data.validity.notBefore && completedAt <= issuer.data.validity.notAfter &&
                crls.any { it.thisUpdate <= completedAt && completedAt < it.nextUpdate }
        }
        if (path.complete && verified.isNotEmpty() && current) ProximityCertificateRevocationResult.Good
        else indeterminate()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        indeterminate()
    }

    private suspend fun fetchCrl(url: String): ByteString? = try {
        when (val result = fetcher.fetch(url, MAX_CRL_BYTES)) {
            is ProximityCrlFetchResult.Available -> {
                require(result.crlDerBase64Url.length in 1..MAX_CRL_BASE64_LENGTH)
                ByteString(crlBase64.decode(result.crlDerBase64Url)).also { require(it.size <= MAX_CRL_BYTES) }
            }
            ProximityCrlFetchResult.Unavailable -> null
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    private suspend fun resolvePath(leaf: X509Certificate, available: List<X509Certificate>): CrlPath {
        val pairs = mutableListOf<Pair<X509Certificate, X509Certificate>>()
        val seen = mutableSetOf<ByteString>()
        var certificate = leaf
        repeat(MAX_CHAIN_LENGTH) {
            if (!seen.add(certificate.encodedDer)) return CrlPath(pairs, false)
            val candidates = mutableListOf<X509Certificate>()
            for (issuer in available.filter { it.data.subjectDnRaw == certificate.data.issuerDnRaw }) {
                val valid = try {
                    X509CertificateUtil.services.signatureValidator.validateCertificateSignature(
                        X509CertificateUtil.services.cryptoRuntime, issuer.data.subjectPublicKeyInfo, certificate,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    false
                }
                if (valid) candidates += issuer
            }
            // Do not silently choose between different cross-certificates for the same signing key.
            if (candidates.size != 1) return CrlPath(pairs, false)
            val issuer = candidates.single()
            pairs += certificate to issuer
            if (scope == ProximityCrlScope.ReaderCertificate || certificate.encodedDer == issuer.encodedDer) {
                return CrlPath(pairs, true)
            }
            certificate = issuer
        }
        return CrlPath(pairs, false)
    }

    private fun X509Certificate.crlUrls(): List<String> = data.extensionCrlDistributionPoints
        ?.distributionPoints.orEmpty()
        .filter { it.reason == null && it.cRLIssuer == null && it.distributionPointNameRelativeToCrlIssuer == null }
        .flatMap { it.distributionPointFullName.orEmpty() }
        .filter { it.type == GeneralName.NameType.uniformResourceIdentifier }
        .map { it.value }
        .filter(::isCrlHttpUrl)
        .distinct()
        .also { require(it.size <= 8) }

    private fun indeterminate(): ProximityCertificateRevocationResult.Indeterminate =
        ProximityCertificateRevocationResult.Indeterminate("Reader certificate CRL status could not be established")

    private data class CrlPath(val pairs: List<Pair<X509Certificate, X509Certificate>>, val complete: Boolean)

    private companion object {
        const val MAX_CHAIN_LENGTH = 10
        const val MAX_CRL_BYTES = 2_097_152
        const val MAX_CRL_BASE64_LENGTH = (MAX_CRL_BYTES * 4 + 2) / 3
        val crlBase64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun parseCrlCertificate(encoded: String): X509Certificate = try {
            require(encoded.length in 1..87_382)
            X509CertificateUtil.parseCertificateDerEncoded(ByteString(crlBase64.decode(encoded)))
        } catch (error: Throwable) {
            // Some platform ASN.1 parsers throw outside Exception; keep Swift construction recoverable.
            throw IllegalArgumentException("Invalid CRL issuer certificate", error)
        }

        fun isCrlHttpUrl(value: String): Boolean = runCatching {
            require(value.length <= 4096 && (value.startsWith("https://") || value.startsWith("http://")))
            val url = Url(value)
            url.host.isNotEmpty() && url.user.isNullOrEmpty() && url.password.isNullOrEmpty() && url.fragment.isEmpty()
        }.getOrDefault(false)
    }
}
