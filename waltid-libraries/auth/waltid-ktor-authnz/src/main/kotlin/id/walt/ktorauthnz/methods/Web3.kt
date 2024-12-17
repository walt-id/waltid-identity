package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.Web3Identifier
import id.walt.ktorauthnz.exceptions.authCheck
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import kotlin.random.Random

object Web3 : AuthenticationMethod("web3") {

    fun makeNonce() = "n" + Random.nextInt() // should be a JWT

    fun verifySignature(web3ExampleSigned: MultiStepExampleSigned) {
        web3ExampleSigned.challenge // check that the challenge comes from us (is a JWT made by us)

        // check that signed challenge verifies correctly
        authCheck(web3ExampleSigned.signed == "${web3ExampleSigned.challenge}-signed") { "Invalid signature" }

        // check that public key belongs to signature
        web3ExampleSigned.publicKey
    }

    @Serializable
    data class MultiStepExampleSigned(
        val challenge: String,
        val signed: String,
        val publicKey: String
    )

    override fun Route.registerAuthenticationRoutes(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        route("web3") {
            get("nonce") { // Step 1
                context.respond(makeNonce())
            }

            post<MultiStepExampleSigned>("signed", { // Step 2
                request { body<MultiStepExampleSigned>() }
            }) { req ->
                val session = getSession(authContext)

                verifySignature(req) // Verification

                // Verification was successful:

                val identifier = Web3Identifier(req.publicKey) // select identifier (= who logged in with this method now?)

                context.handleAuthSuccess(
                    session,
                    identifier.resolveToAccountId()
                ) // handleAuthSuccess() -> session is now logged in
            }
        }
    }
}
