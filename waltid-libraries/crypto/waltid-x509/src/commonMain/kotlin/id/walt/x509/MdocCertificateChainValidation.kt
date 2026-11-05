package id.walt.x509

import at.asitplus.signum.indispensable.pki.X509Certificate
import kotlin.time.Clock
import kotlin.time.Instant

/** Validate the supplied chain; trust anchoring remains the job of the configured IACA/VICAL policies. */
fun validateDocumentSignerCertificateChain(chain: List<CertificateDer>, now: Instant = Clock.System.now(), signed: Instant? = null) {
    require(chain.isNotEmpty()) { "Document signer certificate chain is empty" }
    chain.forEach { PlatformX509Certificate.parse(it).checkValidityAt(now) }
    signed?.let { PlatformX509Certificate.parse(chain.first()).checkValidityAt(it) }
    val constraints = chain.map { X509Certificate.decodeFromDer(it.bytes.toByteArray()).platformCertificateConstraints() }
    val signer = constraints.first()
    require(!signer.isCertificateAuthority && signer.canSignData) { "Invalid document signer certificate usage" }
    require(signer.extendedKeyUsageOids?.contains("1.0.18013.5.1.2") != false) { "Invalid document signer extended key usage" }
    require(constraints.drop(1).all { it.isCertificateAuthority && it.canSignCertificates }) { "Invalid issuer certificate usage" }
    verifyOrderedCertificateChainSignatures(chain)
}
