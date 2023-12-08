package id.walt.service.account

import id.walt.db.models.Accounts
import id.walt.db.models.Web3Wallets
import id.walt.web.model.AddressAccountRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object Web3WalletAccountStrategy : AccountStrategy<AddressAccountRequest> {
    override fun register(request: AddressAccountRequest): Result<RegistrationResult> = runCatching {
        val name = request.name

        if (AccountsService.hasAccountWeb3WalletAddress(request.address)) {
            throw IllegalArgumentException("Account already exists with address: ${request.address}")
        }

        val createdAccountId = transaction {
            val accountId = Accounts.insert {
                it[Accounts.name] = name
                it[createdOn] = Clock.System.now().toJavaInstant()
            }[Accounts.id].value

            Web3Wallets.insert {
                it[account] = accountId
                it[address] = request.address
                it[ecosystem] = request.ecosystem
                it[owner] = false
            }

            accountId
        }

        return Result.success(RegistrationResult(createdAccountId))
    }

    override suspend fun authenticate(request: AddressAccountRequest): AuthenticatedUser {
        val registeredUserId = if (AccountsService.hasAccountWeb3WalletAddress(request.address)) {
            AccountsService.getAccountByWeb3WalletAddress(request.address).first().id
        } else {
            AccountsService.register(request).getOrThrow().id
        }
        return AuthenticatedUser(registeredUserId, request.address)
    }
}
