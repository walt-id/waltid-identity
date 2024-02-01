package id.walt.webwallet.service.account

import com.auth0.jwk.Jwk
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import id.walt.webwallet.db.models.Accounts
import id.walt.webwallet.db.models.OidcLogins
import id.walt.webwallet.service.OidcLoginService
import id.walt.webwallet.web.model.OidcAccountRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey

object OidcAccountStrategy : AccountStrategy<OidcAccountRequest>("oidc") {
    override suspend fun register(tenant: String, request: OidcAccountRequest): Result<RegistrationResult> {
        val jwt = verifiedToken(request.token)

        if (AccountsService.hasAccountOidcId(jwt.subject)) {
            throw IllegalArgumentException("Account already exists with OIDC id: ${request.token}")
        }

        val createdAccountId = transaction {
            val accountId = Accounts.insert {
                it[Accounts.tenant] = tenant
                it[id] = UUID.generateUUID()
                it[name] = jwt.getClaim("name").asString()
                it[email] = jwt.getClaim("email").asString()
                it[createdOn] = Clock.System.now().toJavaInstant()
            }[Accounts.id]

            OidcLogins.insert {
                it[OidcLogins.tenant] = tenant
                it[OidcLogins.accountId] = accountId
                it[oidcId] = jwt.subject
            }

            accountId
        }

        return Result.success(RegistrationResult(createdAccountId))
    }

    private fun verifiedToken(token: String): DecodedJWT {
        val decoded = JWT.decode(token)

        val verifier = JWT.require(OidcLoginService.jwkProvider.get(decoded.keyId).makeAlgorithm())
            .withIssuer(OidcLoginService.oidcRealm)
            .build()

        return verifier.verify(decoded)!!
    }

    internal fun Jwk.makeAlgorithm(): Algorithm = when (algorithm) {
        "RS256" -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
        "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
        "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
        "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
        "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
        "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
        null -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
        else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
    }

    override suspend fun authenticate(tenant: String, request: OidcAccountRequest): AuthenticatedUser {
        println("OIDC LOGIN REQUEST: ${request.token}")

        val jwt = verifiedToken(request.token)

        val registeredUserId = if (AccountsService.hasAccountOidcId(jwt.subject)) {
            AccountsService.getAccountByOidcId(jwt.subject)!!.id
        } else {
            AccountsService.register(tenant, request).getOrThrow().id
        }
        return AuthenticatedUser(registeredUserId, jwt.subject)
    }
}
