package id.walt.openid4vci.pkce

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Persisted with the authorization code. A missing binding denotes an unsupported older record. */
@Serializable
sealed interface PkceBinding {
    @Serializable
    @SerialName("none")
    data object NotUsed : PkceBinding

    @Serializable
    @SerialName("challenge")
    data class Challenge(val codeChallenge: String, val codeChallengeMethod: String) : PkceBinding
}
