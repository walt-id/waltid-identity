package id.waltid.openid4vp.wallet

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.dcql.models.DcqlQuery
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class WalletPresentFunctionality2Test {

    @Test
    fun resolvedAuthorizationRequestBypassesRequestUriResolution() = runTest {
        val result = WalletPresentFunctionality2.walletPresentHandling(
            holderKey = JWKKey.generate(KeyType.Ed25519),
            holderDid = "did:example:holder",
            presentationRequestUrl = Url(
                "openid4vp://authorize?request_uri=https%3A%2F%2Fverifier.invalid%2Frequest.jwt&request_uri_method=post",
            ),
            resolvedAuthorizationRequest = ResolvedAuthorizationRequest.Plain(
                AuthorizationRequest(
                    responseMode = OpenID4VPResponseMode.FRAGMENT,
                    redirectUri = "https://wallet.example/callback",
                    nonce = "nonce-from-preview",
                    dcqlQuery = DcqlQuery(credentials = emptyList()),
                )
            ),
            selectCredentialsForQuery = { emptyMap() },
            holderPoliciesToRun = null,
            runPolicies = null,
        ).getOrThrow()

        assertEquals("https://wallet.example/callback#vp_token=%7B%7D", result.getUrl)
    }
}
