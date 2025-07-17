package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.dto.LinkedWalletDataTransferObject
import id.walt.webwallet.service.dto.WalletDataTransferObject
import id.walt.webwallet.web.controllers.auth.getWalletService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
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
            val wallet = call.getWalletService()
            call.respond<List<LinkedWalletDataTransferObject>>(wallet.getLinkedWallets())
        }

        post("link", {
            summary = "Add a web3 wallet"
            request {
                body<WalletDataTransferObject> {
                    required = true
                    description = "Wallet address and ecosystem"
                }
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
            val wallet = call.getWalletService()
            val data = Json.decodeFromString<WalletDataTransferObject>(call.receive())
            call.respond(wallet.linkWallet(data))
        }

        post("unlink", {
            summary = "Remove a web3 wallet"
            request {
                body<String> {
                    required = true
                    description = "Wallet id" }
            }
            response {
                HttpStatusCode.OK to { description = "Wallet unlinked" }
            }
        }) {
            val wallet = call.getWalletService()
            val walletId = Uuid.parse(call.receiveText())
            call.respond(wallet.unlinkWallet(walletId))
        }

        post("connect", {
            summary = "Connect a web3 wallet"
            request {
                body<WalletDataTransferObject> {
                    required = true
                    description = "Wallet address and ecosystem"
                }
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
            val wallet = call.getWalletService()
            val walletId = Uuid.parse(call.receiveText())
            call.respond(wallet.connectWallet(walletId))
        }

        post("disconnect", {
            summary = "Disconnect a web3 wallet"
            request {
                body<String> {
                    required = true
                    description = "Wallet id"
                }
            }
            response {
                HttpStatusCode.OK to { description = "Wallet disconnected" }
            }
        }) {
            val wallet = call.getWalletService()
            val walletId = Uuid.parse(call.receiveText())
            call.respond(wallet.disconnectWallet(walletId))
        }
    }
}
