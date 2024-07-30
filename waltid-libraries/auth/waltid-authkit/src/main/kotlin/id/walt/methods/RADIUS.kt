package id.walt.methods

import org.aaa4j.radius.client.RadiusClient
import org.aaa4j.radius.client.RadiusClientException
import org.aaa4j.radius.client.clients.UdpRadiusClient
import org.aaa4j.radius.core.attribute.Attribute
import org.aaa4j.radius.core.attribute.StringData
import org.aaa4j.radius.core.attribute.TextData
import org.aaa4j.radius.core.attribute.attributes.NasIdentifier
import org.aaa4j.radius.core.attribute.attributes.UserName
import org.aaa4j.radius.core.attribute.attributes.UserPassword
import org.aaa4j.radius.core.packet.Packet
import org.aaa4j.radius.core.packet.packets.AccessAccept
import org.aaa4j.radius.core.packet.packets.AccessRequest
import java.net.InetSocketAddress

class RADIUS {

    val radiusClient: RadiusClient = UdpRadiusClient.newBuilder()
        .secret("sharedsecret".toByteArray())
        .address(InetSocketAddress("10.1.1.10", 1812))
        .build()

    fun auth(): Result<Boolean> {
        val accessRequest = AccessRequest(
            listOf(
                UserName(TextData("john.doe")),
                UserPassword(StringData("hunter2".toByteArray())),
                NasIdentifier(TextData("SSID1"))
            )
        )

        try {
            val responsePacket: Packet = radiusClient.send(accessRequest)

            if (responsePacket is AccessAccept) {
                println(responsePacket)
                return Result.success(true)
            } else {
                return Result.failure(NotImplementedError("no exception here yet"))
                TODO("todo")
            }
        } catch (e: RadiusClientException) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

}
