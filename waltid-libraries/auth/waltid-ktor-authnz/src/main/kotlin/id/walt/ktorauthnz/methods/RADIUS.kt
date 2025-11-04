package id.walt.ktorauthnz.methods

import id.walt.commons.web.RadiusAuthException
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.RADIUSIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.config.RADIUSConfiguration
import id.walt.ktorauthnz.methods.requests.UserPassCredentials
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import org.aaa4j.radius.client.RadiusClient
import org.aaa4j.radius.client.clients.UdpRadiusClient
import org.aaa4j.radius.core.attribute.StringData
import org.aaa4j.radius.core.attribute.TextData
import org.aaa4j.radius.core.attribute.attributes.MessageAuthenticator
import org.aaa4j.radius.core.attribute.attributes.NasIdentifier
import org.aaa4j.radius.core.attribute.attributes.UserName
import org.aaa4j.radius.core.attribute.attributes.UserPassword
import org.aaa4j.radius.core.packet.Packet
import org.aaa4j.radius.core.packet.packets.AccessAccept
import org.aaa4j.radius.core.packet.packets.AccessRequest
import java.net.InetSocketAddress

object RADIUS : UserPassBasedAuthMethod("radius") {

    override suspend fun auth(session: AuthSession, credential: UserPasswordCredential, context: ApplicationCall): AccountIdentifier {
        val config = session.lookupFlowMethodConfiguration<RADIUSConfiguration>(this)

        val radiusClient: RadiusClient = UdpRadiusClient.newBuilder()
            .secret(config.radiusServerSecret.toByteArray())
            .address(InetSocketAddress(config.radiusServerHost, config.radiusServerPort))
            .build()

        val host = "${config.radiusServerHost}:${config.radiusServerPort}"

        val identifier = RADIUSIdentifier(host, credential.name)

        val accessRequest = AccessRequest(
            listOfNotNull(
                MessageAuthenticator(),
                UserName(TextData(credential.name)),
                UserPassword(StringData(credential.password.toByteArray())),
                if (config.radiusNasIdentifier != null)
                    NasIdentifier(TextData(config.radiusNasIdentifier)) else null
            )
        )

        val responsePacket: Packet = radiusClient.send(accessRequest)
        authCheck(responsePacket is AccessAccept , RadiusAuthException())

        return identifier
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        post("radius", {
            request { body<UserPassCredentials> { required = true } }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = call.getAuthSession(authContext)

            val credential = call.getUsernamePasswordFromRequest()

            val identifier = auth(session, credential, call)
            val authContext = authContext(call)
            call.handleAuthSuccess(session, authContext, identifier.resolveToAccountId())
        }
    }

}
