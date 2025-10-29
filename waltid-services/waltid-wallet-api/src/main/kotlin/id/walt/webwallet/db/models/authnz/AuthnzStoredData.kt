package id.walt.webwallet.db.models.authnz

import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.json

object AuthnzStoredData : Table() {
    val id = uuid("id").autoGenerate()
    val accountId = uuid("account_id").references(AuthnzUsers.id)
    val method = varchar("method", 50)
    val data = json("data", Json.Default, AuthMethodStoredData.serializer())

    override val primaryKey = PrimaryKey(id)
}
