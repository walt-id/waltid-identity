package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.Web3Identifier
import id.walt.siwe.SiweRequest
import id.walt.siwe.Web3jSignatureVerifier
import id.walt.siwe.eip4361.Eip4361Message
import id.walt.siwe.nonceBlacklists
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import java.util.*


object Web3 : AuthenticationMethod("web3") {

    fun makeNonce(): String {
        val newNonce = UUID.randomUUID().toString()
        return newNonce
    }


    fun verifySiwe(siwe: SiweRequest): String {

        val eip4361msg = Eip4361Message.fromString(siwe.message)
        println("EIP4361msg: $eip4361msg")
        println("EIP nonce: ${eip4361msg.nonce}")

        if (nonceBlacklists.contains(eip4361msg.nonce)) {
            throw IllegalArgumentException("Nonce reused.")
        }

        val address = eip4361msg.address.lowercase()
        val signature = siwe.signature
        val origMsg = eip4361msg.toString()


        val signatureVerification = Web3jSignatureVerifier.verifySignature(address, signature, origMsg)
        if (!signatureVerification) {
            throw IllegalArgumentException("Invalid signature.")
        }
        nonceBlacklists.add(eip4361msg.nonce!!)

        return address
    }


    override fun Route.registerAuthenticationRoutes(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        route("web3") {
            get("nonce") {
                val newNonce = makeNonce()
                context.respond(newNonce)
            }

            post<SiweRequest>("signed", {
                request { body<SiweRequest>() }
            }) { req ->

                val session = getSession(authContext)
                val account = verifySiwe(req)

                val identifier =
                    Web3Identifier(account) // select identifier (= who logged in with this method now?)

                context.handleAuthSuccess(
                    session,
                    identifier.resolveToAccountId()
                ) // handleAuthSuccess() -> session is now logged in
            }
        }
    }
}
