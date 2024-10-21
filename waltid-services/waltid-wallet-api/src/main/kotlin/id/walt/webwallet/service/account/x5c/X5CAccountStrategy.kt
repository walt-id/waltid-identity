@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.account.x5c

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.webwallet.config.TrustedCAConfig
import id.walt.webwallet.db.models.Accounts
import id.walt.webwallet.db.models.X5CLogins
import id.walt.webwallet.service.account.*
import id.walt.webwallet.utils.PKIXUtils
import id.walt.webwallet.web.model.X5CAccountRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

object X5CAccountStrategy : PasswordlessAccountStrategy<X5CAccountRequest>() {

    private val x5cValidator = X5CValidator(ConfigManager.getConfig<TrustedCAConfig>().certificates)

    override suspend fun register(tenant: String, request: X5CAccountRequest): Result<RegistrationResult> =
        runCatching {
            val thumbprint = validate(request.token)

            AccountsService.getAccountByX5CId(tenant, thumbprint)?.let {
                throw IllegalArgumentException("Account already exists: $thumbprint")
            }

            val createdAccountId = transaction {
                addAccount(tenant, thumbprint)
            }
            return Result.success(RegistrationResult(createdAccountId))
        }

    override suspend fun authenticate(tenant: String, request: X5CAccountRequest): AuthenticatedUser {
        val thumbprint = validate(request.token)

        val registeredUserId = AccountsService.getAccountByX5CId(tenant, thumbprint)?.id ?:
        AccountsService.register(tenant, request).getOrThrow().id

        return X5CAuthenticatedUser(registeredUserId)
    }

    /**
     * Performs the following steps:
     * - extracts the x509 chain from header
     * - verifies the jwt [token]
     * - validates the trust chain
     * @param token the jwt token containing the x509 chain header
     * @return the public key thumbprint corresponding to the 1st certificate in the x509 chain
     */
    private suspend fun validate(token: String) = let {
        // extract x.509 cert chain from header
        val certificateChain = tryGetX5C(token)
        // convert to public jwk key
        val key = getKey(certificateChain[0])
        // verify token with the holder's public key
        key.verifyJws(token).getOrThrow()
        // validate the chain
        x5cValidator.validate(certificateChain).getOrThrow()
        key.getThumbprint()
    }

    private suspend fun getKey(certificate: String): Key {
        // convert holder certificate to pem format
        val pem = PKIXUtils.convertToPemFormat(certificate)
        // convert to key
        return JWKKey.importPEM(pem).getOrThrow()
    }

    private fun addAccount(tenant: String, thumbprint: String): Uuid {
        // add accounts record
        val accountId = Accounts.insert {
            it[Accounts.tenant] = tenant
            it[id] = Uuid.random()
            it[createdOn] = Clock.System.now().toJavaInstant()
        }[Accounts.id]
        // add x5c logins record
        X5CLogins.insert {
            it[X5CLogins.tenant] = tenant
            it[X5CLogins.accountId] = accountId
            it[x5cId] = thumbprint
        }
        return accountId
    }

    private fun tryGetX5C(jwt: String) = let {
        val x5cHeader = jwt.decodeJws().header["x5c"]
        require(x5cHeader is JsonArray) { "Invalid x5c header" }
        require(x5cHeader.size >= 1) { "x5c header must have at least 1 entry" }
        x5cHeader.map { it.jsonPrimitive.content }
    }
}
