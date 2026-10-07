package id.walt.wallet2.handlers

import id.walt.openid4vci.proofs.Proofs
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReleasedProofApiTest {
    @Test
    fun releasedSingleProofConstructionCopyAndJsonRemainUsable() {
        // Positional construction and destructuring retain the released field order.
        val request = SignProofRequest(Url("https://issuer.example"), "identity", "nonce")
        val (issuer, configuration, nonce) = request.copy(clientId = "client")
        assertEquals(request.issuerUrl, issuer)
        assertEquals("identity", configuration)
        assertEquals("nonce", nonce)

        val original = Json.decodeFromString<SignProofResult>("""{"proofJwt":"released-proof"}""")
        val (proof) = original.copy()
        assertEquals("released-proof", proof)
        assertEquals(SignProofResult("replacement"), original.copy(proofJwt = "replacement"))
        assertEquals("""{"proofJwt":"released-proof"}""", Json.encodeToString(original))
    }

    @Test
    fun pluralProofWireRetainsTheReleasedSingleShapeAndRejectsAmbiguity() {
        // encodeDefaults=true matches servers that normally include nullable defaults.
        val json = Json { encodeDefaults = true }
        for (jwt in listOf(listOf("one"), listOf("one", "two"))) {
            val result = SignProofsResult(Proofs(jwt = jwt))
            val encoded = json.encodeToString(result)
            assertEquals(result, json.decodeFromString<SignProofsResult>(encoded))
            if (jwt.size == 1) assertEquals(SignProofResult("one"), json.decodeFromString<SignProofResult>(encoded))
        }
        for (invalid in listOf("{}", """{"proofJwt":"one","proofs":{"jwt":["two"]}}""",
            """{"proofs":{"jwt":[]}}""", """{"proofJwt":""}""")) {
            assertFailsWith<IllegalArgumentException> { json.decodeFromString<SignProofsResult>(invalid) }
        }
    }

    @Test
    fun releasedCopyOverloadsCarryBatchAndAuthorizationFieldsForward() {
        val token = RequestTokenResult("access", 10, "DPoP")
        val (_, expiresIn, tokenType) = token.copy(accessToken = "renewed")
        assertEquals(10L, expiresIn)
        assertEquals("DPoP", tokenType)

        val request = RequestTokenRequest(Url("https://issuer.example/token"), "code",
            credentialIssuer = "https://issuer.example", credentialConfigurationIds = listOf("identity"))
        assertEquals(request.credentialConfigurationIds, request.copy(txCode = "1234").credentialConfigurationIds)

        val offer = ResolveOfferResult("https://issuer.example", listOf("identity"), "pre-authorized_code", null,
            false, Url("https://issuer.example/credential"), listOf("identity"),
            tokenEndpoint = request.tokenEndpoint)
        assertEquals(request.tokenEndpoint, offer.component8())

        val preview = WalletIssuanceOfferPreview(WalletIssuanceGrant.PRE_AUTHORIZED_CODE,
            WalletIssuanceIssuerPreview("https://issuer.example", null, null, null, null,
                WalletIssuanceMetadataProvenance.Unsigned), emptyList(), null)
        assertEquals(WalletIssuanceGrant.PRE_AUTHORIZED_CODE, preview.component1())

    }

    @Test
    fun releasedRequestCopyPreservesSelectionsAndSenderConstraints() {
        val offer = Url("openid-credential-offer://?credential_offer_uri=https://issuer.example/offer")
        val selections = listOf(WalletCredentialSelection("identity",
            holderBindings = listOf(CredentialHolderBinding("first"), CredentialHolderBinding("second"))))
        val request = ReceiveCredentialRequest(offerUrl = offer, credentials = selections)
        assertEquals(offer, request.component1())
        assertEquals(selections, request.copy(txCode = "1234").credentials)
        val preview = ReceiveCredentialFromPreviewRequest(IssuancePreviewHandle("reviewed"), credentials = selections)
        assertEquals(preview.previewHandle, preview.component1())
        assertEquals(selections, preview.copy(txCode = "1234").credentials)

        val endpoint = Url("https://issuer.example/credential")
        val proofs = Proofs(jwt = listOf("first-proof", "second-proof"))
        val fetch = FetchCredentialRequest(endpoint, "access", "identity", proofs = proofs,
            holderBindings = selections.single().holderBindings, credentialIdentifier = "dataset",
            tokenType = "DPoP", dpopKeyId = "sender")
        val fetchCopy = fetch.copy(label = "updated")
        assertEquals(fetch.copy(label = "updated", proofs = proofs), fetchCopy)
        assertEquals(fetch.proofJwt, fetchCopy.component4())
        // A released copy cannot silently clear a plural proof to make this invalid combination work.
        assertFailsWith<IllegalArgumentException> { fetch.copy(proofJwt = "legacy") }

        val poll = PollDeferredRequest(endpoint, "transaction", "access", proofRequired = true,
            holderBindings = selections.single().holderBindings, credentialIdentifier = "dataset",
            tokenType = "DPoP", dpopKeyId = "sender")
        assertEquals(endpoint, poll.component1())
        assertEquals(poll.copy(label = "updated", proofRequired = true), poll.copy(label = "updated"))
        val session = WalletIssuanceSessionRequest(credentialIssuer = "https://issuer.example",
            credentialConfigurationIds = listOf("identity"))
        assertEquals(session.copy(clientId = "updated", credentialIssuer = session.credentialIssuer),
            session.copy(clientId = "updated"))
    }

}
