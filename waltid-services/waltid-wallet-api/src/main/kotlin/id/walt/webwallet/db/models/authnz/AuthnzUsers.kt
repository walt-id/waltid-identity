package id.walt.webwallet.db.models.authnz

import org.jetbrains.exposed.sql.Table

object AuthnzUsers : Table() {
    val id = uuid("id").autoGenerate()

    override val primaryKey = PrimaryKey(id)
}
