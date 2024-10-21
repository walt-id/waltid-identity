package id.walt.ktorauthnz.methods

import com.atlassian.onetime.core.TOTP
import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.EmailAddress
import com.atlassian.onetime.model.Issuer
import com.atlassian.onetime.model.TOTPSecret
import com.atlassian.onetime.service.DefaultTOTPService
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.data.TOTPStoredData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

object TOTP : AuthenticationMethod("totp") {

    override val relatedAuthMethodStoredData = TOTPStoredData::class

    suspend fun auth(session: AuthSession, code: String) {
        val storedData = lookupStoredMultiData<TOTPStoredData>(session /* context() */)

        val userProvidedOtpCode = TOTP(code)
        val secret = TOTPSecret.fromBase32EncodedString(storedData.secret)

        val service = DefaultTOTPService()
        authCheck(
            service.verify(userProvidedOtpCode, secret).isSuccess()
        ) { "Invalid OTP" }
    }

    @Serializable
    data class TOTPCode(val code: String)

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("totp", {
            request { body<TOTPCode>() }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = getSession(authContext)

            val otp = when (call.request.contentType()) {
                ContentType.Application.Json -> call.receive<TOTPCode>().code
                ContentType.Application.FormUrlEncoded ->
                    call.receiveParameters()["code"] ?: error("Invalid or missing OTP code form post request.")

                else -> call.receiveText()
            }

            auth(session, otp)

            context.handleAuthSuccess(session, null)
        }
    }

}

fun main() {
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
}
