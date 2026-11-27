package id.walt.wallet2.persistence.keys

import kotlinx.serialization.Serializable

/**
 * Text used by operating-system-owned authorization UI.
 *
 * @property reason Reason displayed for the signing authorization request.
 * @property cancelText Cancellation label where platform customization is supported.
 */
@Serializable
public data class KeyUseAuthorizationPrompt(
    public val reason: String = "Please authorize cryptographic signature",
    public val cancelText: String = "Cancel",
) {
    init {
        require(reason.isNotBlank()) { "Authorization reason cannot be blank" }
        require(cancelText.isNotBlank()) { "Authorization cancel text cannot be blank" }
    }
}
