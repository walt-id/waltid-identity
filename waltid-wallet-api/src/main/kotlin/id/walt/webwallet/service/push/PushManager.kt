package id.walt.webwallet.service.push

import com.interaso.webpush.VapidKeys
import com.interaso.webpush.WebPushService
import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.PushConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PushManager {

    private val log = KotlinLogging.logger { }

    private val pushConfig by lazy { ConfigManager.getConfig<PushConfig>() }

    // subject: "mailto:dev@walt.id"
    private val pushService = WebPushService(pushConfig.pushSubject, VapidKeys.create(pushConfig.pushPublicKey, pushConfig.pushPrivateKey.value))

    val subscriptions = ArrayList<Subscription>()

    /*init {
        thread {
            while (true) {
                println("Send notification")
                sendNotification()
                Thread.sleep(6000)
            }
        }
    }*/

    fun sendNotification(id: String, type: String, data: Map<String, String>) {
        val payload = Json.encodeToString(data).toByteArray()

        subscriptions.forEach { subscription ->
            val res = pushService.send(payload, subscription.endpoint, subscription.userPublicKey().encoded, subscription.authAsBytes())
            log.debug { "Push result for ${subscription.endpoint} (${subscription.userPublicKey().encoded}): $res" }
            /*val notification = Notification(
                subscription.endpoint,
                subscription.userPublicKey(),
                subscription.authAsBytes(),
                payload
            )

            val pushResponse = pushService.send(notification)
                .get()

            println("Push send response: $pushResponse")
            */
        }
    }

    fun sendIssuanceNotification(id: String, remoteHost: String, credentialTypes: List<String>, request: String) {
        sendNotification(
            id, "issuance", mapOf(
                "title" to
                        if (credentialTypes.size == 1) "You were offered a ${credentialTypes[0]}:"
                        else "You were offered ${credentialTypes.size} credentials:",
                "body" to "Issued by $remoteHost for $id",
                "action" to request,
                "type" to "issuance"
            )
        )
    }

    fun sendVerificationNotification(id: String, remoteHost: String, credentialTypes: List<String>, request: String) {
        sendNotification(
            id, "issuance", mapOf(
                "title" to "Share credentials:",
                "body" to "Requested by $remoteHost for $id",
                "action" to request,
                "type" to "verification"
            )
        )
    }

    fun registerSubscription(subscription: Subscription) {
        if (!subscriptions.contains(subscription)) {
            subscriptions.add(subscription)
            println("Subscription added: $subscription")
            println("Subscriptions: $subscriptions")
        } else {
            println("Subscription is already contained: $subscription")
        }
    }
}
