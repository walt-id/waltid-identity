package id.waltid.openid4vp.wallet

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class WalletRejectHandlingTest {

    @Test
    fun fragmentErrorResponse_includesErrorDescriptionAndState() = runTest {
        val result = WalletPresentFunctionality2.walletRejectHandling(
            authorizationRequest = AuthorizationRequest(
                redirectUri = "https://wallet.example/callback",
                responseMode = OpenID4VPResponseMode.FRAGMENT,
                state = "state-123",
            ),
            error = "access_denied",
            errorDescription = "User denied",
        ).getOrThrow()

        assertEquals(
            "https://wallet.example/callback#error=access_denied&error_description=User+denied&state=state-123",
            result.getUrl,
        )
    }
}
