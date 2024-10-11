package id.walt.ktorauthnz.methods

import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.MACVerifier
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.JWTIdentifier
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.config.JwtAuthConfiguration
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

object JWT : AuthenticationMethod("jwt") {

    fun auth(jwt: String, config: JwtAuthConfiguration): JWTIdentifier {
        // todo: handle others
        val parsedJws = JWSObject.parse(jwt)
        val jwtVerifier = MACVerifier(config.verifyKey)

        authCheck(parsedJws.verify(jwtVerifier)) { "Could not verify JWT!" }

        val id = parsedJws.payload.toJSONObject()[config.identifyClaim] as String

        return JWTIdentifier(id)
    }

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("jwt", {
            request { body<String>() }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = getSession(authContext)
            val config = session.lookupConfiguration<JwtAuthConfiguration>(this@JWT)

            val jwt = context.receiveText()
            val id = auth(jwt, config)

            context.handleAuthSuccess(session, id.resolveToAccountId())
        }
    }


}
