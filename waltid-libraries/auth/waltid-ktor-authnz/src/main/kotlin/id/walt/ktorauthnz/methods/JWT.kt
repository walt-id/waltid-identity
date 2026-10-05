package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.exceptions.AuthenticationFailureException
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.MACVerifier
import id.walt.ktorauthnz.exceptions.JWTVerificationException
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.JWTIdentifier
import id.walt.ktorauthnz.amendments.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.config.JwtAuthConfiguration
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

object JWT : AuthenticationMethod("jwt") {

    fun auth(jwt: String, config: JwtAuthConfiguration): JWTIdentifier {
        // todo: handle others
        val parsedJws = runCatching { JWSObject.parse(jwt) }.getOrNull() ?: throw JWTVerificationException()
        val jwtVerifier = MACVerifier(config.verifyKey)

        authCheck(parsedJws.verify(jwtVerifier), JWTVerificationException())

        val id = parsedJws.payload.toJSONObject()[config.identifyClaim] as? String
            ?: throw AuthenticationFailureException("JWT has no \"${config.identifyClaim}\" claim")

        return JWTIdentifier(id)
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        post("jwt", {
            request { body<String> { required = true } }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = call.getAuthSession(authContext)
            val config = session.lookupFlowMethodConfiguration<JwtAuthConfiguration>(this@JWT)

            val jwt = call.receiveText()
            val id = auth(jwt, config)

            val authContext = authContext(call)
            call.handleAuthSuccess(session, authContext, id.resolveToAccountId())
        }
    }


}
