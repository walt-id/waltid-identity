@file:OptIn(ExperimentalTime::class)

package id.walt.ktorauthnz.sessions

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.methods
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.config.AuthMethodConfiguration
import id.walt.ktorauthnz.methods.sessiondata.SessionData
import io.klogging.logger
import io.ktor.server.routing.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.reflect.jvm.jvmName
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class AuthSessionStatus(val value: String) {
    INIT("init"),

    @Deprecated("Use `success` instead of `ok`")
    OK("ok"),
    SUCCESS("success"),

    CONTINUE_NEXT_FLOW("continue_next_flow"),
    CONTINUE_NEXT_STEP("continue_next_step"),

    FAILURE("failure");

    @Suppress("DEPRECATION") // Allow check for deprecated `ok`
    fun isSuccess() = this == SUCCESS || this == OK
}

@Serializable
data class AuthSession(
    @SerialName("session_id")
    val id: String,

    var status: AuthSessionStatus = AuthSessionStatus.INIT,
    var flows: Set<AuthFlow>? = null,

    var currentlyActiveMethod: String? = null,
    var nextStepInformation: AuthSessionNextStep? = null,

    /**
     * Account ID, if known yet
     * (could potentially only be determined when actually invoking auth method)
     */
    var accountId: String? = null,

    var token: String? = null,

    var expiration: Instant? = null,

    var sessionData: MutableMap<String, SessionData>? = null
) {
    companion object {
        private val log = logger("AuthSession")
    }

    fun toInformation(
        revealTokenToClient: Boolean = true,
        /** optional: you can enter a human-readable description for the next step action */
        nextStepDescription: String? = null
    ) = AuthSessionInformation(
        id = id,
        status = status,
        currentlyActiveMethod = currentlyActiveMethod,
        nextMethod = flows?.methods(),
        nextStep = nextStepInformation,
        nextStepDescription = nextStepDescription,
        nextStepInformationalMessage = if (nextStepDescription == null) null else
            "The \"$currentlyActiveMethod\" authentication method requires multiple-steps for authentication. " +
                    "Follow the steps in `next_step` (${nextStepInformation?.let { it::class.simpleName ?: "" }}) " +
                    "to complete the authentication method.",
        token = if (revealTokenToClient) token else null,
        expiration = expiration
    )

    suspend fun progressFlow(method: AuthenticationMethod) {
        check(flows!!.any { it.method == method.id }) { "Trying to progress flow with wrong authentication method. Allowed methods: ${flows!!.methods()}, tried method: ${method.id}" }

        setStepInformation(method, null)
        val currentFlow = flows!!.first { it.method == method.id }

        if (currentFlow.continueWith != null) {
            flows = currentFlow.continueWith
            status = AuthSessionStatus.CONTINUE_NEXT_FLOW
        } else if (currentFlow.isEndConditionSuccess()) {
            flows = null
            status = AuthSessionStatus.SUCCESS

            token = KtorAuthnzManager.tokenHandler.generateToken(this)
        } else {
            error("Cannot process flow: No next step defined but current step is not end step!")
        }

        SessionManager.updateSession(this)
    }

    fun setStepInformation(method: AuthenticationMethod, nextStepInfo: AuthSessionNextStep?) {
        currentlyActiveMethod = method.id
        nextStepInformation = nextStepInfo
    }

    suspend fun progressStep(method: AuthenticationMethod, nextStepInfo: AuthSessionNextStep) {
        check(flows!!.any { it.method == method.id }) { "Trying to progress flow step with wrong authentication method. Allowed methods: ${flows!!.methods()}, tried method: ${method.id}" }

        //val currentFlow = flows!!.first { it.method == method.id }
        setStepInformation(method, nextStepInfo)
        status = AuthSessionStatus.CONTINUE_NEXT_STEP

        SessionManager.updateSession(this)
    }

    suspend fun logout() {
        log.trace { "Requested logging out of AuthSession: $id" }
        check(token != null) { "Cannot logout, as no token yet exists (session is not even authenticated yet). You can drop the session without invoking the logout procedure." }

        KtorAuthnzManager.tokenHandler.dropToken(token!!)
        SessionManager.invalidateSession(this)
    }

    suspend fun RoutingCall.logoutAndDeleteCookie() {
        logout()
        SessionTokenCookieHandler.run { deleteCookie() }
    }

    inline fun <reified V : AuthMethodConfiguration> lookupFlowMethodConfiguration(method: AuthenticationMethod): V {
        require(flows?.isNotEmpty() == true) { "No possible authentication flows to go from here in this Authentication Session (are you already authenticated?)" }
        val flow = flows!!.firstOrNull { it.method == method.id }
            ?: error("This authentication session provides no matching flow to go from here for authentication method: ${method.id}")
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

    suspend fun setSessionData(method: AuthenticationMethod, data: SessionData) {
        if (sessionData == null) {
            sessionData = HashMap()
        }

        sessionData!![method.id] = data

        SessionManager.updateSession(this)
    }

    inline fun <reified T : SessionData> getSessionData(method: AuthenticationMethod): T? {
        val retrievedData = sessionData?.get(method.id)
        //?: throw IllegalArgumentException("Session data for method ${method.id} not found")

        if (retrievedData != null && retrievedData !is T) {
            throw IllegalArgumentException("Session data for method ${method.id} is not of type ${T::class.jvmName}, but of ${retrievedData::class.jvmName}")
        }

        return retrievedData
    }

    suspend fun storeExternalIdMapping(namespace: String, externalId: String) {
        KtorAuthnzManager.sessionStore.storeExternalIdMapping(namespace = namespace, externalId = externalId, internalSessionId = id)
    }

    suspend fun dropExternalIdMapping(namespace: String, externalId: String) {
        KtorAuthnzManager.sessionStore.dropExternalIdMappingByExternal(namespace, externalId)
    }
}


@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AuthSessionInformation(
    @SerialName("session_id")
    val id: String,

    val status: AuthSessionStatus = AuthSessionStatus.INIT,

    @SerialName("current_method")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val currentlyActiveMethod: String? = null,

    @SerialName("next_method")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextMethod: List<String>? = null,

    @SerialName("next_step")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextStep: AuthSessionNextStep? = null,

    @SerialName("next_step_description")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextStepDescription: String? = null,

    /**
     * This attribute will possibly be removed in the future:
     */
    @SerialName("next_step_informational_message")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextStepInformationalMessage: String? = null,

    /**
     * Only display token if `httpOnlyToken=false` is set in config
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val token: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val expiration: Instant? = null,
)
