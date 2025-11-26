@file:OptIn(ExperimentalTime::class)

package id.walt.ktorauthnz.methods

import id.walt.commons.web.InvalidChallengeException
import id.walt.commons.web.Web3AuthException
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.Web3Identifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.exceptions.authFailure
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import io.github.smiley4.ktoropenapi.post
import io.klogging.logger
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.web3j.crypto.ECDSASignature
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalStdlibApi::class)
object Web3 : AuthenticationMethod("web3") {
    private val log = logger<Web3>()

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

    override val supportsRegistration = true
    override val authenticationHandlesRegistration = true

    @Serializable
    data class SiweRequest(
        val challenge: String,
        val signed: String,
        val publicKey: String
    )

    suspend fun verifyEthereum2(challenge: String, signature: String, expectedAddress: String): String {
        val messageHash = Sign.getEthereumMessageHash(challenge.toByteArray())

        // Parse signature components
        val signatureBytes = Numeric.hexStringToByteArray(signature.removePrefix("0x"))
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

        authCheck(recoveredAddress.equals(expectedAddress, ignoreCase = true) ,
            Web3AuthException("Recovered address ($recoveredAddress) does not match provided address (${expectedAddress})")
        )

        return recoveredAddress
    }

    suspend fun verifySiweLogin(siweReq: SiweRequest): String {
        log.trace { "Verifying SIWE request: $siweReq" }

        val challenge = siweReq.challenge
        log.trace { "Challenge was: $challenge. Verifying challenge authenticity..." }

        authCheck(jwtHandler.validateToken(challenge) , InvalidChallengeException())
        log.trace { "Challenge is authentic. Verifying challenge timestamp..." }

        val decodedJwt = challenge.decodeJws()
        val jwtPayload = decodedJwt.payload.jsonObject

        //val nonce = jwtPayload["nonce"]?.jsonPrimitive?.content ?: authFailure("No nonce in token")
        val exp = jwtPayload["exp"]?.jsonPrimitive?.long ?: authFailure("No exp in token")

        val now = Clock.System.now().epochSeconds
        if (now > exp) {
            authFailure("Token expired")
        }

        log.trace { "Challenge did not yet expire. Verifying challenge signature..." }

        val address = verifyEthereum2(challenge, siweReq.signed, siweReq.publicKey)
        return address
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        route("web3") {
            get("nonce") {
                val newNonce = makeNonce()
                call.respond(newNonce)
            }

            post<SiweRequest>("signed", {
                request { body<SiweRequest> { required = true } }
            }) { req ->
                val session = call.getAuthSession(authContext)
                val address = verifySiweLogin(req)

                val identifier = Web3Identifier(address)
                val identifierResolved = identifier.resolveIfExists()

                if (identifierResolved == null) {
                    val registrationFunction = functionAmendments?.get(AuthMethodFunctionAmendments.Registration)
                        ?: error("Missing registration function amendment for web3 method")
                    registrationFunction.invoke(identifier)
                }

                val authContext = authContext(call)
                call.handleAuthSuccess(session, authContext, identifierResolved ?: identifier.resolveToAccountId())
            }
        }
    }
}
