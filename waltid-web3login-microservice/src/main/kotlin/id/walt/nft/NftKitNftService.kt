package id.walt.webwallet.service.nft

import id.walt.nftkit.services.*
import id.walt.webwallet.config.ChainExplorerConfiguration
import id.walt.webwallet.config.ConfigManager
import id.walt.webwallet.config.MarketPlaceConfiguration
import id.walt.webwallet.db.models.Web3Wallets
import id.walt.webwallet.service.dto.*
import id.walt.webwallet.service.nft.fetchers.DataFetcher
import id.walt.webwallet.service.nft.fetchers.parameters.*
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class NftKitNftService : NftService {

    override suspend fun getChains(ecosystem: String): List<ChainDataTransferObject> = when (ecosystem.lowercase()) {
        "ethereum" -> EVMChain.entries.toTypedArray()
        "tezos" -> TezosChain.entries.toTypedArray()
        "flow" -> FlowChain.entries.toTypedArray()
        "near" -> NearChain.entries.toTypedArray()
        "polkadot" -> PolkadotParachain.entries.toTypedArray()
        "algorand" -> AlgorandChain.entries.toTypedArray()

        else -> null
    }?.let {
        it.map { ChainDataTransferObject(it.name.uppercase(), ecosystem) }
    } ?: emptyList()

    override suspend fun getMarketPlace(parameter: TokenMarketPlaceParameter): MarketPlaceDataTransferObject? =
        ConfigManager.getConfig<MarketPlaceConfiguration>().let {
            it.marketplaces.firstOrNull {
                it.chain == parameter.chain.lowercase()
            }?.let {
                MarketPlaceDataTransferObject(
                    name = it.name,
                    url = String.format(it.url, parameter.contract, parameter.tokenId)
                )
            }
        }

    override suspend fun getExplorer(parameter: ChainExplorerParameter): ChainExplorerDataTransferObject? =
        ConfigManager.getConfig<ChainExplorerConfiguration>().let {
            it.explorers.firstOrNull {
                it.chain == parameter.chain.lowercase()
            }?.let {
                ChainExplorerDataTransferObject(String.format(it.url, parameter.contract))
            }
        }

    override suspend fun filterTokens(tenant: String, parameter: FilterParameter): List<FilterNftDataTransferObject> =
        parameter.ids.mapNotNull { id ->
            getWalletById(tenant, id)?.let { wallet -> //TODO: fix double conversion to wallet (here + inside getTokens)
                getChains(wallet.ecosystem).filter { net ->
                    parameter.networks.takeIf { it.isNotEmpty() }?.contains(net.network) ?: true//TODO: fix repeated conversion
                }.flatMap {
                    getTokens(tenant, ListFetchParameter(chain = it.network, walletId = id))
                }.let {
                    FilterNftDataTransferObject(tokens = it, owner = wallet)
                }
            }
        }

    override suspend fun getTokens(tenant: String, parameter: ListFetchParameter): List<NftDetailDataTransferObject> =
        getWalletById(tenant, parameter.walletId)?.let {
            DataFetcher.select(it.ecosystem).all(TokenListParameter(chain = parameter.chain, accountId = it.address))
        } ?: emptyList()

    override suspend fun getTokenDetails(tenant: String, parameter: DetailFetchParameter): NftDetailDataTransferObject =
        getWalletById(tenant, parameter.walletId)?.let {
            DataFetcher.select(it.ecosystem).byId(
                TokenDetailParameter(
                    chain = parameter.chain,
                    accountId = it.address,
                    contract = parameter.contract,
                    tokenId = parameter.tokenId,
                    collectionId = parameter.collectionId,
                )
            )
        } ?: throw IllegalArgumentException("Token details not available: $parameter")

    private fun getWalletById(tenant: String, accountId: String) = getWalletById(tenant, UUID(accountId))

    private fun getWalletById(tenant: String, accountId: UUID) =
        transaction {
            Web3Wallets.selectAll().where { (Web3Wallets.tenant eq tenant) and (Web3Wallets.accountId eq accountId) }.firstOrNull()
        }?.let {
            WalletDataTransferObject(address = it[Web3Wallets.address], ecosystem = it[Web3Wallets.ecosystem])
        }
}
