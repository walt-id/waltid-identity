package id.walt.certificate.x509.validation

import kotlin.time.Instant

data class ValidationResult(
    val valid: Boolean,
    val log: List<ValidationLogEntry>
) {

    val hasWarnings get() = log.any { it.severity == Severity.WARNING }

    val hasErrors get() = log.any { it.severity == Severity.ERROR }

    data class ValidationLogEntry(
        val timestamp: Instant,
        val severity: Severity,
        val validatorId: String,
        val subjectDn: String,
        val message: String
    )

    enum class Severity {
        INFO,
        WARNING,
        ERROR
    }
}