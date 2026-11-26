package id.walt.wallet2.mobile.credentials

import id.walt.wallet2.mobile.digitalcredentials.WalletAppGroupAccess
import id.walt.wallet2.persistence.encryption.IosDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Due-only status maintenance for an enrolled iOS wallet under its original storage namespace. */
public suspend fun refreshDueStatusesForExistingWallet(
    binding: String,
    crossProcessAccess: WalletAppGroupAccess? = null,
    maxItems: Int = 25,
    budget: Duration = 20.seconds,
): CredentialStatusMaintenanceResult = runCredentialStatusMaintenance(binding, maxItems, budget, open = { enrolled ->
    val factory = DriverFactory().apply { crossProcessAccess?.let { useAppGroup(it.appGroupIdentifier) } }
    if (enrolled.location != factory.existingLocation()) null else {
        val key = IosDatabaseEncryptionKeyProvider(crossProcessAccess?.keychainAccessGroup)
            .readExistingKey(enrolled.walletId, enrolled.databaseName)
        key?.let { factory.openExistingEncryptedDriver(enrolled.databaseName, enrolled.location, it, enrolled.walletId) }
    }
})
