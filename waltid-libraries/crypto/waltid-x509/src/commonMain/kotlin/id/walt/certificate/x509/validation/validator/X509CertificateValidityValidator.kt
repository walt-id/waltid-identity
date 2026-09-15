package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * @param clock the source of "now" this validator judges validity against. Defaults to the system clock;
 *   pass a fixed clock to decide validity as of a chosen instant, which is what tests holding a captured
 *   real-world certificate need - otherwise they start failing on the day that certificate expires.
 */
class X509CertificateValidityValidator(
    private val allowValidityInFuture: Boolean = false,
    private val clock: Clock
) : X509CertificateValidator {

    override val id: String = ID

    constructor(
        allowValidityInFuture: Boolean = false
    ) : this(allowValidityInFuture = allowValidityInFuture, clock = Clock.System)

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val certificateValidity = x509Certificate.data.validity
        if ((certificateValidity.notBefore - certificateValidity.notAfter).isPositive()) {
            context.addLogEntry(ValidationResult.Severity.ERROR, "Illegal certificate validity")
        } else {
            val now = clock.now()
            if ((now - certificateValidity.notBefore).isNegative()) {
                if (allowValidityInFuture) {
                    context.addLogEntry(ValidationResult.Severity.WARNING, "Certificate is not yet valid")
                } else {
                    context.addLogEntry(ValidationResult.Severity.ERROR, "Certificate is not yet valid")
                }
            } else if ((now - certificateValidity.notAfter).isPositive()) {
                context.addLogEntry(ValidationResult.Severity.ERROR, "Certificate is expired")
            } else {
                if ((certificateValidity.notAfter - now) < 30.days) {
                    context.addLogEntry(ValidationResult.Severity.WARNING, "Certificate will expire soon")
                }
            }
        }
    }

    companion object {
        const val ID = "validityPeriod"
    }
}