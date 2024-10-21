package id.walt.ktorauthnz.sessions

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.methods
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.config.AuthMethodConfiguration
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.reflect.jvm.jvmName

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
    var flows: Set<AuthFlow>? = null,

    /**
     * Account ID, if known yet
     * (could potentially only be determined when actually invoking auth method)
     */
    var accountId: String? = null,

    var token: String? = null,
) {
    fun toInformation() = AuthSessionInformation(id, status, flows?.methods(), token)
    suspend fun progressFlow(method: AuthenticationMethod) {
        check(flows!!.any { it.method == method.id }) { "Trying to progress flow with wrong authentication method. Allowed methods: ${flows!!.methods()}, tried method: ${method.id}" }

        val currentFlow = flows!!.first { it.method == method.id }

        if (currentFlow.continueWith != null) {
            flows = currentFlow.continueWith
            status = AuthSessionStatus.CONTINUE_NEXT_STEP
        } else if (currentFlow.ok) {
            flows = null
            status = AuthSessionStatus.OK

            token = KtorAuthnzManager.tokenHandler.generateToken(this)
        } else {
            error("Cannot process flow: No next step defined but current step is not end step!")
        }

        SessionManager.updateSession(this)
    }

    suspend fun logout() {
        check(token != null) { "Cannot logout, as no token yet exists (session is not even authenticated yet). You can drop the session without invoking the logout procedure." }

        KtorAuthnzManager.tokenHandler.dropToken(token!!)
        SessionManager.removeSession(this)
    }

    inline fun <reified V : AuthMethodConfiguration> lookupConfiguration(method: AuthenticationMethod): V {
        val flow = flows?.firstOrNull { it.method == method.id } ?: error("No flow for method: ${method.id}")
        val config: V =
            flow.config?.let {
                runCatching { Json.decodeFromJsonElement<V>(flow.config) }.getOrElse {
                    throw IllegalArgumentException(
                        "Invalid config provided at auth method ${method.id}, required would have been: ${V::class.jvmName}",
                        it
                    )
                }
            } ?: error("Authentication method ${method.id} requested config, but none is provided by authentication flow.")

        return config
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
    val nextStep: List<String>? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val token: String? = null,
)
