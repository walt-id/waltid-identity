package id.walt.db.models

import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.db.models.AccountWalletMappings.nullable
import id.walt.service.WalletService
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
    val tenant = varchar("tenant", 128).nullable()
    val accountId = kotlinxUUID("accountId")
    val wallet = reference("wallet", Wallets)
    val timestamp = timestamp("timestamp")
    val operation = varchar("operation", 48)
    val data = text("data")

    init {
        AccountWalletMappings.foreignKey(AccountWalletMappings.tenant, AccountWalletMappings.accountId, target = Accounts.primaryKey)
    }
}

@Serializable
data class WalletOperationHistory(
    val id: UUID? = UUID.generateUUID(),
    val account: UUID,
    val wallet: UUID,
    val timestamp: Instant,
    val operation: String,
    val data: JsonObject,
) {
    constructor(result: ResultRow) : this(
        id = result[WalletOperationHistories.id].value,
        account = result[WalletOperationHistories.accountId],
        wallet = result[WalletOperationHistories.wallet].value,
        timestamp = result[WalletOperationHistories.timestamp].toKotlinInstant(),
        operation = result[WalletOperationHistories.operation],
        data = Json.parseToJsonElement(result[WalletOperationHistories.data]).jsonObject,
    )

    companion object {
        fun new(wallet: WalletService, operation: String, data: Map<String, String?>) = WalletOperationHistory(
            account = wallet.accountId,
            wallet = wallet.walletId,
            timestamp = Clock.System.now(),
            operation = operation,
            data = data.toJsonObject()
        )
    }
}
