package id.walt.mdoc.proximity

import org.kotlincrypto.hash.sha2.SHA256

/**
 * One wallet-validated value that is safe to show during holder consent.
 *
 * @property id Stable identifier within its enclosing authorization.
 * @property label Display-safe label.
 * @property value Display-safe value.
 */
data class MdocApplicationAuthorizationDetail(
    val id: String,
    val label: String,
    val value: String,
) {
    init {
        require(id.isNotBlank()) { "Application authorization detail id must not be blank" }
        require(label.isNotBlank()) { "Application authorization detail label must not be blank" }
        require(value.isNotBlank()) { "Application authorization detail value must not be blank" }
    }
}

/**
 * Profile-neutral application authorization prepared by a wallet-owned profile implementation.
 *
 * The proximity engine neither parses application extensions nor assigns application meaning. It
 * carries these already validated, display-safe values through consent and binds them to the opaque
 * profile result used to construct the response.
 *
 * @property profileId Stable, versioned identifier of the wallet application profile.
 * @property displayTitle Display-safe heading for the authorization details.
 * @property details Ordered, display-safe values with identifiers unique within this authorization.
 * @property resultBindingDigest SHA-256 digest over the validated profile result and its selected
 * device-signed response mapping.
 */
class MdocApplicationAuthorization(
    val profileId: String,
    val displayTitle: String,
    details: List<MdocApplicationAuthorizationDetail>,
    val resultBindingDigest: ImmutableBytes,
) {
    /** Ordered, display-safe values supplied by the wallet application profile. */
    val details: List<MdocApplicationAuthorizationDetail> = details.toList()

    init {
        require(profileId.isNotBlank()) { "Application profile id must not be blank" }
        require(displayTitle.isNotBlank()) { "Application authorization title must not be blank" }
        require(this.details.isNotEmpty()) { "Application authorization details must not be empty" }
        require(this.details.distinctBy(MdocApplicationAuthorizationDetail::id).size == this.details.size) {
            "Application authorization detail ids must be unique"
        }
        require(resultBindingDigest.size == SHA256_BYTES) {
            "Application result binding must be a SHA-256 digest"
        }
    }

    internal fun consentBindingDigest(): ImmutableBytes {
        val material = buildList {
            add(PROFILE_BINDING_DOMAIN.encodeToByteArray())
            add(profileId.encodeToByteArray())
            add(displayTitle.encodeToByteArray())
            add(resultBindingDigest.copy())
            details.forEach { detail ->
                add(detail.id.encodeToByteArray())
                add(detail.label.encodeToByteArray())
                add(detail.value.encodeToByteArray())
            }
        }.fold(bindingIntBytes(details.size)) { bytes, value -> bytes + bindingLengthPrefixed(value) }
        return ImmutableBytes.of(SHA256().digest(material))
    }

    private companion object {
        const val SHA256_BYTES = 32
        const val PROFILE_BINDING_DOMAIN = "walt.id/mdoc-application-authorization/v1"
    }
}

internal fun bindingIntBytes(value: Int): ByteArray = byteArrayOf(
    (value ushr 24).toByte(),
    (value ushr 16).toByte(),
    (value ushr 8).toByte(),
    value.toByte(),
)

internal fun bindingLengthPrefixed(value: ByteArray): ByteArray = bindingIntBytes(value.size) + value
