package id.walt.webwallet.service

import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.TrustConfig
import id.walt.webwallet.db.models.AccountWalletMappings
import id.walt.webwallet.db.models.AccountWalletPermissions
import id.walt.webwallet.db.models.Wallets
import id.walt.webwallet.seeker.DefaultCredentialTypeSeeker
import id.walt.webwallet.seeker.DefaultDidSeeker
import id.walt.webwallet.service.account.AccountsService
import id.walt.webwallet.service.category.CategoryServiceImpl
import id.walt.webwallet.service.nft.NftKitNftService
import id.walt.webwallet.service.nft.NftService
import id.walt.webwallet.service.settings.SettingsService
import id.walt.webwallet.service.trust.DefaultTrustValidationService
import io.ktor.client.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.concurrent.ConcurrentHashMap

object WalletServiceManager {

    private val walletServices = ConcurrentHashMap<Pair<UUID, UUID>, WalletService>()
    private val categoryService = CategoryServiceImpl
    private val settingsService = SettingsService
    private val http = HttpClient()
    private val trustConfig = ConfigManager.getConfig<TrustConfig>()
    val issuerTrustValidationService = DefaultTrustValidationService(http, trustConfig.issuersRecord)
    val verifierTrustValidationService = DefaultTrustValidationService(http, trustConfig.verifiersRecord)

    fun getWalletService(tenant: String, account: UUID, wallet: UUID): WalletService =
        walletServices.getOrPut(Pair(account, wallet)) {
            //WalletKitWalletService(account, wallet)
            SSIKit2WalletService(
                tenant = tenant,
                accountId = account,
                walletId = wallet,
                categoryService = categoryService,
                settingsService = settingsService,
                trustService = issuerTrustValidationService,
                didSeeker = DefaultDidSeeker(),
                credentialTypeSeeker = DefaultCredentialTypeSeeker(),
            )
        }

    fun createWallet(tenant: String, forAccount: UUID): UUID {
        val accountName = AccountsService.getNameFor(forAccount)

        // TODO: remove testing code / lock behind dev-mode
        if (accountName?.contains("multi-wallet") == true) {
            val second = Wallets.insert {
                it[name] = "ABC Company wallet"
                it[createdOn] = Clock.System.now().toJavaInstant()
            }[Wallets.id].value

            AccountWalletMappings.insert {
                it[AccountWalletMappings.tenant] = tenant
                it[accountId] = forAccount
                it[wallet] = second
                it[permissions] = AccountWalletPermissions.READ_ONLY
                it[addedOn] = Clock.System.now().toJavaInstant()
            }
        }

        val walletId = Wallets.insert {
            it[name] = "Wallet of $accountName"
            it[createdOn] = Clock.System.now().toJavaInstant()
        }[Wallets.id].value

        println("Creating wallet mapping: $forAccount -> $walletId")
        AccountWalletMappings.insert {
            it[AccountWalletMappings.tenant] = tenant
            it[accountId] = forAccount
            it[wallet] = walletId
            it[permissions] = AccountWalletPermissions.ADMINISTRATE
            it[addedOn] = Clock.System.now().toJavaInstant()
        }

        return walletId
    }

    @Deprecated(
        replaceWith = ReplaceWith(
            "AccountsService.getAccountWalletMappings(account)",
            "id.walt.service.account.AccountsService"
        ), message = "depreacted"
    )
    fun listWallets(tenant: String, account: UUID): List<UUID> =
        AccountWalletMappings.innerJoin(Wallets)
            .selectAll().where { (AccountWalletMappings.tenant eq tenant) and (AccountWalletMappings.accountId eq account) }.map {
                it[Wallets.id].value
            }

    fun getNftService(): NftService = NftKitNftService()
}