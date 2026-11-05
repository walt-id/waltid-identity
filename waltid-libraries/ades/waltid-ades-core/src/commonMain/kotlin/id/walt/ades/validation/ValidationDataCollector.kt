package id.walt.ades.validation

import id.walt.ades.transport.AdesTransport
import id.walt.ades.x509.OcspResponse
import id.walt.ades.x509.X509Certificate
import id.walt.ades.x509.X509Crl
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Collects the certificates and revocation data required to validate a set of certificates (signing certificate,
 * counter-signers, TSA certificates of previous time-stamps, ...), including the material needed for OCSP
 * responders and CRL issuers. Used for augmentation to B-LT / before each new archive time-stamp.
 */
class ValidationDataCollector(
    private val transport: AdesTransport?,
    private val trustAnchors: List<X509Certificate> = emptyList(),
    /** Embedded revocation data issued within this window is reused instead of being fetched again. */
    private val reuseWindow: Duration = 15.minutes,
    /** Include the self-signed root / trust anchor certificate in the collected certificates. */
    private val includeRoot: Boolean = true,
) {
    class Result(val required: ValidationData, val missing: List<String>)

    suspend fun collect(targets: Collection<X509Certificate>, available: ValidationData): Result {
        val pool = available.copy()
        val resolver = ValidationDataResolver(pool, transport, trustAnchors, fetchOnline = transport != null)
        val required = ValidationData()
        val missing = mutableListOf<String>()
        val queue = ArrayDeque(targets.distinct())
        val visited = HashSet<X509Certificate>()
        val now = Clock.System.now()

        while (queue.isNotEmpty()) {
            val cert = queue.removeFirst()
            if (!visited.add(cert)) continue
            val chain = resolver.buildChains(cert).firstOrNull() ?: resolver.bestEffortChain(cert)
            chain.forEach { c ->
                val isRoot = c.isSelfIssued || resolver.isTrustAnchor(c)
                if (!isRoot || includeRoot) required.add(c)
                pool.add(c)
            }
            if (!chain.last().isSelfIssued && !resolver.isTrustAnchor(chain.last())) missing += "issuer of ${chain.last().subject} not found"

            for (i in 0 until chain.size - 1) {
                val c = chain[i]
                if (c in visited && i > 0) continue
                if (c.hasOcspNoCheck) continue
                val status = resolver.revocationStatus(c, chain[i + 1], now, fresh = { it.issuedAt?.let { t -> t >= now - reuseWindow } ?: false })
                when (val source = status.source) {
                    is OcspResponse -> {
                        required.add(source)
                        source.findSigner(pool.certificates + chain[i + 1])?.let { signer ->
                            if (signer != chain[i + 1]) {
                                required.add(signer); queue.addLast(signer)
                            }
                        }
                        source.certificates.forEach(pool::add)
                    }

                    is X509Crl -> required.add(source)
                    else -> missing += "no revocation data for ${c.subject}"
                }
                if (i > 0) visited += c
                queue.addLast(chain[i + 1])
            }
        }
        return Result(required, missing.distinct())
    }
}
