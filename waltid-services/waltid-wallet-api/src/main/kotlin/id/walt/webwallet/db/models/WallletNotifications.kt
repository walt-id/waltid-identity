@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.db.models

import id.walt.webwallet.db.kotlinxUuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object WalletNotifications : UUIDTable("notifications") {
    //TODO: change to reference username
    val account = kotlinxUuid("account")
    val wallet = reference("wallet", Wallets)
    val isRead = bool("is_read").default(false)
    val type = varchar("type", 128)
    val addedOn = timestamp("added_on")
    val data = text("data")
}

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Notification(
    val id: String? = null,
    val account: String,
    val wallet: String,
    val type: String,
    val status: Boolean,
    val addedOn: Instant,
    val data: Data,
) {
    constructor(
        id: String? = null,
        account: String,
        wallet: String,
        type: String,
        status: Boolean,
        addedOn: Instant,
        data: JsonElement,
    ) : this(
        id = id,
        account = account,
        wallet = wallet,
        type = type,
        status = status,
        addedOn = addedOn,
        data = NotificationDataSerializer.decodeFromJsonElement<Data>(data),
    )

    constructor(resultRow: ResultRow) : this(
        id = resultRow[WalletNotifications.id].value.toString(),
        account = resultRow[WalletNotifications.account].toString(),
        wallet = resultRow[WalletNotifications.wallet].value.toString(),
        type = resultRow[WalletNotifications.type],
        status = resultRow[WalletNotifications.isRead],
        addedOn = resultRow[WalletNotifications.addedOn].toKotlinInstant(),
        data = NotificationDataSerializer.parseToJsonElement(resultRow[WalletNotifications.data]).jsonObject,
    )

    @Serializable
    sealed interface Data

    @Serializable
    data class CredentialIssuanceData(
        val credentialId: String,
        val credentialType: String = "",
        val issuer: String = "",
        val logo: String = "",
    ) : Data
}

private val NotificationDataSerializer = Json { ignoreUnknownKeys = true }
fun Notification.Data.serialize() = NotificationDataSerializer.encodeToString(this)
