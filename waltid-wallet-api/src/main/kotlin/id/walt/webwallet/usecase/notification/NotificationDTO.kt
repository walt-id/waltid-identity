package id.walt.webwallet.usecase.notification

import id.walt.webwallet.db.models.Notification
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

//TODO: refactor the notification flow to listen to events,
// create notification for the subscribed ones
// and reference the event data instead of duplicating the event
@Serializable
data class NotificationDTO(
    val id: String? = null,
    val account: String,
    val wallet: String,
    val type: String,
    val status: Boolean,
    val addedOn: Instant,
    val data: JsonObject,
) {
    constructor(notification: Notification, data: JsonObject) : this(
        id = notification.id,
        account = notification.account,
        wallet = notification.wallet,
        type = notification.type,
        status = notification.status,
        addedOn = notification.addedOn,
        data = data,
    )
}