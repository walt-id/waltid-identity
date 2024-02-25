package id.walt.webwallet.service.profiles

import id.walt.webwallet.db.models.Profile
import id.walt.webwallet.db.models.Profiles
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

object ProfilesService {
    fun get(account: UUID): Profile? = transaction {
        Profiles.select { Profiles.account eq account }.singleOrNull()?.let {
            Profile(it)
        }
    }

    fun save(profile: Profile): Int = transaction {
        Profiles.upsert(Profiles.account) {
            it[this.account] = profile.account
            it[this.preferredEmail] = profile.preferredEmail
            it[this.preferredNumber] = profile.preferredNumber
            it[this.countryCode] = profile.countryCode
            it[this.preferredContactMethod] = profile.preferredContactMethod
        }.insertedCount
    }
}