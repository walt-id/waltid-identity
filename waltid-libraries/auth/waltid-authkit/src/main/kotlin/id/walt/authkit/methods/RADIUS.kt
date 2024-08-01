package id.walt.authkit.methods

import id.walt.authkit.AuthContext
import id.walt.authkit.exceptions.authCheck
import id.walt.authkit.methods.data.AuthMethodStoredData
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
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

object RADIUS : UserPassBasedAuthMethod() {

    @Serializable
    data class RADIUSConfiguration(
        val radiusServerHost: String,
        val radiusServerPort: Int,
        val radiusServerSecret: String,
        val radiusNasIdentifier: String,
    ): AuthMethodStoredData

    val radiusClient: RadiusClient = UdpRadiusClient.newBuilder()
        .secret("sharedsecret".toByteArray())
        .address(InetSocketAddress("10.1.1.10", 1812))
        .build()

    // Todo: Move to configuration (is not stored data)

    override suspend fun auth(credential: UserPasswordCredential) {
        val accessRequest = AccessRequest(
            listOf(
                UserName(TextData(credential.name)),
                UserPassword(StringData(credential.password.toByteArray())),
                NasIdentifier(TextData("this is like a client id"))
            )
        )

        val responsePacket: Packet = radiusClient.send(accessRequest)
        authCheck(responsePacket is AccessAccept) { "RADIUS server did not accept authentication" }

    }


    override fun Route.register(context: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("userpass") {
            val credential = call.getUsernamePasswordFromRequest()

            auth(credential)
        }
    }

}
