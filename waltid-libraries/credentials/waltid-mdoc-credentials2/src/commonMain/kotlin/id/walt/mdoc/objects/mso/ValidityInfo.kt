package id.walt.mdoc.objects.mso

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ValueTags
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
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
@OptIn(ExperimentalUnsignedTypes::class, ExperimentalTime::class, ExperimentalSerializationApi::class)
@Serializable
data class ValidityInfo(
    @SerialName("signed")
    @ValueTags(0u) // CBOR tag 0 for standard date-time string (tdate)
    val signed: Instant,

    @SerialName("validFrom")
    @ValueTags(0u)
    val validFrom: Instant,

    @SerialName("validUntil")
    @ValueTags(0u)
    val validUntil: Instant,

    @SerialName("expectedUpdate")
    @ValueTags(0u)
    val expectedUpdate: Instant? = null
) {
    fun validate() {
        val now = Clock.System.now()
        require(validFrom <= now) { "MSO is not yet valid" }
        require(validUntil >= now) { "MSO is no longer valid" }
    }
}
