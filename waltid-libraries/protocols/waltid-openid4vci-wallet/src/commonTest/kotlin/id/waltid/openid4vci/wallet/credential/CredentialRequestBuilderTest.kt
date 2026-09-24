package id.waltid.openid4vci.wallet.credential

import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.prooftypes.Proofs
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import kotlinx.serialization.json.*
import kotlin.test.*

class CredentialRequestBuilderTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun authorizationServer(details: Set<String>? = setOf("openid_credential")) =
        AuthorizationServerMetadata(issuer = "https://issuer.example", authorizationEndpoint = "https://issuer.example/authorize",
            tokenEndpoint = "https://issuer.example/token", responseTypesSupported = setOf("code"), authorizationDetailsTypesSupported = details)
    private fun metadata(batch: Int? = null) = json.decodeFromString<CredentialIssuerMetadata>(
        """{"credential_issuer":"https://issuer.example","credential_endpoint":"https://issuer.example/credential",
          ${batch?.let { "\"batch_credential_issuance\":{\"batch_size\":$it}," } ?: ""}
          "credential_configurations_supported":{
            "identity":{"format":"dc+sd-jwt","vct":"identity","scope":"identity_scope"},
            "badge":{"format":"jwt_vc_json","scope":"badge_scope","credential_definition":{"type":["VerifiableCredential"]}}
          }}"""
    )

    @Test fun singleAndBatchUseTheSameProofCollection() {
        for (count in listOf(1, 2, 5)) {
            val request = CredentialRequestBuilder.build(CredentialIssuanceTarget("identity"),
                Proofs(jwt = List(count) { "proof-$it" }))
            assertEquals(count, request["proofs"]!!.jsonObject["jwt"]!!.jsonArray.size)
            assertEquals("identity", request["credential_configuration_id"]!!.jsonPrimitive.content)
            assertNull(request["credential_identifier"])
        }
    }

    @Test fun preAuthorizedTokenRequestsIdentifiersForEachSelectedConfiguration() {
        val parameters = CredentialRequestBuilder.preAuthorizedTokenParameters(metadata(), listOf("identity", "badge", "identity"), authorizationServer())
        assertNull(parameters["scope"])
        val details = json.parseToJsonElement(assertNotNull(parameters["authorization_details"])).jsonArray
        assertEquals(listOf("identity", "badge"), details.map { it.jsonObject.getValue("credential_configuration_id").jsonPrimitive.content })
        assertTrue(details.all { it.jsonObject.getValue("type").jsonPrimitive.content == "openid_credential" })
    }

    @Test fun automaticallyUsesAdvertisedScopesWhenCredentialAuthorizationDetailsAreNotAdvertised() {
        val parameters = CredentialRequestBuilder.preAuthorizedTokenParameters(metadata(), listOf("identity", "badge"), authorizationServer(null))
        assertEquals(mapOf("scope" to "identity_scope badge_scope"), parameters)
    }

    @Test fun unrelatedAuthorizationDetailsTypesDoNotEnableCredentialAuthorizationDetails() {
        assertEquals("identity_scope", CredentialRequestBuilder.authorizationScope(metadata(), listOf("identity"), authorizationServer(setOf("payment"))))
    }

    @Test fun configurationsWithoutScopesRequireAdvertisedCredentialAuthorizationDetails() {
        val withoutScopes = metadata().copy(credentialConfigurationsSupported = metadata().credentialConfigurationsSupported.mapValues {
            it.value.copy(scope = null)
        })
        assertNull(CredentialRequestBuilder.authorizationScope(withoutScopes, listOf("identity", "badge"), authorizationServer()))
        assertFailsWith<IllegalArgumentException> {
            CredentialRequestBuilder.authorizationScope(withoutScopes, listOf("identity", "badge"), authorizationServer(null))
        }
        val mixed = metadata().copy(credentialConfigurationsSupported = metadata().credentialConfigurationsSupported.mapValues {
            if (it.key == "badge") it.value.copy(scope = null) else it.value
        })
        assertFailsWith<IllegalArgumentException> {
            CredentialRequestBuilder.authorizationScope(mixed, listOf("identity", "badge"), authorizationServer(null))
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialRequestBuilder.authorizationScope(metadata(), listOf("missing"), authorizationServer())
        }
    }

    @Test fun scopeRequestedTokensStillUseReturnedDatasetIdentifiers() {
        assertEquals("identity_scope", CredentialRequestBuilder.authorizationScope(metadata(), listOf("identity"), authorizationServer(null)))
        assertEquals(listOf(CredentialIssuanceTarget("identity", "dataset-a"), CredentialIssuanceTarget("identity", "dataset-b")),
            CredentialRequestBuilder.resolveTargets(metadata(), listOf("identity"),
                listOf(AuthorizationDetail("openid_credential", "identity", listOf("dataset-a", "dataset-b"))), "identity_scope"))
    }

    @Test fun identifierReplacesConfigurationSelector() {
        val request = CredentialRequestBuilder.build(CredentialIssuanceTarget("identity", "issuer-issued-dataset-id"))
        assertEquals("issuer-issued-dataset-id", request["credential_identifier"]!!.jsonPrimitive.content)
        assertNull(request["credential_configuration_id"])
        assertNull(request["proofs"])
    }

    @Test fun rejectsEmptyProofCollectionsButAllowsRepeatedProofs() {
        assertFailsWith<IllegalArgumentException> { CredentialRequestBuilder.build(CredentialIssuanceTarget("identity"), Proofs(jwt = emptyList())) }
        assertFailsWith<IllegalArgumentException> { CredentialRequestBuilder.build(CredentialIssuanceTarget("identity"), Proofs(jwt = listOf(""))) }
        assertEquals(2, CredentialRequestBuilder.build(CredentialIssuanceTarget("identity"), Proofs(jwt = listOf("same", "same")))
            ["proofs"]!!.jsonObject["jwt"]!!.jsonArray.size)
    }

    @Test fun batchIsOptInAndBoundedByIssuerMetadata() {
        CredentialRequestBuilder.validateBatchSize(metadata(), 1)
        assertFailsWith<IllegalArgumentException> { CredentialRequestBuilder.validateBatchSize(metadata(), 2) }
        assertFailsWith<IllegalArgumentException> { CredentialRequestBuilder.validateBatchSize(metadata(5), 0) }
        CredentialRequestBuilder.validateBatchSize(metadata(5), 5)
        assertFailsWith<IllegalArgumentException> { CredentialRequestBuilder.validateBatchSize(metadata(5), 6) }
    }

    @Test fun expandsTokenDatasetsAcrossDifferentFormats() {
        val details = json.decodeFromString<List<AuthorizationDetail>>(
            """[{"type":"openid_credential","credential_configuration_id":"identity","credential_identifiers":["alice","bob"]},
                {"type":"openid_credential","credential_configuration_id":"badge","credential_identifiers":["badge-1"]}]""")
        assertEquals(listOf(CredentialIssuanceTarget("identity", "alice"), CredentialIssuanceTarget("identity", "bob"),
            CredentialIssuanceTarget("badge", "badge-1")),
            CredentialRequestBuilder.resolveTargets(metadata(), listOf("identity", "badge"), details))
        assertEquals(listOf(CredentialIssuanceTarget("badge", "badge-1")),
            CredentialRequestBuilder.resolveTargets(metadata(), listOf("badge"), details))
    }

    @Test fun scopesSelectConfigurationsWithoutInventingDatasetIdentifiers() {
        assertEquals(listOf(CredentialIssuanceTarget("badge")),
            CredentialRequestBuilder.resolveTargets(metadata(), listOf("identity", "badge"), null, "openid badge_scope"))
        assertFailsWith<IllegalArgumentException> {
            CredentialRequestBuilder.resolveTargets(metadata(), listOf("identity"), null, "badge_scope")
        }
    }

    @Test fun tokenIdentifiersAreMandatoryInsideCredentialAuthorizationDetails() {
        val details = json.decodeFromString<List<AuthorizationDetail>>(
            """[{"type":"openid_credential","credential_configuration_id":"identity"}]""")
        assertFailsWith<IllegalArgumentException> {
            CredentialRequestBuilder.resolveTargets(metadata(), listOf("identity"), details)
        }
    }
}
