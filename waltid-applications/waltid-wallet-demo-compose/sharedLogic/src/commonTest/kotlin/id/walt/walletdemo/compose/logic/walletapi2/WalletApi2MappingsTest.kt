package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDeepLinkScheme
import id.walt.walletdemo.compose.logic.incomingWalletPageLink
import io.ktor.http.encodeURLParameter
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceGrant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.claimDisplayGroups
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceOutcome
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WalletApi2MappingsTest {
    @Test
    fun futurePresentationAndUnsupportedMethodNamesStayConservative() {
        fun render(presentation: CredentialStatusPresentationDto) = CredentialStatusSnapshotDto(listOf(
            CredentialStatusRecordDto(CredentialStatusReferenceDto(0, "identity"), presentation),
        )).toDemoStatuses().credentials.getValue("identity")
        val future = render(CredentialStatusPresentationDto("FUTURE_STATUS", "NONE", "FRESH", "CHECK", "opaque",
            lastKnown = false, coverage = "COMPLETE"))
        assertTrue(future.hasIssue)
        assertFalse(future.positive)

        val unsupported = render(CredentialStatusPresentationDto("UNSUPPORTED", "UNSUPPORTED", "UNKNOWN", "NONE", "opaque",
            lastKnown = false, references = listOf(CredentialStatusReferencePresentationDto("reference", method = "TOKEN_STATUS_LIST"))))
        val reference = unsupported.references.single()
        assertNull(reference.protocolName, "An unsupported method spelling is not a recognized protocol tag")
        assertEquals("TOKEN_STATUS_LIST", reference.method)
    }

    @Test
    fun overlappingStoreIdsCannotHideAnAdverseResultAndRefreshesKeepTheirScope() {
        val checked = "2026-11-12T09:00:00Z"
        val reference = CredentialStatusReferencePresentationDto("ref", protocolName = "BITSTRING_STATUS_LIST",
            purpose = "revocation", result = "CLEAR", checkedAt = checked, freshUntil = "2026-11-12T11:00:00Z", freshness = "FRESH")
        val clear = CredentialStatusPresentationDto("NOT_REVOKED", "NONE", "FRESH", "CHECK", "clear", lastKnown = false,
            coverage = "COMPLETE", lastCheckedAt = checked, purposes = listOf("revocation"), references = listOf(reference))
        val revoked = clear.copy(kind = "REVOKED", issue = "REVOKED", advisoryRevision = "revoked",
            references = listOf(reference.copy(result = "REVOKED")))
        val wire = CredentialStatusSnapshotDto(listOf(
            CredentialStatusRecordDto(CredentialStatusReferenceDto(0, "same-id"), revoked),
            CredentialStatusRecordDto(CredentialStatusReferenceDto(1, "same-id"), clear),
        ))
        val snapshot = wire.toDemoStatuses()
        assertEquals("NEEDS_REVIEW", snapshot.credentials.getValue("same-id").issue)
        assertEquals("AMBIGUOUS_CREDENTIAL_ID", snapshot.credentials.getValue("same-id").failure)
        assertFailsWith<IllegalArgumentException> {
            val duplicate = CredentialStatusRecordDto(CredentialStatusReferenceDto(0, "same-id"), revoked)
            CredentialStatusSnapshotDto(listOf(duplicate, duplicate))
        }
        val context = CredentialStatusContextDto("active-review", "2026-11-12T10:05:00Z")
        val active = wire.copy(statusContext = context)
        val update = CredentialStatusRefreshResultDto(CredentialStatusReferenceDto(0, "same-id"), "COMPLETED",
            revoked.copy(failure = CredentialStatusFailureDto("reason", "TRANSPORT")), checked, true, context)
        val refreshed = active.withRefreshResults(listOf(update))
        assertEquals(update.presentation, refreshed.credentials[0].presentation)
        assertEquals(clear, refreshed.credentials[1].presentation, "A matching local ID in another store is a separate record")
        assertEquals(listOf(wire.credentials[1]), active.withRefreshResults(listOf(update.copy(outcome = "REMOVED"))).credentials)
        assertFailsWith<IllegalArgumentException> { active.withRefreshResults(listOf(update, update)) }
        assertFailsWith<IllegalArgumentException> { active.withRefreshResults(listOf(update.copy(reference = CredentialStatusReferenceDto(2, "same-id")))) }
        assertFailsWith<IllegalArgumentException> { active.withRefreshResults(listOf(update.copy(statusContext = context.copy(id = "foreign")))) }
    }

    @Test
    fun typedClaimMetadataReachesOfferPreview() {
        val response = walletApi2Json.decodeFromString<ResolveOfferDetailedResponseDto>(
            """
            {
              "credentialIssuer":"https://issuer.example",
              "credentialEndpoint":"https://issuer.example/credential",
              "issuer":{"credentialIssuer":"https://issuer.example"},
              "offeredCredentials":[{
                "configurationId":"pid","format":"dc+sd-jwt",
                "claims":[
                  {"path":["given_name"],"displayName":"Given name"},
                  {"path":["nationalities",null],"mandatory":true,"displayName":"Nationality"},
                  {"path":["other_nationalities",0],"mandatory":false,"displayName":"First nationality"}
                ]
              }]
            }
            """.trimIndent()
        )
        val preview = response.toDemoPreview()
        assertEquals(listOf("pid"), preview.offeredCredentials.map { it.configurationId })
        val offered = preview.offeredCredentials.single()
        assertEquals(listOf("Given name", "Nationality", "First nationality"), offered.claims.map { it.displayName })
        assertEquals(listOf(null, true, false), offered.claims.map { it.mandatory })
        assertEquals(Json.parseToJsonElement("""["nationalities",null]""").jsonArray.toList(), offered.claims[1].path)
        assertEquals(Json.parseToJsonElement("""["other_nationalities",0]""").jsonArray.toList(), offered.claims[2].path)
        assertEquals(listOf("May be included", "Always included", "May be included"), offered.claimDisplayGroups().single().claims.map { it.inclusion })
        assertEquals(
            listOf("Given name", "Nationality", "First nationality"),
            offered.claimDisplayGroups().single().claims.map { it.label },
        )
    }

    @Test
    fun preAuthorizedGrantWhenCodePresent() {
        val response = ResolveOfferDetailedResponseDto(
            credentialIssuer = "https://issuer.example",
            grantType = "authorization_code",
            preAuthorizedCode = "abc",
            credentialEndpoint = "https://issuer.example/credential",
            issuer = OfferIssuerMetadataDto(credentialIssuer = "https://issuer.example"),
        )
        assertEquals(WalletDemoIssuanceGrant.PreAuthorizedCode, response.toDemoGrant())
    }

    @Test
    fun authorizationGrantFromGrantType() {
        val response = ResolveOfferDetailedResponseDto(
            credentialIssuer = "https://issuer.example",
            grantType = "authorization_code",
            credentialEndpoint = "https://issuer.example/credential",
            issuer = OfferIssuerMetadataDto(credentialIssuer = "https://issuer.example"),
        )
        assertEquals(WalletDemoIssuanceGrant.AuthorizationCode, response.toDemoGrant())
        assertTrue(response.toDemoPreview().requiresIssuerAuthentication)
    }

    @Test
    fun persistedAuthorizationIssuanceRoundTrip() {
        val original = PersistedAuthorizationIssuance(
            id = "session-1",
            offerUrl = "openid-credential-offer://issuer.example",
            redirectUri = "http://localhost:8080/",
            did = "did:jwk:test",
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            nonceEndpoint = "https://issuer.example/nonce",
            codeVerifier = "verifier",
            authorizationState = "state-1",
            walletId = "wallet-1",
            credentials = listOf(IssuanceCredentialSelectionDto("UniversityDegree", listOf(
                HolderBindingDto("holder-1", "did:key:one"), HolderBindingDto("holder-2", "did:key:two"),
            ))),
        )
        val decoded = walletApi2Json.decodeFromString<PersistedAuthorizationIssuance>(
            walletApi2Json.encodeToString(original),
        )
        assertEquals(original, decoded)
    }

    @Test
    fun authorizedRestRequestPreservesAllBindingsWithoutASingularConfigurationField() {
        val selections = listOf(IssuanceCredentialSelectionDto("pid", listOf(
            HolderBindingDto("key-1", "did:key:one"), HolderBindingDto("key-2", "did:key:two"),
        )))
        val request = ReceiveAuthorizedCredentialRequestDto(
            code = "code", credentialIssuer = "https://issuer.example", credentialEndpoint = "https://issuer.example/credential",
            redirectUri = "openid://", credentials = selections,
        )
        val json = walletApi2Json.parseToJsonElement(walletApi2Json.encodeToString(request)).jsonObject
        assertFalse("credentialConfigurationId" in json)
        assertEquals(WalletApi2DefaultClientId, json.getValue("clientId").jsonPrimitive.content)
        assertEquals(2, json.getValue("credentials").jsonArray.single().jsonObject.getValue("holderBindings").jsonArray.size)
        assertEquals(request, walletApi2Json.decodeFromString<ReceiveAuthorizedCredentialRequestDto>(json.toString()))
    }

    @Test
    fun authorizationUrlRequestAlwaysSendsTheSameClientId() {
        val request = GenerateAuthorizationUrlRequestDto(
            offerUrl = "openid-credential-offer://issuer.example/?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer",
            redirectUri = "http://localhost:8080/",
            credentialConfigurationIds = listOf("OpenBadgeCredential_jwt_vc_json"),
        )
        val json = walletApi2Json.parseToJsonElement(walletApi2Json.encodeToString(request)).jsonObject
        assertEquals(WalletApi2DefaultClientId, json.getValue("clientId").jsonPrimitive.content)
    }

    @Test
    fun repeatedDatasetResultsDoNotDependOnTheLegacyTransactionMap() {
        val result = walletApi2Json.decodeFromString<ReceiveCredentialResultDto>("""
            {"credentialIds":["saved"],"deferredTransactionIds":null,
             "deferredCredentials":[
               {"credentialConfigurationId":"pid","credentialIdentifier":"first","deferredCredentialId":"handle-1","transactionId":"tx-1","intervalSeconds":5},
               {"credentialConfigurationId":"pid","credentialIdentifier":"second","deferredCredentialId":"handle-2","transactionId":"tx-2","intervalSeconds":7}],
             "failure":{"target":{"credentialConfigurationId":"pid","credentialIdentifier":"third"},"stage":"REQUEST",
               "notAttempted":[{"credentialConfigurationId":"mdl"}]}}
        """.trimIndent())
        assertEquals(listOf("saved"), result.credentialIds)
        assertEquals(listOf("first", "second"), result.deferredCredentials.map { it.credentialIdentifier })
        assertEquals(listOf("tx-1", "tx-2"), result.deferredCredentials.map { it.transactionId })
        val outcome = assertIs<WalletDemoIssuanceOutcome.Failed>(result.toOutcome())
        assertEquals(listOf("saved"), outcome.storedCredentialIds)
        assertEquals(listOf("handle-1", "handle-2"), outcome.deferredCredentials.map { it.id })
        assertEquals(listOf("first", "second"), outcome.deferredCredentials.map { it.credentialIdentifier })
        assertTrue(outcome.offerConsumed)
        assertEquals(1, outcome.failedTargetCount)
        assertEquals(1, outcome.notAttemptedTargetCount)
        assertEquals("third", result.failure?.target?.credentialIdentifier)
        assertEquals(listOf("mdl"), result.failure?.notAttempted?.map { it.credentialConfigurationId })
    }

    @Test
    fun fullFlowStorageRecoveryIsMergedWithIssuerDeferredTargetsWithoutDoubleCountingIds() {
        val result = walletApi2Json.decodeFromString<ReceiveCredentialResultDto>("""
            {"credentialIds":["earlier","saved"],
             "deferredCredentials":[{"credentialConfigurationId":"pid","credentialIdentifier":"first",
               "deferredCredentialId":"issuer-handle","transactionId":"tx","intervalSeconds":5}],
             "failure":{"target":{"credentialConfigurationId":"pid","credentialIdentifier":"second"},"stage":"STORAGE"},
             "storageOutcome":{"sessionId":"local-handle","error":{"code":"STORAGE","message":"Could not save"},
               "storedCredentialIds":["saved"],"deferredCredentials":[
                 {"id":"local-handle","credentialConfigurationId":"pid","credentialIdentifier":"second"}]}}
        """)
        val outcome = assertIs<WalletDemoIssuanceOutcome.Failed>(result.toOutcome())
        assertEquals(listOf("earlier", "saved"), outcome.storedCredentialIds)
        assertEquals(listOf("issuer-handle", "local-handle"), outcome.deferredCredentials.map { it.id })
        assertEquals(listOf("first", "second"), outcome.deferredCredentials.map { it.credentialIdentifier })
        assertNull(outcome.deferredCredentials.last().intervalSeconds)
        assertTrue(outcome.offerConsumed)
    }

    @Test
    fun deferredResumeWireOutcomesPreservePendingAndCommittedProgress() {
        fun decode(json: String) = walletApi2Json.decodeFromString<DeferredIssuanceOutcomeDto>(json).toOutcome()
        val pending = """{"id":"handle","credentialConfigurationId":"pid","credentialIdentifier":"record","intervalSeconds":9}"""
        val deferred = assertIs<WalletDemoIssuanceOutcome.Deferred>(decode("""
            {"type":"deferred","sessionId":"session","storedCredentialIds":["saved"],"credentials":[$pending]}
        """))
        assertEquals(listOf("saved"), deferred.storedCredentialIds)
        assertEquals("handle", deferred.credentials.single().id)
        assertEquals("record", deferred.credentials.single().credentialIdentifier)
        assertEquals(9L, deferred.credentials.single().intervalSeconds)
        val failed = assertIs<WalletDemoIssuanceOutcome.Failed>(decode("""
            {"type":"failed","sessionId":"session","error":{"code":"STORAGE","message":"Could not save"},
             "storedCredentialIds":["saved"],"deferredCredentials":[$pending],
             "failure":{"target":{"credentialConfigurationId":"pid"},"stage":"STORAGE",
               "notAttempted":[{"credentialConfigurationId":"mdl"},{"credentialConfigurationId":"employee"}]}}
        """))
        assertEquals(deferred.storedCredentialIds, failed.storedCredentialIds)
        assertEquals(deferred.credentials, failed.deferredCredentials)
        assertEquals("Could not save", failed.message)
        assertEquals(1, failed.failedTargetCount)
        assertEquals(2, failed.notAttemptedTargetCount)
        assertEquals(listOf("saved", "new"), assertIs<WalletDemoIssuanceOutcome.Stored>(decode("""
            {"type":"stored","sessionId":"session","credentialIds":["saved","new"]}
        """)).credentialIds)
        assertIs<WalletDemoIssuanceOutcome.Cancelled>(decode("""{"type":"cancelled","sessionId":"session"}"""))
    }

    @Test
    fun localSaveHandleWithoutConfigurationKeepsTheIdentityUnknown() {
        val handle = walletApi2Json.decodeFromString<DeferredCredentialHandleDto>("""{"id":"local-save"}""")
        assertNull(handle.toDemoDeferred().credentialConfigurationId)
        assertNull(handle.toDemoDeferred().credentialIdentifier)
    }

    @Test
    fun retainedStatusIsExplicitAndStorageOnlyFailureIsNotReportedAsSuccess() {
        val handle = walletApi2Json.decodeFromString<DeferredCredentialHandleDto>(
            """{"id":"local-save","status":"AWAITING_LOCAL_SAVE","displayMetadataJson":"{}"}""")
        assertEquals(id.walt.walletdemo.compose.logic.WalletDemoContinuationStatus.AwaitingLocalSave, handle.toDemoDeferred().status)
        assertEquals("{}", handle.toDemoDeferred().displayMetadataJson)
        val result = walletApi2Json.decodeFromString<ReceiveCredentialResultDto>("""
            {"credentialIds":[],"storageOutcome":{"type":"failed","error":{"code":"STORAGE_OUTCOME_UNCERTAIN","message":"Saving was interrupted"}}}
        """).toOutcome()
        val failed = assertIs<WalletDemoIssuanceOutcome.Failed>(result)
        assertEquals("Saving was interrupted", failed.message)
        assertEquals(id.walt.walletdemo.compose.logic.WalletDemoIssuanceFailureKind.StorageOutcomeUncertain, failed.kind)
        assertTrue(failed.offerConsumed)
    }

    @Test
    fun emptyDisclosureSelectionStaysEmpty() {
        assertEquals(
            emptyList(),
            emptyList<WalletDemoPresentationDisclosureSelection>().toDisclosureSelectionDtos(),
        )
    }

    @Test
    fun disclosureSelectionPathsRoundTrip() {
        val selected = listOf(
            WalletDemoPresentationDisclosureSelection(
                queryId = "query-1",
                credentialId = "cred-1",
                path = "$.given_name",
            ),
        )
        assertEquals(
            listOf(
                DisclosureSelectionDto(
                    queryId = "query-1",
                    credentialId = "cred-1",
                    path = "$.given_name",
                ),
            ),
            selected.toDisclosureSelectionDtos(),
        )
    }

    @Test
    fun emptyDisclosureSelectionIsEncodedAsEmptyArray() {
        val encoded = walletApi2Json.encodeToString(
            BuildVpTokenRequestDto(
                requestUrl = "https://verifier.example/request",
                selectedDisclosureOptions = emptyList(),
            ),
        )
        assertTrue("\"selectedDisclosureOptions\":[]" in encoded.replace(" ", ""))
    }

    @Test
    fun omittedDisclosureSelectionIsNotEncoded() {
        val encoded = walletApi2Json.encodeToString(
            BuildVpTokenRequestDto(requestUrl = "https://verifier.example/request"),
        )
        assertFalse("selectedDisclosureOptions" in encoded)
    }

}

class WalletDeepLinkSchemeWebTest {
    @Test
    fun pageLinkReadsCredentialOffer() {
        val offer =
            "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer%2Fa+b"
        assertEquals(
            offer,
            incomingWalletPageLink("http://localhost:7106/?link=${offer.encodeURLParameter()}"),
        )
    }

    @Test
    fun pageLinkReadsPresentationRequest() {
        val request = "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest"
        assertEquals(
            request,
            incomingWalletPageLink("http://localhost:7106/?link=${request.encodeURLParameter()}"),
        )
    }

    @Test
    fun pageLinkIgnoresWalletRoutesAndOtherSchemes() {
        assertEquals(
            null,
            incomingWalletPageLink("http://localhost:7106/api/siop/initiateIssuance?credential_offer=abc"),
        )
        assertEquals(null, incomingWalletPageLink("http://localhost:7106/?code=abc&state=1"))
        assertEquals(
            null,
            incomingWalletPageLink("http://localhost:7106/?link=${"https://issuer.example/offer".encodeURLParameter()}"),
        )
        assertEquals(null, incomingWalletPageLink("not a url"))
    }

    @Test
    fun httpsAuthorizationCallbackWithCode() {
        assertEquals(
            WalletDeepLinkScheme.AuthorizationCallback,
            WalletDeepLinkScheme.parse("http://localhost:8080/?code=abc&state=1"),
        )
    }

    @Test
    fun httpWithoutCodeIsIgnored() {
        assertEquals(null, WalletDeepLinkScheme.parse("http://localhost:8080/"))
    }

    @Test
    fun httpsAuthorizationCallbackWithError() {
        assertEquals(
            WalletDeepLinkScheme.AuthorizationCallback,
            WalletDeepLinkScheme.parse("http://localhost:8080/?error=access_denied&state=1"),
        )
    }
}
