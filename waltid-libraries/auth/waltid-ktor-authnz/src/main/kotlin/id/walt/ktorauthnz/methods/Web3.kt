package id.walt.ktorauthnz.methods

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.Web3Identifier
import id.walt.ktorauthnz.exceptions.AuthenticationFailureException
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.exceptions.authFailure
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.security.SecureRandom


object Web3 : AuthenticationMethod("web3") {
    private val jwtHandler = JwtTokenHandler().apply {

        signingKey = runBlocking { JWKKey.generate(KeyType.Ed25519) }
        verificationKey = signingKey
    }
    private const val NONCE_VALIDITY_SECONDS = 300L // 5 minutes


    suspend fun makeNonce(): String {
        val nonce = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val nonceHex = Numeric.toHexString(nonce)

        val payload = buildJsonObject {
            put("nonce", JsonPrimitive(nonceHex))
            put("exp", JsonPrimitive(Clock.System.now().epochSeconds + NONCE_VALIDITY_SECONDS))
        }.toString().toByteArray()

        return jwtHandler.signingKey.signJws(payload)
    }


    fun verifySignature(web3ExampleSigned: MultiStepExampleSigned) {
        try {

            println("Received challenge: ${web3ExampleSigned.challenge}")
            println("Received signature: ${web3ExampleSigned.signed}")
            println("Received public key: ${web3ExampleSigned.publicKey}")

            // Decode and validate JWT
            val decodedJwtTest = web3ExampleSigned.challenge.decodeJws()
            println("Decoded JWT payload: ${decodedJwtTest.payload}")


            // Verify format of Ethereum address
            authCheck(web3ExampleSigned.publicKey.startsWith("0x") && web3ExampleSigned.publicKey.length == 42) {
                "Invalid Ethereum address format"
            }

            // Verify format of signature
            authCheck(web3ExampleSigned.signed.startsWith("0x")) {
                "Invalid signature format - must start with 0x"
            }

            // Verify and decode JWT
            val decodedJwt = web3ExampleSigned.challenge.decodeJws()
            val jwtPayload = decodedJwt.payload.jsonObject

            // Check JWT has required fields
            authCheck(jwtPayload.containsKey("nonce")) {
                "JWT missing nonce claim"
            }

            // Get nonce from JWT
            val nonce = jwtPayload["nonce"]?.jsonPrimitive?.content ?: authFailure("No nonce in token")
            val message = "\u0019Ethereum Signed Message:\n${nonce.length}$nonce"
            val messageHash = org.web3j.crypto.Hash.sha3(nonce.toByteArray())

            println("Nonce: $nonce")
            println("Message: $message")
            println("Message hash: $messageHash")


            // Parse signature
            val signatureBytes = try {
                Numeric.hexStringToByteArray(web3ExampleSigned.signed.removePrefix("0x"))
            } catch (e: Exception) {
                authFailure("Invalid signature format: ${e.message}")
            }

            authCheck(signatureBytes.size == 65) {
                "Invalid signature length: expected 65 bytes, got ${signatureBytes.size}"
            }

            val r = signatureBytes.slice(0..31).toByteArray()
            val s = signatureBytes.slice(32..63).toByteArray()
            val v = signatureBytes[64]


            println("r: ${Numeric.toHexString(r)}")
            println("s: ${Numeric.toHexString(s)}")
            println("v: $v")


            // Recover public key and address
            val recoveredPublicKey = try {
                Sign.signedMessageToKey(
                    messageHash,
                    Sign.SignatureData(v, r, s)
                )
            } catch (e: Exception) {
                authFailure("Failed to recover public key from signature: ${e.message}")
            }


            val recoveredAddress = "0x" + Keys.getAddress(recoveredPublicKey)

            // Compare addresses case-insensitively
            authCheck(recoveredAddress.equals(web3ExampleSigned.publicKey, ignoreCase = true)) {
                "Recovered address ($recoveredAddress) does not match provided address (${web3ExampleSigned.publicKey})"
            }

        } catch (e: Exception) {
            when (e) {
                is AuthenticationFailureException -> throw e
                else -> authFailure("Signature verification failed: ${e.message}")
            }
        }
    }

//    fun verifySignature(web3ExampleSigned: MultiStepExampleSigned) {
//        web3ExampleSigned.challenge // check that the challenge comes from us (is a JWT made by us)
//
//        // check that signed challenge verifies correctly
//        authCheck(web3ExampleSigned.signed == "${web3ExampleSigned.challenge}-signed") { "Invalid signature" }
//
//        // check that public key belongs to signature
//        web3ExampleSigned.publicKey
//    }

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

                val identifier =
                    Web3Identifier(req.publicKey) // select identifier (= who logged in with this method now?)

                context.handleAuthSuccess(
                    session,
                    identifier.resolveToAccountId()
                ) // handleAuthSuccess() -> session is now logged in
            }
        }
    }
}
