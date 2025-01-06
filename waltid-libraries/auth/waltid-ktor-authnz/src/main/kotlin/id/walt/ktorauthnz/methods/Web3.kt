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
    fun debugEthereumSignature(
        signature: String,
        message: String,
        expectedAddress: String
    ) {
        println("=== Ethereum Signature Debug ===")

        // 1. Message formatting
        val cleanMessage = message.removePrefix("0x")
        val messageLength = cleanMessage.length.toString()
        // The \u0019 prefix is important for Ethereum signed messages
        val ethMessage = "\u0019Ethereum Signed Message:\n$messageLength$cleanMessage"

        println("Original message: $message")
        println("Clean message (no 0x): $cleanMessage")
        println("Message length: $messageLength")
        println("Formatted message (hex): ${ethMessage.toByteArray().toHexString()}")
        println("Formatted message (text): $ethMessage")

        // 2. Hash calculation
        val messageHash = org.web3j.crypto.Hash.sha3(ethMessage.toByteArray())
        println("\nMessage hash: ${Numeric.toHexString(messageHash)}")

        // 3. Signature parsing
        val cleanSignature = signature.removePrefix("0x")
        val signatureBytes = Numeric.hexStringToByteArray(cleanSignature)

        val r = signatureBytes.slice(0..31).toByteArray()
        val s = signatureBytes.slice(32..63).toByteArray()
        val v = signatureBytes[64]

        println("\nSignature components:")
        println("r: ${Numeric.toHexString(r)}")
        println("s: ${Numeric.toHexString(s)}")
        println("v: $v (decimal: ${v.toInt() and 0xFF})")

        // 4. Address recovery
        val recoveredKey = Sign.signedMessageHashToKey(
            messageHash,
            Sign.SignatureData(v, r, s)
        )

        val recoveredAddress = "0x" + Keys.getAddress(recoveredKey)
        println("\nRecovered address: $recoveredAddress")
        println("Expected address:  $expectedAddress")
        println("Addresses match: ${recoveredAddress.equals(expectedAddress, ignoreCase = true)}")
    }

    fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }



    fun verifySignature(web3ExampleSigned: MultiStepExampleSigned) {
        try {
            println("====DEBUG LOGS====")
            val decodedJwt = web3ExampleSigned.challenge.decodeJws()
            val jwtPayload = decodedJwt.payload.jsonObject

            val nonce = jwtPayload["nonce"]?.jsonPrimitive?.content
                ?: authFailure("No nonce in token")

            // Remove 0x prefix for message formatting
            val cleanNonce = nonce.removePrefix("0x")
            val expectedMessage = "\u0019Ethereum Signed Message:\n${cleanNonce.length}$cleanNonce"
            println("Formatted message: $expectedMessage") // Debug


            debugEthereumSignature(
                signature = web3ExampleSigned.signed,
                message = nonce,
                expectedAddress = web3ExampleSigned.publicKey
            )

            val messageHash = org.web3j.crypto.Hash.sha3(expectedMessage.toByteArray())

            val signatureBytes = Numeric.hexStringToByteArray(web3ExampleSigned.signed.removePrefix("0x"))
            val r = signatureBytes.slice(0..31).toByteArray()
            val s = signatureBytes.slice(32..63).toByteArray()
            val v = signatureBytes[64]

            // Try to recover the address
            val recoveredPublicKey = Sign.signedMessageHashToKey(
                messageHash,
                Sign.SignatureData(v, r, s)
            )

            val recoveredAddress = "0x" + Keys.getAddress(recoveredPublicKey)

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
