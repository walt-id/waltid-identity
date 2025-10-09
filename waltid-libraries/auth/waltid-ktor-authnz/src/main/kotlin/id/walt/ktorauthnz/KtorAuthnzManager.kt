package id.walt.ktorauthnz

import id.walt.ktorauthnz.accounts.EditableAccountStore
import id.walt.ktorauthnz.security.PasswordHashingConfiguration
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.sessions.SessionStore
import id.walt.ktorauthnz.tokens.TokenHandler
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler

object KtorAuthnzManager {

    var passwordHashingConfig = PasswordHashingConfiguration()
    lateinit var accountStore: EditableAccountStore
    var sessionStore: SessionStore = InMemorySessionStore()

    var tokenHandler: TokenHandler = KtorAuthNzTokenHandler()
    /*var tokenHandler: TokenHandler = JwtTokenHandler().apply {
        runBlocking {
            signingKey = JWKKey.importJWK("{\"kty\":\"OKP\",\"d\":\"1JU5RIQIs4L5RPoYc3Qfzk_n_m6Ende1_hcJOSf2NYU\",\"crv\":\"Ed25519\",\"kid\":\"CSMdhzTFRmrnKxir3gPFs6lmSLKZrNnwk9meKBg5bYM\",\"x\":\"UBOjkIuse1xW6yLQyZkPfIMennHW2l1mg7vOpIJPrqM\"}").getOrThrow()
            verificationKey = signingKey
        }
    }*/

}
