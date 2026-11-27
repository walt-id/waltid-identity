package id.walt.wallet2.mobile.credentials

import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.UserManager
import id.walt.wallet2.persistence.encryption.AndroidDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A cancellable, due-only check for a foreground-enrolled managed wallet. A credential-protected
 * application context and the original opaque [binding] are sufficient. The app owns scheduling.
 */
public suspend fun refreshDueStatusesForExistingWallet(
    context: Context,
    binding: String,
    maxItems: Int = 25,
    budget: Duration = 20.seconds,
): CredentialStatusMaintenanceResult {
    val application = context.applicationContext
    if (context.isDeviceProtectedStorage || !application.getSystemService(UserManager::class.java).isUserUnlocked) {
        return CredentialStatusMaintenanceResult.Deferred
    }
    return try {
        runCredentialStatusMaintenance(binding, maxItems, budget, open = { enrolled ->
            val key = AndroidDatabaseEncryptionKeyProvider(application)
                .readExistingKey(enrolled.walletId, enrolled.databaseName)
            key?.let { DriverFactory(application).openExistingEncryptedDriver(enrolled.databaseName, enrolled.location, it, enrolled.walletId) }
        })
    } catch (_: SQLiteException) {
        CredentialStatusMaintenanceResult.Deferred
    }
}
