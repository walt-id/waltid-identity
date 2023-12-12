package id.walt.webwallet.service.events

import id.walt.webwallet.db.models.Events
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.ResultRow

const val EventDataNotAvailable = "n/a"

@Serializable
data class Event(
    val event: String,
    val action: String,
    val timestamp: Instant = Clock.System.now(),
    val tenant: String? = null,
    val originator: String? = null,
    val account: UUID,
    val wallet: UUID,
    val data: JsonObject,
) {
    constructor(
        action: EventType.Action, tenant: String?, originator: String?, account: UUID, wallet: UUID, data: EventData
    ) : this(
        event = action.type,
        action = action.toString(),
        tenant = tenant,
        originator = originator,
        account = account,
        wallet = wallet,
        data = Json.encodeToJsonElement(data).jsonObject
    )

    constructor(row: ResultRow) : this(
        event = row[Events.event],
        action = row[Events.action],
        timestamp = row[Events.timestamp].toKotlinInstant(),
        tenant = row[Events.tenant],
        originator = row[Events.originator],
        account = row[Events.account],
        wallet = row[Events.wallet].value,
        data = Json.parseToJsonElement(row[Events.data]).jsonObject,
    )
}