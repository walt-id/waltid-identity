package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.dto.*
import id.walt.webwallet.service.nft.fetchers.parameters.*
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import id.walt.webwallet.service.dto.*
import id.walt.webwallet.service.nft.fetchers.parameters.*
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun Application.nfts() = walletRoute {
    route("nft", {
        tags = listOf("NFTs")
    }) {
        get("chains/{ecosystem}", {
            summary = "Fetch the list of ecosystem networks for the provided ecosystem"
            request {
                pathParameter<String>("ecosystem") {
                    description = "the ecosystem name"
                    example = "ethereum"
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<List<ChainDataTransferObject>> {
                        description = "The list of ecosystem networks"
                    }
                }
            }
        }) {
            val nft = getNftService()
            val ecosystem = call.parameters["ecosystem"] ?: throw IllegalArgumentException("No ecosystem provided")
            call.respond(nft.getChains(ecosystem = ecosystem))
        }
        get("filter", {
            summary = "Fetch the list of tokens with details"
            request {
                queryParameter<String>("accountId") {
                    description = "Wallet account Id"
                    required = false
                }
                queryParameter<String>("network") {
                    description = "Blockchain network name"
                    example = "mumbai"
                    required = false
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<List<FilterNftDataTransferObject>> {
                        description = "The list of tokens"
                    }
                }
            }
        }) {
            val nft = getNftService()
            val accountIds = call.request.queryParameters.getAll("accountId")
            val networks = call.request.queryParameters.getAll("network")
            call.respond(
                nft.filterTokens("", FilterParameter(accountIds ?: emptyList(), networks ?: emptyList()))
                // FIXME -> TENANT HERE
            )
        }
        get("list/{account}/{chain}", {
            summary = "Fetch the list of tokens with details"
            request {
                pathParameter<String>("account") {
                    description = "the account address"
                    example = "0x..."
                }
                pathParameter<String>("chain") {
                    description = "the chain name"
                    example = "mumbai"
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<List<NftDetailDataTransferObject>> {
                        description = "The list of tokens"
                    }
                }
            }
        }) {
            val nft = getNftService()
            val chain = call.parameters["chain"] ?: throw IllegalArgumentException("No chain provided")
            val account = call.parameters["account"] ?: throw IllegalArgumentException("No account provided")
            call.respond(
                nft.getTokens(
                    "", // FIXME -> TENANT HERE
                    ListFetchParameter(
                        chain = chain,
                        walletId = account
                    )
                )
            )
        }
        get("detail/{account}/{chain}/{contract}/{tokenId}", {
            summary = "Fetch token details"
            request {
                pathParameter<String>("account") {
                    description = "the account address"
                    example = "0x..."
                }
                pathParameter<String>("chain") {
                    description = "the chain name"
                    example = "mumbai"
                }
                pathParameter<String>("contract") {
                    description = "the contract address"
                    example = "0x..."
                }
                pathParameter<String>("tokenId") {
                    description = "the nft tokenId"
                    example = "1"
                }
                queryParameter<String>("collectionId") {
                    description = "Collection Id (for Polkadot ecosystem)"
                    required = false
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<NftDetailDataTransferObject> {
                        description = "The token detail data transfer object"
                    }
                }
            }
        }) {
            val chain = call.parameters["chain"] ?: throw IllegalArgumentException("No chain provided")
            val account = call.parameters["account"] ?: throw IllegalArgumentException("No account provided")
            val contract = call.parameters["contract"] ?: throw IllegalArgumentException("No contract provided")
            val tokenId = call.parameters["tokenId"] ?: throw IllegalArgumentException("No tokenId provided")
            val collection = call.request.queryParameters["collectionId"]
            val nft = getNftService()
            runCatching {
                nft.getTokenDetails(
                    "", // FIXME -> TENANT HERE
                    DetailFetchParameter(
                        chain = chain,
                        walletId = account,
                        contract = contract,
                        tokenId = tokenId,
                        collectionId = collection
                    )
                )
            }.onSuccess {
                call.respond(it)
            }.onFailure { context.response.status(HttpStatusCode.NotFound) }
        }
        get("marketplace/{chain}/{contract}/{tokenId}", {
            summary = "Retrieve the marketplace data"
            request {
                pathParameter<String>("chain") {
                    description = "the chain name"
                    example = "polygon"
                }
                pathParameter<String>("contract") {
                    description = "the contract address"
                }
                pathParameter<String>("tokenId") {
                    description = "the tokenId"
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<MarketPlaceDataTransferObject> {
                        description = "The marketplace data"
                    }
                }
            }
        }) {
            val nft = getNftService()
            nft.getMarketPlace(
                TokenMarketPlaceParameter(
                    chain = call.parameters["chain"] ?: throw IllegalArgumentException("No chain provided"),
                    contract = call.parameters["contract"]
                        ?: throw IllegalArgumentException("No contract provided"),
                    tokenId = call.parameters["tokenId"] ?: throw IllegalArgumentException("No tokenId provided")
                )
            )?.run {
                call.respond(this)
            } ?: run {
                context.response.status(HttpStatusCode.NotFound)
            }
        }
        get("explorer/{chain}/{contract}", {
            summary = "Retrieve the chain explorer data"
            request {
                pathParameter<String>("chain") {
                    description = "the chain name"
                    example = "polygon"
                }
                pathParameter<String>("contract") {
                    description = "the contract address"
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<ChainExplorerDataTransferObject> {
                        description = "The chain explorer data"
                    }
                }
            }
        }) {
            val nft = getNftService()
            nft.getExplorer(
                ChainExplorerParameter(
                    chain = call.parameters["chain"] ?: throw IllegalArgumentException("No chain provided"),
                    contract = call.parameters["contract"]
                        ?: throw IllegalArgumentException("No contract provided"),
                )
            )?.run {
                call.respond(this)
            } ?: run {
                context.response.status(HttpStatusCode.NotFound)
            }
        }
    }
}
