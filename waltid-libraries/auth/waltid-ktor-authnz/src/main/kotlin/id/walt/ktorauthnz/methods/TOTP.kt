package id.walt.ktorauthnz.methods

import com.atlassian.onetime.core.TOTP
import com.atlassian.onetime.model.TOTPSecret
import com.atlassian.onetime.service.DefaultTOTPService
import id.walt.commons.web.OTPAuthException
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.storeddata.TOTPStoredData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

object TOTP : AuthenticationMethod("totp") {

    override val relatedAuthMethodStoredData = TOTPStoredData::class

    suspend fun auth(session: AuthSession, code: String) {
        val storedData = lookupAccountStoredData<TOTPStoredData>(session.accountId ?: error("No account ID") /* context() */)

        val userProvidedOtpCode = TOTP(code)
        val secret = TOTPSecret.fromBase32EncodedString(storedData.secret)

        val service = DefaultTOTPService()
        authCheck(
            service.verify(userProvidedOtpCode, secret).isSuccess() , OTPAuthException()
        )
    }

    @Serializable
    data class TOTPCode(val code: String)

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        post("totp", {
            request { body<TOTPCode>() }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = call.getAuthSession(authContext)

            val contentType = call.request.contentType()
            val otp = when {
                contentType.match(ContentType.Application.Json) -> call.receive<TOTPCode>().code
                contentType.match(ContentType.Application.FormUrlEncoded) ->
                    call.receiveParameters()["code"] ?: error("Invalid or missing OTP code form post request.")

                else -> call.receiveText()
            }

            auth(session, otp)

            val authContext = authContext(call)
            call.handleAuthSuccess(session, authContext, null)
        }
    }

}

/*fun main() {
    val service = DefaultTOTPService()

    val secret = TOTPSecret.fromBase32EncodedString("ZIQL3WHUAGCS5FQQDKP74HZCFT56TJHR")
    val totpGenerator: TOTPGenerator = TOTPGenerator()
    val totp = totpGenerator.generateCurrent(secret) //TOTP(value=123456)
    println("totp: $totp")

    val totpUri = service.generateTOTPUrl(
        secret, ////NIQXUILREVGHIUKNORKHSJDHKMWS6UTY
        EmailAddress("jsmith@acme.com"),
        Issuer("Acme Co")
    )
    println("URI: $totpUri")
}*/
