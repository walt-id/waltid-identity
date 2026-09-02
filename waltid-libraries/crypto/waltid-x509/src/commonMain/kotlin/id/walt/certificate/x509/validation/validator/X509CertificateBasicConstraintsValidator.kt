package id.walt.certificate.x509.validation.validator

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.BasicConstraintsExtension
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.validation.ValidationContext
import id.walt.certificate.x509.validation.ValidationResult

/**
 * Validation Rules basic constraints extension:
 *
 * CA Flag Check: Verify that every certificate in the chain except the final end-entity/leaf
 * certificate explicitly sets cA:TRUE.
 *
 * Path Length Constraint: Decrement the pathLenConstraint integer during chain traversal to
 * ensure the remaining number of sub-CAs does not exceed the allowed maximum limit.
 *
 * Criticality Verification: Ensure CA certificates mark the basic constraints extension as
 * critical according to baseline public key infrastructure profile standards (Severity is WARNING and
 * not ERROR because criticality is not mentioned in RFC 5280 section 6.1.4).
 *
 * Leaf Rejection: Confirm that an end-user or server leaf certificate does not
 * feature cA:TRUE. (this can be
 * disabled by setting leafCanBeCa to true)
 */
class X509CertificateBasicConstraintsValidator(val leafCanBeCa: Boolean = false) : X509CertificateValidator {


    override val id: String = ID

    override suspend fun validate(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val basicConstraints = x509Certificate.data.extensionBasicConstraints
        if (basicConstraints == null) {
            if (!context.isLeaf) {
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "Certificate extension '${BasicConstraintsExtension.OID}' ('${BasicConstraintsExtension.NAME}') is not present"
                )
            }
        } else {
            if (!basicConstraints.critical) {
                context.addLogEntry(
                    ValidationResult.Severity.WARNING,
                    "Certificate extension '${BasicConstraintsExtension.OID}' ('${BasicConstraintsExtension.NAME}') must have critical flag set"
                )
            }
            if (context.isLeaf) {
                if (!leafCanBeCa && basicConstraints.cA)
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "Certificate with subject '${x509Certificate.data.subjectDn}' must not have cA flag set because it is a leaf certificate"
                    )
            } else {
                if (!basicConstraints.cA) {
                    context.addLogEntry(
                        ValidationResult.Severity.ERROR,
                        "Certificate with subject '${x509Certificate.data.subjectDn}' must have cA flag set because it is not a leaf certificate"
                    )
                }
                checkPathLengthConstraintForParentCertificatesInChain(context, x509Certificate)
                if (basicConstraints.pathLenConstraint != null) {
                    val currentPathLengthConstraints: MutableMap<String, Int> =
                        context.getVariable(PATH_LENGTH_CONSTRAINT_VALUE_NAME) ?: mutableMapOf()
                    if (!currentPathLengthConstraints.containsKey(x509Certificate.data.subjectDn)) {
                        context.setVariable(
                            PATH_LENGTH_CONSTRAINT_VALUE_NAME,
                            updatePathLengthConstraints(currentPathLengthConstraints, x509Certificate)
                        )
                    }
                }
            }
        }
    }

    private fun checkPathLengthConstraintForParentCertificatesInChain(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ) {
        val currentConstraints = context.getVariable<MutableMap<String, Int>>(PATH_LENGTH_CONSTRAINT_VALUE_NAME)
            ?: pathLengthConstraintsFromTrustStore(context, x509Certificate)
        val newConstraints = updatePathLengthConstraints(currentConstraints, x509Certificate)
        context.setVariable(PATH_LENGTH_CONSTRAINT_VALUE_NAME, newConstraints)
        newConstraints.filter { it.value < 0 }
            .forEach { (subjectDn, _) ->
                context.addLogEntry(
                    ValidationResult.Severity.ERROR,
                    "Certificate with subjectDn '$subjectDn' has exceeded path length constraint"
                )
            }
    }

    private fun pathLengthConstraintsFromTrustStore(
        context: ValidationContext,
        x509Certificate: X509Certificate
    ): MutableMap<String, Int> {
        var currentCert = x509Certificate
        val trustedChain = mutableListOf<X509Certificate>()
        while (currentCert.data.subjectDn != currentCert.data.issuerDn) {
            val potentialIssuers = context.findCertificateBySubjectDn(currentCert.data.issuerDn)
            if (potentialIssuers.isEmpty()) {
                break
            }
            require(potentialIssuers.size == 1) { "Selecting issuer Certificate from multiple potential parents is not implemented" }
            currentCert = potentialIssuers.first()
            trustedChain.add(currentCert)
        }
        var pathLengthConstraints = mutableMapOf<String, Int>()
        trustedChain.reversed().forEach {
            pathLengthConstraints = updatePathLengthConstraints(pathLengthConstraints, it)
        }
        return pathLengthConstraints
    }

    private fun updatePathLengthConstraints(
        currentConstraints: MutableMap<String, Int>,
        certificate: X509Certificate
    ): MutableMap<String, Int> {
        // 1. decrement pathLenConstraint for all certificates in the chain
        val updated = currentConstraints.mapValues { it.value - 1 }
            .toMutableMap()
        // 2. add pathLenConstraint for current certificate if it is not null
        certificate.data.extensionBasicConstraints?.pathLenConstraint?.also {
            check(!updated.containsKey(certificate.data.subjectDn)) { "It seems certificate with subjectDn '${certificate.data.subjectDn}' is twice in chain" }
            updated[certificate.data.subjectDn] = it
        }
        return updated
    }

    companion object {
        const val ID = "basicConstraints"
        private const val PATH_LENGTH_CONSTRAINT_VALUE_NAME = "pathLength"
    }
}