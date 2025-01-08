package id.walt.ktorauthnz.methods

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.Web3Identifier
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
import kotlinx.serialization.json.*
import org.web3j.crypto.ECDSASignature
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
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

    @Serializable
    data class SiweRequest(
        val challenge: String,
        val signed: String,
        val publicKey: String
    )

    fun verifySiwe(siwe: SiweRequest): String {
        val decodedJwt = siwe.challenge.decodeJws()
        val jwtPayload = decodedJwt.payload.jsonObject

        val nonce = jwtPayload["nonce"]?.jsonPrimitive?.content
            ?: authFailure("No nonce in token")

        val exp = jwtPayload["exp"]?.jsonPrimitive?.long
            ?: authFailure("No exp in token")

        val now = Clock.System.now().epochSeconds
        if (now > exp) {
            authFailure("Token expired")
        }

        // formatting the message according to the EIP-191 standard
        val prefix = "\u0019Ethereum Signed Message:\n"
        val messageLength = nonce.length.toString()
        val prefixedMessage = prefix + messageLength + nonce

        val messageHash = Sign.getEthereumMessageHash(prefixedMessage.toByteArray())

        // Parse signature components
        val signatureBytes = Numeric.hexStringToByteArray(siwe.signed.removePrefix("0x"))
        val r = BigInteger(1, signatureBytes.copyOfRange(0, 32))
        val s = BigInteger(1, signatureBytes.copyOfRange(32, 64))

        // The v value is the last byte, convert it to the correct format
        // MetaMask adds 27 to v, so we need to subtract it
        val v = (signatureBytes[64].toInt() and 0xFF) - 27


        val signature = ECDSASignature(r, s)

        // Recover the public key
        val recoveredKey = Sign.recoverFromSignature(
            v.toByte().toInt(),
            signature,
            messageHash
        ) ?: authFailure("Could not recover public key from signature")


        val recoveredAddress = "0x" + Keys.getAddress(recoveredKey)

        authCheck(recoveredAddress.equals(siwe.publicKey, ignoreCase = true)) {
            "Recovered address ($recoveredAddress) does not match provided address (${siwe.publicKey})"
        }


        return recoveredAddress
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
