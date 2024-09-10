package id.walt.authkit.methods

import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.MACVerifier
import id.walt.authkit.AuthContext
import id.walt.authkit.accounts.identifiers.JWTIdentifier
import id.walt.authkit.exceptions.authCheck
import id.walt.authkit.methods.config.AuthMethodConfiguration
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

object JWT : AuthenticationMethod("jwt") {

    @Serializable
    data class JwtAuthConfiguration(
        val verifyKey: String,
        val identifyClaim: String = "sub",
    ) : AuthMethodConfiguration

    fun auth(jwt: String, config: JwtAuthConfiguration): JWTIdentifier {
        // todo: handle others
        val parsedJws = JWSObject.parse(jwt)
        val jwtVerifier = MACVerifier(config.verifyKey)

        authCheck(parsedJws.verify(jwtVerifier)) { "Could not verify JWT!" }

        val id = parsedJws.payload.toJSONObject()[config.identifyClaim] as String

        return JWTIdentifier(id)
    }

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("jwt") {
            val session = getSession(authContext)
            val config = session.lookupConfiguration<JwtAuthConfiguration>(this@JWT)

            val jwt = context.receiveText()
            val id = auth(jwt, config)

            context.handleAuthSuccess(session, id.resolveToAccountId())
        }
    }


}
