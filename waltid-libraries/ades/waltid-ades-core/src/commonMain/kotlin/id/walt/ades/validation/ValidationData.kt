package id.walt.ades.validation

import id.walt.ades.asn1.Oids
import id.walt.ades.cms.CmsSignedData
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.transport.AdesTransport
import id.walt.ades.x509.OcspCertId
import id.walt.ades.x509.OcspCertStatus
import id.walt.ades.x509.OcspResponse
import id.walt.ades.x509.Pem
import id.walt.ades.x509.RevocationReason
import id.walt.ades.x509.X509Certificate
import id.walt.ades.x509.X509Crl
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

private val log = KotlinLogging.logger {}

/** Certificates and revocation data, deduplicated, in insertion order. */
class ValidationData(
    certificates: Collection<X509Certificate> = emptyList(),
    crls: Collection<X509Crl> = emptyList(),
    ocsps: Collection<OcspResponse> = emptyList(),
) {
    private val certSet = LinkedHashSet(certificates)
    private val crlSet = LinkedHashSet(crls)
    private val ocspSet = LinkedHashSet(ocsps)

    val certificates: List<X509Certificate> get() = certSet.toList()
    val crls: List<X509Crl> get() = crlSet.toList()
    val ocsps: List<OcspResponse> get() = ocspSet.toList()

    fun add(certificate: X509Certificate) = certSet.add(certificate)
    fun add(crl: X509Crl) = crlSet.add(crl)
    fun add(ocsp: OcspResponse) = ocspSet.add(ocsp)
    fun addAll(other: ValidationData) {
        certSet += other.certSet; crlSet += other.crlSet; ocspSet += other.ocspSet
    }

    fun contains(certificate: X509Certificate) = certificate in certSet
    fun contains(crl: X509Crl) = crl in crlSet
    fun contains(ocsp: OcspResponse) = ocsp in ocspSet

    /** Items present here but not in [existing] (to avoid duplicating validation data, e.g. Table 14 e/f). */
    fun minus(existing: ValidationData) = ValidationData(
        certSet.filterNot { existing.contains(it) }, crlSet.filterNot { existing.contains(it) }, ocspSet.filterNot { existing.contains(it) },
    )

    val isEmpty: Boolean get() = certSet.isEmpty() && crlSet.isEmpty() && ocspSet.isEmpty()

    fun copy() = ValidationData(certSet, crlSet, ocspSet)
}

sealed interface RevocationStatus {
    val source: Any?
    val issuedAt: Instant?

    data class Good(override val source: Any, override val issuedAt: Instant, val nextUpdate: Instant?) : RevocationStatus
    data class Revoked(override val source: Any, override val issuedAt: Instant, val revocationTime: Instant, val reason: RevocationReason) : RevocationStatus
    data class Unknown(val reason: String) : RevocationStatus {
        override val source: Any? = null
        override val issuedAt: Instant? = null
    }
}

/**
 * Resolves issuers and revocation status from a [ValidationData] pool, optionally completing it online
 * (AIA caIssuers, OCSP responders, CRL distribution points). Fetched items are added to the pool, so the pool
 * doubles as the validation material to embed when augmenting to B-LT.
 */
class ValidationDataResolver(
    val pool: ValidationData,
    private val transport: AdesTransport?,
    private val trustAnchors: Collection<X509Certificate> = emptyList(),
    private val fetchOnline: Boolean = transport != null,
) {
    private val failedUrls = HashSet<String>()

    fun isTrustAnchor(cert: X509Certificate): Boolean =
        trustAnchors.any { it == cert || (it.subject.matches(cert.subject) && it.publicKey.encoded.contentEquals(cert.publicKey.encoded)) }

    suspend fun issuersOf(cert: X509Certificate): List<X509Certificate> {
        val local = (pool.certificates + trustAnchors).distinct().filter { it != cert || cert.isSelfIssued }.filter { cert.isIssuedBy(it) }
        if (local.isNotEmpty() || cert.isSelfIssued || !fetchOnline) return local
        for (url in cert.caIssuersUrls.filter { it.startsWith("http") }) {
            val fetched = fetch(url) { transport!!.get(url) } ?: continue
            val certs = runCatching { parseCertificates(fetched) }.getOrElse { emptyList() }
            certs.forEach { pool.add(it) }
            certs.filter { cert.isIssuedBy(it) }.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return emptyList()
    }

    /** All chains from [cert] to a trust anchor (or to a self-signed root when no anchors are configured). */
    suspend fun buildChains(cert: X509Certificate, maxDepth: Int = 10): List<List<X509Certificate>> {
        val result = mutableListOf<List<X509Certificate>>()
        suspend fun walk(chain: List<X509Certificate>) {
            val last = chain.last()
            if (isTrustAnchor(last)) {
                result += chain; return
            }
            if (chain.size >= maxDepth) return
            if (last.isSelfIssued) {
                if (trustAnchors.isEmpty()) result += chain
                return
            }
            for (issuer in issuersOf(last)) if (issuer !in chain) walk(chain + issuer)
        }
        walk(listOf(cert))
        return result
    }

    /** Partial chain up to the last resolvable issuer, used for collecting validation data when no anchor is configured. */
    suspend fun bestEffortChain(cert: X509Certificate): List<X509Certificate> {
        val chain = mutableListOf(cert)
        while (chain.size < 10) {
            val last = chain.last()
            if (last.isSelfIssued || isTrustAnchor(last)) break
            val issuer = issuersOf(last).firstOrNull { it !in chain } ?: break
            chain += issuer
        }
        return chain
    }

    /**
     * Revocation status of [cert] (issued by [issuer]) as known at [time], using the latest-issued applicable
     * data in the pool; fetches online when nothing in the pool is [fresh] for [time].
     */
    suspend fun revocationStatus(
        cert: X509Certificate,
        issuer: X509Certificate,
        time: Instant,
        fresh: (RevocationStatus) -> Boolean = { true },
    ): RevocationStatus {
        val local = localStatuses(cert, issuer)
        local.firstOrNull { it.revokedAtOrBefore(time) }?.let { return it }
        local.firstOrNull(fresh)?.let { return it }
        if (fetchOnline) {
            fetchOcsp(cert, issuer)?.let { if (it !is RevocationStatus.Unknown) return it }
            fetchCrl(cert, issuer)?.let { if (it !is RevocationStatus.Unknown) return it }
        }
        return local.firstOrNull() ?: RevocationStatus.Unknown("no revocation data for ${cert.subject}")
    }

    /** All usable statuses from the pool, latest issued first. */
    suspend fun localStatuses(cert: X509Certificate, issuer: X509Certificate): List<RevocationStatus> {
        val out = mutableListOf<RevocationStatus>()
        for (ocsp in pool.ocsps) ocspStatus(ocsp, cert, issuer)?.let(out::add)
        for (crl in pool.crls) crlStatus(crl, cert, issuer)?.let(out::add)
        return out.sortedByDescending { it.issuedAt }
    }

    private fun RevocationStatus.revokedAtOrBefore(time: Instant) = this is RevocationStatus.Revoked && revocationTime <= time

    suspend fun ocspStatus(ocsp: OcspResponse, cert: X509Certificate, issuer: X509Certificate): RevocationStatus? {
        if (!ocsp.isSuccessful) return null
        val single = runCatching { ocsp.responseFor(cert, issuer) }.getOrNull() ?: return null
        val signer = ocsp.findSigner(pool.certificates + issuer) ?: return null.also { log.debug { "OCSP signer not found for ${cert.subject}" } }
        val authorized = signer == issuer || (signer.isIssuedBy(issuer) && Oids.EKU_OCSP_SIGNING in signer.extendedKeyUsage)
        if (!authorized || !ocsp.verifySignature(signer)) return null
        signer.takeIf { it != issuer }?.let(pool::add)
        return when (val s = single.status) {
            OcspCertStatus.Good -> RevocationStatus.Good(ocsp, single.thisUpdate, single.nextUpdate)
            is OcspCertStatus.Revoked -> RevocationStatus.Revoked(ocsp, single.thisUpdate, s.revocationTime, s.reason)
            OcspCertStatus.Unknown -> null
        }
    }

    suspend fun crlStatus(crl: X509Crl, cert: X509Certificate, issuer: X509Certificate): RevocationStatus? {
        if (!crl.issuer.matches(cert.issuer) || crl.isDelta) return null
        if (!crl.verifySignature(issuer)) return null
        val entry = crl.entryFor(cert)
        return if (entry != null && entry.reason != RevocationReason.REMOVE_FROM_CRL) {
            RevocationStatus.Revoked(crl, crl.thisUpdate, entry.revocationDate, entry.reason)
        } else RevocationStatus.Good(crl, crl.thisUpdate, crl.nextUpdate)
    }

    suspend fun fetchOcsp(cert: X509Certificate, issuer: X509Certificate): RevocationStatus? {
        for (url in cert.ocspResponderUrls.filter { it.startsWith("http") }) {
            for (hash in listOf(AdesDigestAlgorithm.SHA1, AdesDigestAlgorithm.SHA256)) {
                val request = OcspResponse.request(OcspCertId.of(cert, issuer, hash))
                val bytes = fetch("$url#${hash.name}") { transport!!.post(url, "application/ocsp-request", request) } ?: continue
                val response = runCatching { OcspResponse(bytes) }.getOrNull() ?: continue
                val status = ocspStatus(response, cert, issuer) ?: continue
                pool.add(response)
                return status
            }
        }
        return null
    }

    suspend fun fetchCrl(cert: X509Certificate, issuer: X509Certificate): RevocationStatus? {
        for (url in cert.crlDistributionPoints.filter { it.startsWith("http") }) {
            val bytes = fetch(url) { transport!!.get(url) } ?: continue
            val crl = runCatching { X509Crl(bytes) }.getOrNull() ?: continue
            val status = crlStatus(crl, cert, issuer) ?: continue
            pool.add(crl)
            return status
        }
        return null
    }

    private suspend fun fetch(key: String, block: suspend () -> ByteArray): ByteArray? {
        if (key in failedUrls) return null
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log.debug { "Fetching $key failed: ${e.message}" }
            failedUrls += key
            null
        }
    }

    private fun parseCertificates(bytes: ByteArray): List<X509Certificate> = runCatching { Pem.certificates(bytes) }.getOrElse {
        // PKCS#7 certs-only bundle (application/pkcs7-mime)
        CmsSignedData(bytes).certificates
    }
}
