package id.walt.webwallet.db.models

import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.webwallet.service.WalletService
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.uuid.UUID
import kotlinx.uuid.exposed.KotlinxUUIDTable
import kotlinx.uuid.exposed.kotlinxUUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.timestamp

object WalletOperationHistories : KotlinxUUIDTable("wallet_operation_histories") {
    val tenant = varchar("tenant", 128).default("")
    val accountId = kotlinxUUID("accountId")
    val wallet = reference("wallet", Wallets)
    val timestamp = timestamp("timestamp")
    val operation = varchar("operation", 48)
    val data = text("data")

    init {
        foreignKey(tenant, accountId, target = Accounts.primaryKey)
        index(false, tenant, accountId, wallet)
    }
}

@Serializable
data class WalletOperationHistory(
    val tenant: String,
    val id: UUID? = UUID.generateUUID(),
    val account: UUID,
    val wallet: UUID,
    val timestamp: Instant,
    val operation: String,
    val data: JsonObject,
) {
    constructor(result: ResultRow) : this(
        tenant = result[WalletOperationHistories.tenant],
        id = result[WalletOperationHistories.id].value,
        account = result[WalletOperationHistories.accountId],
        wallet = result[WalletOperationHistories.wallet].value,
        timestamp = result[WalletOperationHistories.timestamp].toKotlinInstant(),
        operation = result[WalletOperationHistories.operation],
        data = Json.parseToJsonElement(result[WalletOperationHistories.data]).jsonObject,
    )

    companion object {
        fun new(tenant: String, wallet: WalletService, operation: String, data: Map<String, String?>) = WalletOperationHistory(
            tenant = tenant,
            account = wallet.accountId,
            wallet = wallet.walletId,
            timestamp = Clock.System.now(),
            operation = operation,
            data = data.toJsonObject()
        )
    }
}
