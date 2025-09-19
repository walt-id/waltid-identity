package id.walt.webwallet.db.models.authnz

import org.jetbrains.exposed.v1.core.Table

object AuthnzAccountIdentifiers : Table() {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(AuthnzUsers.id)
    val identifier = varchar("identifier", 255)
    //val method = varchar("method", 50)

    override val primaryKey = PrimaryKey(id)
}
