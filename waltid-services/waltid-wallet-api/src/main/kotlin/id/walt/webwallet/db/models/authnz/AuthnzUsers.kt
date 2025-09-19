package id.walt.webwallet.db.models.authnz

import org.jetbrains.exposed.v1.core.Table

object AuthnzUsers : Table() {
    val id = uuid("id").autoGenerate()

    override val primaryKey = PrimaryKey(id)
}
