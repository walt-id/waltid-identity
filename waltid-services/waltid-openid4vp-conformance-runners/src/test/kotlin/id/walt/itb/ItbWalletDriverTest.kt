@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.itb

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.data.Wallet
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ItbWalletDriverTest {
    @Test
    fun `live presentation rejects an unsigned request before wallet disclosure`() = runTest {
        val clientId = "redirect_uri:https://verifier.example/response"
        val header = """{"alg":"none","typ":"oauth-authz-req+jwt"}""".encodeToByteArray().encodeToBase64Url()
        val body = """{"client_id":"$clientId","nonce":"nonce","aud":"https://self-issued.me/v2","response_type":"vp_token","response_mode":"direct_post","response_uri":"https://verifier.example/response"}"""
            .encodeToByteArray().encodeToBase64Url()
        val url = URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", clientId)
            parameters.append("request", "$header.$body.")
        }.build()
        val driver = ItbWalletDriver(
            wallet = Wallet(id = "fixture"),
            client = HttpClient(MockEngine) { engine { addHandler { error("No network request expected") } } },
            trustedOrigin = Url("https://dev-i4mlab.aegean.gr"),
            clientIdTrust = ClientIdTrustConfiguration(),
            authorize = { _, _ -> error("No authorization callback expected") },
        )
        assertFailsWith<AuthorizationRequestResolver.UnsignedAuthorizationRequestNotAllowedException> {
            driver.execute(ItbWalletInteraction.Presentation(url))
        }
    }
}
