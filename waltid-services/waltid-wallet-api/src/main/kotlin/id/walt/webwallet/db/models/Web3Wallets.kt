package id.walt.webwallet.db.models

import id.walt.webwallet.db.autoGenerate
import id.walt.webwallet.db.kotlinxUuid
import org.jetbrains.exposed.v1.core.Table
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object Web3Wallets : Table("web3wallets") {
    val tenant = varchar("tenant", 128).default("")
    val accountId = kotlinxUuid("accountId")

    val id = kotlinxUuid("id").autoGenerate()

    val address = varchar("address", 256).uniqueIndex()
    val ecosystem = varchar("ecosystem", 128)
    val owner = bool("owner")

    init {
        foreignKey(tenant, accountId, target = Accounts.primaryKey)
    }

    override val primaryKey = PrimaryKey(tenant, accountId, id)
}
