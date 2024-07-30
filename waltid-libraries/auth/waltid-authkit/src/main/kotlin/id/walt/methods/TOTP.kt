package id.walt.methods

import com.atlassian.onetime.core.TOTP
import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.EmailAddress
import com.atlassian.onetime.model.Issuer
import com.atlassian.onetime.model.TOTPSecret
import com.atlassian.onetime.service.AsciiRangeSecretProvider
import com.atlassian.onetime.service.AsyncSecretProvider
import com.atlassian.onetime.service.DefaultTOTPService
import com.atlassian.onetime.service.TOTPConfiguration
import java.util.concurrent.CompletableFuture

class AsyncAsciiRangeSecretProvider : AsyncSecretProvider {

    override fun generateSecret(): CompletableFuture<TOTPSecret> =
        CompletableFuture.supplyAsync {
            AsciiRangeSecretProvider.generateSecret()
        }
}

fun interface CPSSecretProvider {
    suspend fun generateSecret(): TOTPSecret
}

fun main() {
    val service = DefaultTOTPService(
        totpGenerator = TOTPGenerator(),
        totpConfiguration = TOTPConfiguration()
    )

    val secret = TOTPSecret.fromBase32EncodedString("ZIQL3WHUAGCS5FQQDKP74HZCFT56TJHR")
    val totpGenerator: TOTPGenerator = TOTPGenerator()
    val totp = totpGenerator.generateCurrent(secret) //TOTP(value=123456)
    println("totp: $totp")

    val totpUri = service.generateTOTPUrl(
        secret, ////NIQXUILREVGHIUKNORKHSJDHKMWS6UTY
        EmailAddress("jsmith@acme.com"),
        Issuer("Acme Co")
    )
    println("URI: $totpUri")


    val userInput: TOTP = TOTP("123456") //TOTP from user input
    val result = service.verify(
        userInput,
        secret //NIQXUILREVGHIUKNORKHSJDHKMWS6UTY
    )
    println("Result: $result")
}
