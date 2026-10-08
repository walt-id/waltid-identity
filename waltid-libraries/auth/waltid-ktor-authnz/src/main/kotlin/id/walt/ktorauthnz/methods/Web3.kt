package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.exceptions.AccountNotFoundException
import kotlin.time.Duration.Companion.minutes
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.exceptions.InvalidChallengeException
import id.walt.ktorauthnz.exceptions.Web3AuthException
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.Web3Identifier
import id.walt.ktorauthnz.amendments.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.exceptions.authFailure
import io.github.smiley4.ktoropenapi.post
import io.klogging.logger
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.web3j.crypto.ECDSASignature
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.SecureRandom

object Web3 : AuthenticationMethod("web3") {
    private val log = logger<Web3>()

    private val challengeLifetime = 5.minutes

    private fun challengeKey(challenge: String) = "web3-challenge:$challenge"

    /**
     * A new challenge for the wallet to sign: random, kept in the expiring store for [challengeLifetime] - so it works
     * across instances sharing that store - and accepted once.
     */
    suspend fun makeNonce(): String {
        val challenge = "Sign in: " + Numeric.toHexString(ByteArray(32).apply { SecureRandom().nextBytes(this) })
        KtorAuthnzManager.expiringStore.put(challengeKey(challenge), "issued", challengeLifetime)
        return challenge
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
        val signatureBytes = runCatching { Numeric.hexStringToByteArray(signature.removePrefix("0x")) }.getOrNull()
        authCheck(signatureBytes != null && signatureBytes.size == 65, Web3AuthException("Signature must be 65 bytes, hex encoded"))
        val r = BigInteger(1, signatureBytes.copyOfRange(0, 32))
        val s = BigInteger(1, signatureBytes.copyOfRange(32, 64))

        // The last byte is the recovery id: MetaMask and most wallets send 27/28, others (e.g. hardware wallets,
        // some libraries) the raw 0/1.
        val v = (signatureBytes[64].toInt() and 0xFF).let { if (it >= 27) it - 27 else it }
        authCheck(v == 0 || v == 1, Web3AuthException("Invalid signature recovery id"))


        val signature = ECDSASignature(r, s)

        // Recover the public key
        val recoveredKey = Sign.recoverFromSignature(
            v.toByte().toInt(),
            signature,
            messageHash
        ) ?: authFailure("Could not recover public key from signature")


        val recoveredAddress = "0x" + Keys.getAddress(recoveredKey)

        authCheck(
            recoveredAddress.equals(expectedAddress, ignoreCase = true),
            Web3AuthException("Recovered address ($recoveredAddress) does not match provided address (${expectedAddress})")
        )

        return recoveredAddress
    }

    suspend fun verifySiweLogin(siweReq: SiweRequest): String {
        log.trace { "Verifying SIWE request: $siweReq" }

        val challenge = siweReq.challenge
        log.trace { "Challenge was: $challenge. Verifying challenge authenticity..." }

        // Issued here, not expired, and not used before: taking it removes it.
        val store = KtorAuthnzManager.expiringStore
        authCheck(store.get(challengeKey(challenge)) != null, InvalidChallengeException())
        authCheck(store.putIfAbsent("${challengeKey(challenge)}:used", "used", challengeLifetime), InvalidChallengeException())
        store.remove(challengeKey(challenge))

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
                val accountId = accountFor(session, identifier, functionAmendments?.get(AuthMethodFunctionAmendments.Registration))
                call.handleAuthSuccess(session, authContext(call), accountId)
            }
        }
    }
}
