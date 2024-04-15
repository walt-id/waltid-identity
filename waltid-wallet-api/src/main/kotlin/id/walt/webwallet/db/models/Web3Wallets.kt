package id.walt.webwallet.db.models

import kotlinx.uuid.exposed.autoGenerate
import kotlinx.uuid.exposed.kotlinxUUID
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Web3Wallets : Table("web3wallets") {
    val tenant = varchar("tenant", 128).default("")
    val accountId = kotlinxUUID("accountId")

    val id = kotlinxUUID("id").autoGenerate()

    val address = varchar("address", 256).uniqueIndex()
    val ecosystem = varchar("ecosystem", 128)
    val owner = bool("owner")

    init {
        foreignKey(tenant, accountId, target = Accounts.primaryKey, onDelete = ReferenceOption.CASCADE)
    }

    override val primaryKey = PrimaryKey(tenant, accountId, id)
}
