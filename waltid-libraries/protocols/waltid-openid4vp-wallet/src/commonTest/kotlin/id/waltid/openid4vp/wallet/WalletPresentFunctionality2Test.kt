package id.waltid.openid4vp.wallet

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
            transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
        ).getOrThrow()

        assertEquals("https://wallet.example/callback#vp_token=%7B%7D", result.getUrl)
    }

    @Test
    fun immediateFlowSelectionRequiresAllQueriesWhenCredentialSetsAreAbsent() {
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery("identity", CredentialFormat.JWT_VC_JSON, meta = NoMeta),
                CredentialQuery("address", CredentialFormat.JWT_VC_JSON, meta = NoMeta),
            )
        )
        val request = AuthorizationRequest(dcqlQuery = query)
        val identity = match("identity")
        val address = match("address")

        assertFailsWith<IllegalArgumentException> {
            WalletPresentFunctionality2.validateMatchedCredentialSelection(request, emptyMap())
        }
        assertFailsWith<IllegalArgumentException> {
            WalletPresentFunctionality2.validateMatchedCredentialSelection(
                request,
                mapOf("identity" to listOf(identity)),
            )
        }
        WalletPresentFunctionality2.validateMatchedCredentialSelection(
            request,
            mapOf("identity" to listOf(identity), "address" to listOf(address)),
        )
    }

    private fun match(queryId: String): DcqlMatcher.DcqlMatchResult {
        val query = CredentialQuery(queryId, CredentialFormat.JWT_VC_JSON, meta = NoMeta)
        return DcqlMatcher.DcqlMatchResult(
            credential = RawDcqlCredential(queryId, "jwt_vc_json", buildJsonObject {}),
            selectedDisclosures = null,
            originalQuery = query,
        )
    }
}
