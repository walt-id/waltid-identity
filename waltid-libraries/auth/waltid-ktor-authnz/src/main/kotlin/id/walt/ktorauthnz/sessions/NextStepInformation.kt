package id.walt.ktorauthnz.sessions

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


/**
 * An additional step is required for a multi-step flow (e.g. OIDC)
 */
@Serializable
sealed class AuthSessionNextStep(

)

@Serializable
@SerialName("redirect")
data class AuthSessionNextStepRedirectData(
    //val url: String
    val url: Url,
) : AuthSessionNextStep()

@Serializable
@SerialName("custom")
data class AuthSessionNextStepCustomData(
    val data: JsonElement
) : AuthSessionNextStep()
