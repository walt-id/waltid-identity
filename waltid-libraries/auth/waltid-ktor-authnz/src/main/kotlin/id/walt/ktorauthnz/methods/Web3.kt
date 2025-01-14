package id.walt.ktorauthnz.methods

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.Web3Identifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.exceptions.authFailure
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.klogging.logger
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

    suspend fun verifyEthereum2(nonce: String, signature: String, expectedAddress: String): String {
        // formatting the message according to the EIP-191 standard
        val prefix = "\u0019Ethereum Signed Message:\n"
        val messageLength = nonce.length.toString()
        val prefixedMessage = prefix + messageLength + nonce

        val messageHash = Sign.getEthereumMessageHash(prefixedMessage.toByteArray())

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

        authCheck(recoveredAddress.equals(expectedAddress, ignoreCase = true)) {
            "Recovered address ($recoveredAddress) does not match provided address (${expectedAddress})"
        }


        return recoveredAddress
    }

    suspend fun verifyEthereum(nonce: String, signature: String, expectedAddress: String): String {
        log.trace { "Provided signature (hex): $signature" }

        // Step 1: Compute the Ethereum message hash
        log.trace { "Challenge nonce: $nonce" }
        val messageHashX = Sign.getEthereumMessageHash(nonce.toByteArray())

        val prefix = "\u0019Ethereum Signed Message:\n"
        val prefixedMessage = prefix + nonce.length.toString() + nonce
        val messageHash = Sign.getEthereumMessageHash(prefixedMessage.toByteArray())

        log.trace { "Unprefixed message hash: ${messageHashX.toHexString()}" }
        log.trace { "Prefixed message hash  : ${messageHash.toHexString()}" }

        log.trace { "Ethereum hash (hex) for challenge nonce: ${messageHash.toHexString()}" }

        // Step 2: Decode the signature
        val signatureBytes = Numeric.hexStringToByteArray(signature)
        if (signatureBytes.size != 65) throw IllegalArgumentException("Invalid signature length")

        val r = signatureBytes.copyOfRange(0, 32)
        val s = signatureBytes.copyOfRange(32, 64)
        val v = signatureBytes[64].toInt() and 0xFF

        // Adjust `v` value to Ethereum standard (27 or 28)
        val vCorrected = if (v < 27) v + 27 else v
        log.trace { "Original v: $v, corrected v: $vCorrected" }


        // Step 3: Recover the public key from the signature
        val signatureData = Sign.SignatureData(vCorrected.toByte(), r, s)
        log.trace { "Decoded signature, getting public key from signature..." }

        val publicKey: BigInteger = try {
            Sign.signedMessageToKey(messageHash, signatureData)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid signature", e)
        }

        // Step 4: Derive the Ethereum address from the public key
        val recoveredAddress = "0x" + Keys.getAddress(publicKey)
        log.trace { "Recovered address from challenge signature: $recoveredAddress" }

        // Step 5: Compare the recovered address to the expected address
        authCheck(recoveredAddress.equals(expectedAddress, ignoreCase = true)) {
            "Recovered address ($recoveredAddress) does not match provided address (${expectedAddress})"
        }
        log.trace { "Recovered address ($recoveredAddress) matches expected address." }

        return recoveredAddress
    }

    suspend fun verifySiweLogin(siweReq: SiweRequest): String {
        log.trace { "Verifying SIWE request: $siweReq" }

        val challenge = siweReq.challenge
        log.trace { "Challenge was: $challenge. Verifying challenge authenticity..." }

        authCheck(jwtHandler.validateToken(challenge)) { "Cannot verify that nonce was supplied by system." }
        log.trace { "Challenge is authentic. Verifying challenge timestamp..." }

        val decodedJwt = challenge.decodeJws()
        val jwtPayload = decodedJwt.payload.jsonObject

        val nonce = jwtPayload["nonce"]?.jsonPrimitive?.content ?: authFailure("No nonce in token")
        val exp = jwtPayload["exp"]?.jsonPrimitive?.long ?: authFailure("No exp in token")

        val now = Clock.System.now().epochSeconds
        if (now > exp) {
            authFailure("Token expired")
        }

        log.trace { "Challenge did not yet expire. Verifying challenge signature..." }

        val address = verifyEthereum2(nonce, siweReq.signed, siweReq.publicKey)
        return address

        /*// formatting the message according to the EIP-191 standard
        val prefix = "\u0019Ethereum Signed Message:\n"
        val messageLength = nonce.length.toString()
        val prefixedMessage = prefix + messageLength + nonce

        val messageHash = Sign.getEthereumMessageHash(prefixedMessage.toByteArray())

        // Parse signature components
        val signatureBytes = Numeric.hexStringToByteArray(siweReq.signed.removePrefix("0x"))
        val r =  signatureBytes.copyOfRange(0, 32)
        val s =  signatureBytes.copyOfRange(32, 64)
        val vUncorrected = signatureBytes[64].toInt() and 0xFF

        // Adjust `v` value to fit the Ethereum convention (27 or 28)
        val v = if (vUncorrected < 27) vUncorrected + 27 else vUncorrected

        val signatureData = Sign.SignatureData(v.toByte(), r, s)

        val publicKey: BigInteger? = try {
            Sign.signedMessageToKey(messageHash, signatureData)
        } catch (e: Exception) {
            null
        }


        val signature = ECDSASignature(r, s)

        // Recover the public key
        val recoveredPublicKey = Sign.recoverFromSignature(
            v.toByte().toInt(), signature, messageHash
        ) ?: authFailure("Could not recover public key from signature")


        val recoveredAddress = "0x" + Keys.getAddress(recoveredPublicKey)

        authCheck(recoveredAddress.equals(siweReq.publicKey, ignoreCase = true)) {
            "Recovered address ($recoveredAddress) does not match provided address (${siweReq.publicKey})"
        }*/
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        route("web3") {
            get("nonce") {
                val newNonce = makeNonce()
                context.respond(newNonce)
            }

            post<SiweRequest>("signed", {
                request { body<SiweRequest>() }
            }) { req ->
                val session = getSession(authContext)
                val address = verifySiweLogin(req)

                val identifier = Web3Identifier(address)
                val identifierResolved = identifier.resolveIfExists()

                if (identifierResolved == null) {
                    val registrationFunction = functionAmendments?.get(AuthMethodFunctionAmendments.Registration) ?: error("Missing registration function amendment for web3 method")
                    registrationFunction.invoke(identifier)
                }

                context.handleAuthSuccess(session, identifierResolved ?: identifier.resolveToAccountId())
            }
        }
    }
}
