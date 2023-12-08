package id.walt.service.account

import de.mkammerer.argon2.Argon2Factory
import id.walt.db.models.Accounts
import id.walt.web.UnauthorizedException
import id.walt.web.controllers.ByteLoginRequest
import id.walt.web.model.EmailAccountRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object EmailAccountStrategy : AccountStrategy<EmailAccountRequest> {
    override fun register(request: EmailAccountRequest): Result<RegistrationResult> = runCatching {
        val name = request.name ?: throw IllegalArgumentException("No name provided!")
        val email = request.email

        if (AccountsService.hasAccountEmail(email)) {
            throw IllegalArgumentException("Account already exists!")
        }

        val hash = hashPassword(ByteLoginRequest(request).password)

        val createdAccountId = transaction {
            Accounts.insert {
                it[Accounts.name] = name
                it[Accounts.email] = email
                it[password] = hash
                it[createdOn] = Clock.System.now().toJavaInstant()
            }[Accounts.id].value
        }

        RegistrationResult(createdAccountId)
    }

    override suspend fun authenticate(request: EmailAccountRequest): AuthenticatedUser = ByteLoginRequest(request).let { req ->
        val email = request.email

        if (!AccountsService.hasAccountEmail(email)) {
            throw UnauthorizedException("Unknown user \"${req.username}\".")
        }

        val (matchedAccount, pwHash) = transaction {
            val matchedAccount = Accounts.select { Accounts.email eq email }.first()

            val pwHash = matchedAccount[Accounts.password]
                ?: throw UnauthorizedException("User \"${req.username}\" does not have password authentication enabled.")

            Pair(matchedAccount, pwHash)
        }

        val passwordMatches = Argon2Factory.create().run {
            verify(pwHash, req.password).also {
                wipeArray(req.password)
            }
        }

        if (passwordMatches) {
            val id = matchedAccount[Accounts.id].value
            return AuthenticatedUser(id, req.username)
        } else {
            throw UnauthorizedException("Invalid password for \"${req.username}\"!")
        }
    }

    private fun hashPassword(password: ByteArray) = Argon2Factory.create().run {
        hash(10, 65536, 1, password).also {
            wipeArray(password)
        }
    }
}
