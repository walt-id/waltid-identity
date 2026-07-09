package id.walt.certificate.x509.validation

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.truststore.CompositeTrustStore
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import kotlin.time.Clock

class ValidationContext(trustStore: X509CertificateTrustStore) : X509CertificateTrustStore {

    private val internalLog = mutableListOf<ValidationResult.ValidationLogEntry>()
    private val internalChainTrust = InMemoryTrustStore()
    private val internalTrustStore = CompositeTrustStore(
        listOf(
            trustStore,
            internalChainTrust
        )
    )

    val valid: Boolean
        get() =
            !internalLog.any { it.severity == ValidationResult.Severity.ERROR }

    val log: List<ValidationResult.ValidationLogEntry>
        get() = internalLog.toList()

    val validatorId: String
        get() = current.validatorId
    val certificateIndex: Int
        get() = current.certificateIndex
    val certificateSubjectDn: String
        get() = current.certificateSubjectDn

    override suspend fun findCertificateBySubjectDn(subjectDn: String): List<X509Certificate> =
        internalTrustStore.findCertificateBySubjectDn(subjectDn)

    fun addTrustedCertificate(certificate: X509Certificate) {
        internalChainTrust.addCertificate(certificate)
    }

    fun addLogEntry(severity: ValidationResult.Severity, message: String) {
        val now = Clock.System.now()
        internalLog.add(
            ValidationResult.ValidationLogEntry(
                timestamp = now,
                severity = severity,
                validatorId = validatorId,
                subjectDn = certificateSubjectDn,
                message = message
            )
        )
    }

    private val current: Current
        get() {
            checkNotNull(internalCurrent) { "Current validator state is not set" }
            return internalCurrent!!
        }

    private var internalCurrent: Current? = null

    fun setCurrent(
        validatorId: String,
        certificateIndex: Int,
        certificateSubjectDn: String
    ) {
        internalCurrent = Current(validatorId, certificateIndex, certificateSubjectDn)
    }

    private data class Current(
        val validatorId: String,
        val certificateIndex: Int,
        val certificateSubjectDn: String,
    )
}