package id.walt.webwallet.db.models

import kotlinx.serialization.Serializable
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

object Profiles : Table("profiles") {
    val account = reference("account", Accounts.id).uniqueIndex()
    val preferredEmail = varchar("preferred_email", 128)
    val countryCode = varchar("country_code", 8)
    val preferredNumber = varchar("preferred_number", 128)
    val preferredContactMethod = varchar("preferred_contact_method", 128)

    override val primaryKey: PrimaryKey = PrimaryKey(Profiles.account)
}

@Serializable
data class Profile(
    val account: UUID,
    val preferredEmail: String,
    val preferredNumber: String,
    val countryCode: String,
    val preferredContactMethod: String,
) {
    constructor(resultRow: ResultRow) : this(
        account = resultRow[Profiles.account],
        preferredEmail = resultRow[Profiles.preferredEmail],
        preferredNumber = resultRow[Profiles.preferredNumber],
        countryCode = resultRow[Profiles.countryCode],
        preferredContactMethod = resultRow[Profiles.preferredContactMethod],
    )
}