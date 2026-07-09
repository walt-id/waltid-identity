package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class X509CertificateValidityValidator : X509CertificateValidator {

    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val certificateValidity = x509Certificate.data.validity

        if ((certificateValidity.notBefore - certificateValidity.notAfter).isPositive()) {
            context.addLogEntry(ValidationResult.Severity.ERROR, "Illegal certificate validity")
        } else {
            val now = Clock.System.now()
            if ((now - certificateValidity.notBefore).isNegative()) {
                context.addLogEntry(ValidationResult.Severity.ERROR, "Certificate is not yet valid")
            } else if ((now - certificateValidity.notAfter).isPositive()) {
                context.addLogEntry(ValidationResult.Severity.ERROR, "Certificate is expired")
            } else {
                if ((certificateValidity.notAfter - now) < 30.days) {
                    context.addLogEntry(ValidationResult.Severity.WARNING, "Certificate will expire soon")
                } else {
                    context.addLogEntry(ValidationResult.Severity.INFO, "OK")
                }
            }
        }
    }

    companion object {
        const val ID = "validityPeriod"
    }
}