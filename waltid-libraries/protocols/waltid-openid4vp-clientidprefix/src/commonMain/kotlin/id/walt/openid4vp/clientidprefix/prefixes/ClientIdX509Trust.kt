package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.X509CertificateChain
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.trust.decision.TrustCheck
import id.walt.trust.decision.TrustDecisionRequest
import id.walt.trust.decision.TrustPurpose
import id.walt.trust.decision.TrustRequirement
import id.walt.trust.decision.TrustSubject
import id.walt.trust.decision.check
import id.walt.trust.model.TrustDecisionCode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.io.encoding.Base64
import kotlin.time.Clock

/**
 * The X.509 trust step of `x509_hash` and `x509_san_dns`, in two parts so that a registry is asked last.
 *
 * With static anchors, [validateX509Chain] runs PKIX against them and [authorizeX509Chain] does nothing.
 * With a trust registry, any static anchors are ignored: [validateX509Chain] checks what the wallet can check
 * without anchors, and [authorizeX509Chain] asks the registry. Call it after the request signature and the client
 * id binding have been checked, so a forged or unbound request never reaches the registry.
 */
internal object ClientIdX509Trust {

    private val log = KotlinLogging.logger { }

    suspend fun ClientIdTrustConfiguration.validateX509Chain(
        certificates: List<X509Certificate>,
    ): ClientValidationResult.Failure? {
        if (x509TrustRegistry == null) {
            return ClientIdCrypto2.validateCertificateChain(certificates, x509TrustStore)
        }
        val violations = localChainViolations(certificates)
        if (violations.isEmpty()) return null
        return ClientValidationResult.Failure(ClientIdError.AttestationError(violations.joinToString("\n")))
    }

    /**
     * The registry owns the anchors and the path validation. The wallet still requires one chain in leaf-first
     * order, so that a resolver matching any presented certificate cannot be satisfied by an unrelated trusted
     * certificate appended to `x5c`, plus every certificate within its validity period and a leaf that may sign
     * a verifier's request.
     */
    private fun localChainViolations(certificates: List<X509Certificate>): List<String> = buildList {
        val chainProblem = try {
            if (X509CertificateChain.of(certificates).isProvidedLeafFirst) null
            else "the certificates are not in leaf-first order, or one is repeated"
        } catch (e: IllegalArgumentException) {
            e.message ?: "the certificates do not form one chain"
        }
        chainProblem?.let { add("x5c must contain one leaf-first certificate chain: $it") }

        val now = Clock.System.now()
        certificates.forEach { certificate ->
            val validity = certificate.data.validity
            if (now < validity.notBefore || now > validity.notAfter) {
                add("'${certificate.data.subjectDn}': Certificate is not within its validity period")
            }
        }

        val leaf = certificates.first()
        AttestationX509CertificateValidator.leafProfileViolations(leaf).forEach { add("'${leaf.data.subjectDn}': $it") }
    }

    /** Exceptions from the evaluator propagate: an infrastructure failure is not a trust decision. */
    suspend fun ClientIdTrustConfiguration.authorizeX509Chain(
        certificates: List<X509Certificate>,
    ): ClientValidationResult.Failure? {
        val registry = x509TrustRegistry ?: return null
        val request = TrustDecisionRequest(
            purpose = TrustPurpose.WALLET_PRESENTATION_AUTH_REQUEST,
            subject = TrustSubject.CertificateChain(certificates.map { Base64.encode(it.encodedDer.toByteArray()) }),
            expectedEntityTypes = registry.expectedEntityTypes,
        )
        return when (val check = registry.evaluator.check(request, TrustRequirement.RegistryFresh(registry.requireAuthenticatedSource))) {
            is TrustCheck.Accepted -> null
            is TrustCheck.Rejected -> {
                if (check.decision?.decision == TrustDecisionCode.UNKNOWN) {
                    log.warn { "Request object trust source is unavailable: ${check.decision?.warnings}" }
                    ClientValidationResult.Failure(ClientIdError.X509TrustSourceUnavailable)
                } else {
                    log.info { "Request object signer is not trusted: ${check.reason}" }
                    ClientValidationResult.Failure(ClientIdError.X509TrustAnchorMismatch)
                }
            }

            TrustCheck.Skipped -> error("A registry trust requirement is never skipped")
        }
    }
}
