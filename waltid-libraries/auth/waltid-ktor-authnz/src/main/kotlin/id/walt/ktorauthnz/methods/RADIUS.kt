package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.RADIUSIdentifier
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.config.RADIUSConfiguration
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.aaa4j.radius.client.RadiusClient
import org.aaa4j.radius.client.clients.UdpRadiusClient
import org.aaa4j.radius.core.attribute.StringData
import org.aaa4j.radius.core.attribute.TextData
import org.aaa4j.radius.core.attribute.attributes.NasIdentifier
import org.aaa4j.radius.core.attribute.attributes.UserName
import org.aaa4j.radius.core.attribute.attributes.UserPassword
import org.aaa4j.radius.core.packet.Packet
import org.aaa4j.radius.core.packet.packets.AccessAccept
import org.aaa4j.radius.core.packet.packets.AccessRequest
import java.net.InetSocketAddress

object RADIUS : UserPassBasedAuthMethod("radius") {

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier {
        val config = session.lookupConfiguration<RADIUSConfiguration>(this)

        val radiusClient: RadiusClient = UdpRadiusClient.newBuilder()
            .secret(config.radiusServerSecret.toByteArray())
            .address(InetSocketAddress(config.radiusServerHost, config.radiusServerPort))
            .build()

        val host = "${config.radiusServerHost}:${config.radiusServerPort}"

        val identifier = RADIUSIdentifier(host, credential.name)

        val accessRequest = AccessRequest(
            listOf(
                UserName(TextData(credential.name)),
                UserPassword(StringData(credential.password.toByteArray())),
                NasIdentifier(TextData(config.radiusNasIdentifier))
            )
        )

        val responsePacket: Packet = radiusClient.send(accessRequest)
        authCheck(responsePacket is AccessAccept) { "RADIUS server did not accept authentication" }

        return identifier
    }


    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("radius", {
            request { body<UserPassCredentials>() }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = getSession(authContext)

            val credential = call.getUsernamePasswordFromRequest()

            val identifier = auth(session, credential, context)
            context.handleAuthSuccess(session, identifier.resolveToAccountId())
        }
    }

}
