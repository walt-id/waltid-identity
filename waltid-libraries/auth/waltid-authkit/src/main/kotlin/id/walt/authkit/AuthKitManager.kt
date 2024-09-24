package id.walt.authkit

import id.walt.authkit.accounts.AccountStore
import id.walt.authkit.accounts.ExampleAccountStore
import id.walt.authkit.sessions.InMemorySessionStore
import id.walt.authkit.tokens.TokenHandler
import id.walt.authkit.tokens.authkittoken.AuthKitTokenHandler

object AuthKitManager {

    var accountStore: AccountStore = ExampleAccountStore
    var sessionStore = InMemorySessionStore()

    var tokenHandler: TokenHandler = AuthKitTokenHandler()
    /*var tokenHandler: TokenHandler = JwtTokenHandler().apply {
        runBlocking {
            signingKey = JWKKey.importJWK("{\"kty\":\"OKP\",\"d\":\"1JU5RIQIs4L5RPoYc3Qfzk_n_m6Ende1_hcJOSf2NYU\",\"crv\":\"Ed25519\",\"kid\":\"CSMdhzTFRmrnKxir3gPFs6lmSLKZrNnwk9meKBg5bYM\",\"x\":\"UBOjkIuse1xW6yLQyZkPfIMennHW2l1mg7vOpIJPrqM\"}").getOrThrow()
            verificationKey = signingKey
        }
    }*/

}
