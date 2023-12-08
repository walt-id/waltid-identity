package id.walt.web.controllers

import id.walt.service.dto.LinkedWalletDataTransferObject
import id.walt.service.dto.WalletDataTransferObject
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import kotlinx.uuid.UUID

fun Application.web3accounts() = walletRoute {
    route("web3accounts", {
        tags = listOf("Web3 wallet accounts")
    }) {
        get({
            summary = "List watched wallets"

            response {
                HttpStatusCode.OK to {
                    description = "Listing watched wallets"
                    body<List<LinkedWalletDataTransferObject>> {
                        description = "List of watched wallets"
                    }
                }
            }
        }) {
            val wallet = getWalletService()
            context.respond<List<LinkedWalletDataTransferObject>>(wallet.getLinkedWallets())
        }

        post("link", {
            summary = "Add a web3 wallet"
            request {
                body<WalletDataTransferObject> { description = "Wallet address and ecosystem" }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Wallet linked"
                    body<LinkedWalletDataTransferObject> {
                        description = "TODO"
                    }
                }
            }
        }) {
            val wallet = getWalletService()
            val data = Json.decodeFromString<WalletDataTransferObject>(call.receive())
            context.respond(wallet.linkWallet(data))
        }

        post("unlink", {
            summary = "Remove a web3 wallet"
            request {
                body<String> { description = "Wallet id" }
            }
            response {
                HttpStatusCode.OK to { description = "Wallet unlinked" }
            }
        }) {
            val wallet = getWalletService()
            val walletId = UUID(call.receiveText())
            context.respond(wallet.unlinkWallet(walletId))
        }

        post("connect", {
            summary = "Connect a web3 wallet"
            request {
                body<WalletDataTransferObject> { description = "Wallet address and ecosystem" }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Wallet connected"
                    body<LinkedWalletDataTransferObject> {
                        description = "TODO"
                    }
                }
            }
        }) {
            val wallet = getWalletService()
            val walletId = UUID(call.receiveText())
            context.respond(wallet.connectWallet(walletId))
        }

        post("disconnect", {
            summary = "Disconnect a web3 wallet"
            request {
                body<String> { description = "Wallet id" }
            }
            response {
                HttpStatusCode.OK to { description = "Wallet disconnected" }
            }
        }) {
            val wallet = getWalletService()
            val walletId = UUID(call.receiveText())
            context.respond(wallet.disconnectWallet(walletId))
        }
    }
}
