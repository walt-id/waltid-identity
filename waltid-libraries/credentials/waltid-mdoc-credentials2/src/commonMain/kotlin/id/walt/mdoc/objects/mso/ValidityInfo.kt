package id.walt.mdoc.objects.mso

import id.walt.mdoc.encoding.MdocTDateInstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Defines the temporal validity of the Mobile Security Object (MSO) and its signature.
 *
 * This structure is critical for a verifier to determine if an mdoc is current and not expired,
 * even in an offline context.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 9.1.2.4 (Signing method and structure for MSO)
 *
 * @property signed The timestamp at which the MSO signature was created by the issuing authority. A verifier
 * must check that this time is within the validity period of the MSO's signing certificate.
 * @property validFrom The timestamp on or after which the MSO is considered valid. This must be equal to or later
 * than the `signed` timestamp.
 * @property validUntil The timestamp after which the MSO is no longer considered valid and should be rejected by a verifier.
 * This value must be later than `validFrom` and should be on or before the expiration date of the signing
 * certificate.
 * @property expectedUpdate An optional timestamp indicating when the issuing authority expects to re-sign the MSO,
 * potentially with updated data elements. This can serve as a hint for the mdoc application to
 * seek an update.
 *
 * @note Privacy Consideration: To prevent timestamps from becoming a tracking vector, the specification
 * recommends that issuing authorities reduce their precision (e.g., by setting hour/minute/second values
 * consistently across all provisioned mdocs).
 */
@OptIn(ExperimentalUnsignedTypes::class)
@Serializable
data class ValidityInfo(
    @SerialName("signed")
    @Serializable(with = MdocTDateInstantSerializer::class)
    val signed: Instant,

    @SerialName("validFrom")
    @Serializable(with = MdocTDateInstantSerializer::class)
    val validFrom: Instant,

    @SerialName("validUntil")
    @Serializable(with = MdocTDateInstantSerializer::class)
    val validUntil: Instant,

    @SerialName("expectedUpdate")
    @Serializable(with = MdocTDateInstantSerializer::class)
    val expectedUpdate: Instant? = null
) {
    /**
     * Inspection-time check: the MSO is currently valid. A future-valid structure can still be issued;
     * presentation before [validFrom] fails here, not in [precheck].
     */
    fun validate() {
        val now = Clock.System.now()
        require(validFrom <= now) { "MSO is not yet valid (becomes valid in ${validFrom - now})" }
        require(validUntil >= now) { "MSO is no longer valid (expired ${now - validFrom} ago)" }
    }

    /**
     * ISO/IEC 18013-5:2021 §9.1.2.4 structural check: `signed <= validFrom < validUntil`.
     * [expectedUpdate] is optional and is not bounded by this ISO rule; use
     * [requireExpectedUpdateWithinWindow] for the opt-in issuer policy.
     */
    fun precheck() {
        require(signed <= validFrom) { "signed must be at or before validFrom" }
        require(validFrom < validUntil) { "validUntil must be after validFrom" }
    }

    /**
     * Opt-in issuer policy, off by default. ISO does not require [expectedUpdate] to lie in the
     * validity window; enable this only when the product should reject an earlier planned refresh.
     */
    fun requireExpectedUpdateWithinWindow() {
        val update = expectedUpdate ?: return
        require(update >= validFrom) { "expectedUpdate cannot be before validFrom" }
        require(update <= validUntil) { "expectedUpdate cannot be after validUntil" }
    }
}
