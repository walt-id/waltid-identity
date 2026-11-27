package id.walt.wallet2.mobile

import app.cash.sqldelight.db.SqlDriver
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.did.dids.Crypto2DidService
import id.walt.mdoc.proximity.mobile.BleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.mdoc.proximity.mobile.WifiAwareProximityTransportFactory
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.mobile.credentials.StatusMaintenanceEnrollmentStore
import id.walt.wallet2.mobile.persistence.DatabaseKeySource
import id.walt.wallet2.mobile.digitalcredentials.CredentialRegistry
import id.walt.wallet2.mobile.digitalcredentials.UnavailableCredentialRegistry
import id.walt.wallet2.mobile.digitalcredentials.RegistryProjection
import id.walt.wallet2.mobile.identity.WalletDidSupport
import id.walt.wallet2.mobile.identity.SigningIdentityManager
import id.walt.wallet2.mobile.identity.toKeySpec
import id.walt.wallet2.mobile.presentation.NativeScaPresentationAuthorizer
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import id.walt.wallet2.persistence.keys.WalletKeyCreationRequest
import id.walt.wallet2.persistence.keys.WalletKeyRequirements
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightDidStore
import id.walt.wallet2.persistence.stores.SqlDelightIssuanceSessionStore
import id.walt.wallet2.persistence.stores.SqlDelightKeyStore
import kotlin.uuid.Uuid
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun openEncryptedSqlDelightWallet(
    config: WalletConfiguration,
    managedDatabaseKeyProvider: DatabaseEncryptionKeyProvider,
    platformKeyProvider: PlatformManagedKeyProvider,
    proximityTransportFactory: BleProximityTransportFactory,
    proximityNfcHostPlatformAdapter: NfcHostPlatformAdapter? = null,
    proximityWifiAwareTransportFactory: WifiAwareProximityTransportFactory? = null,
    openEncryptedDriver: (
        databaseName: String,
        encryptionKey: DatabaseEncryptionKey,
        isDeviceLocal: Boolean,
        walletId: String,
    ) -> SqlDriver,
    deleteDatabase: (databaseName: String) -> Unit,
    registry: CredentialRegistry = config.digitalCredentials.registry ?: UnavailableCredentialRegistry,
    registrationProjection: RegistryProjection = RegistryProjection.Full,
    maintenanceLocation: (databaseName: String) -> String? = { null },
    databaseLocation: String = "wallet_${config.walletId}",
    keyNamespace: String = config.walletId,
): Wallet {
    // Resolver state belongs to this process, including when all wallet keys already exist.
    WalletDidSupport.ensureInitialized()
    val databaseName = "wallet_${config.walletId}"
    val databaseKeyProvider = when (val databaseKeySource = config.persistence.databaseKeySource) {
        is DatabaseKeySource.Managed -> managedDatabaseKeyProvider
        is DatabaseKeySource.Provided -> databaseKeySource.provider
    }
    val lease = WalletStorageLease(databaseLocation, keyNamespace,
        listOfNotNull(config.persistence.credentialStore, config.persistence.didStore))
    suspend fun open(owner: WalletStorageLease?): Wallet {
        val encryptionKey = databaseKeyProvider.getOrCreateKey(config.walletId, databaseName)
        // An app-provided key source may complete normally after its caller has cancelled.
        currentCoroutineContext().ensureActive()
        val driver = openEncryptedDriver(
            databaseName,
            encryptionKey,
            config.persistence.databaseKeySource is DatabaseKeySource.Managed,
            config.walletId,
        )
        return try {
            currentCoroutineContext().ensureActive()
            openSqlDelightWallet(
                config = config,
                db = WalletPersistenceDatabase(driver),
                keyProvider = platformKeyProvider,
                registry = registry,
                registrationProjection = registrationProjection,
                proximityTransportFactory = proximityTransportFactory,
                proximityNfcHostPlatformAdapter = proximityNfcHostPlatformAdapter,
                proximityWifiAwareTransportFactory = proximityWifiAwareTransportFactory,
                maintenanceLocation = { maintenanceLocation(databaseName) },
                persistenceOwner = WalletPersistenceOwner(driver::close, { open(null) }, owner),
                deleteLocalPersistence = {
                    deleteDatabase(databaseName)
                    databaseKeyProvider.deleteKey(config.walletId, databaseName)
                },
            )
        } catch (failure: Throwable) {
            try { driver.close() } catch (closeFailure: Throwable) { failure.addSuppressed(closeFailure) }
            throw failure
        }
    }
    lease.open()
    return try {
        open(lease)
    } catch (failure: Throwable) {
        withContext(NonCancellable) { lease.close() }
        throw failure
    }
}

internal fun openSqlDelightWallet(
    config: WalletConfiguration,
    db: WalletPersistenceDatabase,
    keyProvider: PlatformManagedKeyProvider,
    proximityTransportFactory: BleProximityTransportFactory? = null,
    proximityNfcHostPlatformAdapter: NfcHostPlatformAdapter? = null,
    proximityWifiAwareTransportFactory: WifiAwareProximityTransportFactory? = null,
    didService: Crypto2DidService = Crypto2DidService,
    deleteLocalPersistence: suspend () -> Unit,
    maintenanceLocation: () -> String? = { null },
    persistenceOwner: WalletPersistenceOwner? = null,
    registry: CredentialRegistry = config.digitalCredentials.registry ?: UnavailableCredentialRegistry,
    registrationProjection: RegistryProjection = RegistryProjection.Full,
): Wallet {
    val queries = db.walletPersistenceQueries
    val managedStatus = config.persistence.databaseKeySource == DatabaseKeySource.Managed && config.persistence.credentialStore == null
    val keyStore = SqlDelightKeyStore(keyProvider, queries, config.walletId)
    val credentialStore = config.persistence.credentialStore ?: SqlDelightCredentialStore(queries)
    val didStore = config.persistence.didStore?.let { delegate ->
        object : WalletDidStore by delegate {
            override suspend fun getDefaultDid(): String? =
                queries.selectActiveIdentity(config.walletId).executeAsOneOrNull()?.did
        }
    } ?: SqlDelightDidStore(queries, config.walletId)
    val issuanceSessionStore = SqlDelightIssuanceSessionStore(queries)
    return Wallet(
        scaAuthorizer = NativeScaPresentationAuthorizer(keyProvider),
        walletId = config.walletId,
        createSigningIdentityManager = { lifecycle, onActive ->
            SigningIdentityManager(
                config.walletId, config.signingIdentity, config.keyAuthorization.policy, config.keyAuthorization.prompt,
                keyStore, didStore, keyProvider, queries, didService, onActive, lifecycle,
            )
        },
        keyStore = keyStore,
        didStore = didStore,
        managedCredentialStatus = managedStatus,
        statusMaintenance = StatusMaintenanceEnrollmentStore(config.walletId, queries) {
            if (managedStatus) maintenanceLocation() else null
        },
        credentialStore = credentialStore,
        issuanceSessionStore = issuanceSessionStore,
        runKeyUseAuthorizationPreflight = { keyType, policy ->
            keyStore.preflight(
                WalletKeyRequirements(
                    spec = keyType.toKeySpec(),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                    authorizationPolicy = policy,
                )
            )
        },
        defaultKeyUseAuthorizationPolicy = config.keyAuthorization.policy,
        generateAndPersistHolderKey = { keyType, policy ->
            keyStore.generateKey(WalletKeyCreationRequest(
                id = KeyId("wallet_holder_${Uuid.random()}"),
                requirements = WalletKeyRequirements(keyType.toKeySpec(), setOf(KeyUsage.SIGN, KeyUsage.VERIFY), policy),
                prompt = config.keyAuthorization.prompt,
            ))
        },
        attestationConfig = config.issuance.attestation,
        walletProviderConfig = config.issuance.walletProvider,
        preferredLocales = config.preferredLocales,
        receiptTrust = config.issuance.receiptTrust,
        transactionDataProfiles = config.presentation.transactionDataProfiles,
        paymentCredentialIssuers = config.presentation.paymentCredentialIssuers,
        clientIdTrustConfiguration = config.presentation.verifierTrust,
        credentialIssuerMetadataTrustResolver = config.issuance.metadataTrustResolver,
        onEvent = config.onEvent,
        credentialRegistry = registry,
        registrationProjection = registrationProjection,
        onDigitalCredentialRegistryChanged = config.digitalCredentials.onRegistrationChanged,
        readerTrustEvaluator = config.digitalCredentials.readerTrustEvaluator,
        proximityTransportFactory = proximityTransportFactory,
        proximityNfcHostPlatformAdapter = proximityNfcHostPlatformAdapter,
        proximityWifiAwareTransportFactory = proximityWifiAwareTransportFactory,
        persistenceOwner = persistenceOwner,
        deleteLocalPersistence = deleteLocalPersistence,
    )
}

/** Resources allocated by the platform factory. A closed wallet reopens storage only for explicit erasure. */
internal class WalletPersistenceOwner(
    val close: () -> Unit,
    val openForDeletion: suspend () -> Wallet,
    val storageLease: WalletStorageLease? = null,
)
