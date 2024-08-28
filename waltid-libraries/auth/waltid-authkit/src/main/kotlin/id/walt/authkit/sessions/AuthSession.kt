package id.walt.authkit.sessions

import id.walt.authkit.flows.AuthFlow
import id.walt.authkit.methods.AuthenticationMethod
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class AuthSessionStatus(val value: String) {
    INIT("init"),

    OK("ok"),
    CONTINUE_NEXT_STEP("continue_next_step"),
    FAIL("fail"),
}

@Serializable
data class AuthSession(
    @SerialName("session_id")
    val id: String,

    var status: AuthSessionStatus = AuthSessionStatus.INIT,
    var flow: AuthFlow? = null,

    /**
     * Account ID, if known yet
     * (could potentially only be determined when actually invoking auth method)
     */
    var accountId: String? = null,

    var token: String? = null,
) {
    fun toInformation() = AuthSessionInformation(id, status, flow?.method, token)
    suspend fun progressFlow(method: AuthenticationMethod) {
        check(flow!!.method == method.id) { "Trying to progress flow with wrong authentication method" }

        if (flow!!.continueWith != null) {
            flow = flow!!.continueWith
            status = AuthSessionStatus.CONTINUE_NEXT_STEP
        } else if (flow!!.ok) {
            flow = null
            status = AuthSessionStatus.OK

            token = TokenManager.supplyNewToken(this)
        } else {
            throw IllegalStateException("Cannot process flow: No next step defined but current step is not end step!")
        }

        SessionManager.updateSession(this)
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AuthSessionInformation(
    @SerialName("session_id")
    val id: String,

    val status: AuthSessionStatus = AuthSessionStatus.INIT,

    @SerialName("next_step")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextStep: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val token: String? = null,
)
