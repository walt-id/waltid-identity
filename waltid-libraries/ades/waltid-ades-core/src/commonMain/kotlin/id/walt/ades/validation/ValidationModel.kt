package id.walt.ades.validation

import id.walt.ades.x509.X509Certificate
import kotlin.time.Instant

/** Main indications of ETSI EN 319 102-1 clause 5.1.3 (TOTAL-* for the overall result, PASSED/FAILED for building blocks). */
enum class Indication(val uri: String) {
    TOTAL_PASSED("urn:etsi:019102:mainindication:total-passed"),
    TOTAL_FAILED("urn:etsi:019102:mainindication:total-failed"),
    INDETERMINATE("urn:etsi:019102:mainindication:indeterminate"),
    PASSED("urn:etsi:019102:mainindication:passed"),
    FAILED("urn:etsi:019102:mainindication:failed"),
}

/** Sub-indications of ETSI EN 319 102-1 Table 6. */
enum class SubIndication {
    FORMAT_FAILURE, HASH_FAILURE, SIG_CRYPTO_FAILURE, REVOKED, EXPIRED, NOT_YET_VALID,
    SIG_CONSTRAINTS_FAILURE, CHAIN_CONSTRAINTS_FAILURE, CERTIFICATE_CHAIN_GENERAL_FAILURE, CRYPTO_CONSTRAINTS_FAILURE,
    POLICY_PROCESSING_ERROR, SIGNATURE_POLICY_NOT_AVAILABLE, TIMESTAMP_ORDER_FAILURE, NO_SIGNING_CERTIFICATE_FOUND,
    NO_CERTIFICATE_CHAIN_FOUND, NO_CERTIFICATE_CHAIN_FOUND_NO_POE, REVOKED_NO_POE, REVOKED_CA_NO_POE,
    OUT_OF_BOUNDS_NOT_REVOKED, OUT_OF_BOUNDS_NO_POE, REVOCATION_OUT_OF_BOUNDS_NO_POE, CRYPTO_CONSTRAINTS_FAILURE_NO_POE,
    NO_POE, TRY_LATER, SIGNED_DATA_NOT_FOUND, CUSTOM;

    val uri: String get() = "urn:etsi:019102:subindication:$name"
}

/** AdES baseline levels shared by CAdES/XAdES/JAdES/PAdES/CB-AdES. */
enum class AdesLevel(val code: String) {
    B_B("B-B"), B_T("B-T"), B_LT("B-LT"), B_LTA("B-LTA");

    operator fun compareTo(other: AdesLevel?): Int = if (other == null) 1 else ordinal - other.ordinal
}

enum class CheckStatus { PASSED, FAILED, WARNING, INFO }

/** A single, traceable check performed during validation (format, crypto, X.509, time-stamps, ...). */
data class ValidationCheck(
    val id: String,
    val status: CheckStatus,
    val message: String,
    /** Clause reference such as "TS 119 152-1 5.2.2" or "EN 319 102-1 5.2.6". */
    val reference: String? = null,
)

data class TimestampValidationResult(
    val type: TimestampType,
    val indication: Indication,
    val subIndication: SubIndication?,
    val genTime: Instant?,
    val tsaCertificate: X509Certificate?,
    val problems: List<String>,
) {
    val passed: Boolean get() = indication == Indication.PASSED
}

enum class TimestampType { CONTENT, SIGNATURE, VALIDATION_DATA_REFS, ARCHIVE }

data class SignatureValidationResult(
    val signatureId: String,
    val indication: Indication,
    val subIndication: SubIndication?,
    val signingCertificate: X509Certificate?,
    val certificateChain: List<X509Certificate>,
    val claimedSigningTime: Instant?,
    val bestSignatureTime: Instant,
    val validationTime: Instant,
    val level: AdesLevel?,
    val timestamps: List<TimestampValidationResult>,
    val checks: List<ValidationCheck>,
) {
    val isValid: Boolean get() = indication == Indication.TOTAL_PASSED
    val errors: List<ValidationCheck> get() = checks.filter { it.status == CheckStatus.FAILED }
    val warnings: List<ValidationCheck> get() = checks.filter { it.status == CheckStatus.WARNING }
}

/** Result for a whole signed document: one entry per signature (COSE_Sign signers, counter signatures, ...). */
data class DocumentValidationResult(
    val format: String,
    val signatures: List<SignatureValidationResult>,
    val documentChecks: List<ValidationCheck> = emptyList(),
) {
    /** Worst indication across signatures (FAILED < INDETERMINATE < PASSED). */
    val indication: Indication
        get() = when {
            documentChecks.any { it.status == CheckStatus.FAILED } -> Indication.TOTAL_FAILED
            signatures.isEmpty() -> Indication.INDETERMINATE
            signatures.any { it.indication == Indication.TOTAL_FAILED } -> Indication.TOTAL_FAILED
            signatures.any { it.indication == Indication.INDETERMINATE } -> Indication.INDETERMINATE
            else -> Indication.TOTAL_PASSED
        }

    val subIndication: SubIndication?
        get() = when {
            documentChecks.any { it.status == CheckStatus.FAILED } -> SubIndication.FORMAT_FAILURE
            else -> signatures.firstOrNull { it.indication == indication }?.subIndication
        }

    val isValid: Boolean get() = indication == Indication.TOTAL_PASSED
}
