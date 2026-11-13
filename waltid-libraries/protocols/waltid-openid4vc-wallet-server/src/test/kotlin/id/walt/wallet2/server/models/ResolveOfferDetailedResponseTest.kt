package id.walt.wallet2.server.models

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.ClaimDescription
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialMetadata
import id.walt.wallet2.handlers.ResolveOfferResult
import id.walt.wallet2.handlers.WalletOfferResolution
import id.waltid.openid4vci.wallet.metadata.OfferedCredentialResolver
import id.waltid.openid4vci.wallet.metadata.ResolvedCredentialIssuerMetadata
import io.ktor.http.Url
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveOfferDetailedResponseTest {

    private fun configuration(format: CredentialFormat, vararg paths: List<JsonElement>) = CredentialConfiguration(
        format = format,
        credentialMetadata = CredentialMetadata(
            display = listOf(CredentialDisplay(name = "Card", locale = "en")),
            claims = paths.map { ClaimDescription(path = it) },
        ),
    )

    @Test
    fun contradictoryClaimDescriptionsAreLeftOutOfTheOfferPreview() {
        val configurations = mapOf(
            "pid" to configuration(
                CredentialFormat.SD_JWT_VC,
                listOf(JsonPrimitive("nationalities"), JsonNull),
                listOf(JsonPrimitive("nationalities"), JsonPrimitive(1)),
            ),
            "mdl" to configuration(CredentialFormat.MSO_MDOC, listOf(JsonPrimitive("org.iso.18013.5.1"), JsonPrimitive("family_name"))),
        )
        val resolution = WalletOfferResolution(
            summary = ResolveOfferResult(
                credentialIssuer = "https://issuer.example",
                credentialConfigurationIds = configurations.keys.toList(),
                grantType = "urn:ietf:params:oauth:grant-type:pre-authorized_code",
                txCodeRequired = false,
                credentialEndpoint = Url("https://issuer.example/credential"),
                offeredCredentials = configurations.keys.toList(),
            ),
            resolvedIssuerMetadata = ResolvedCredentialIssuerMetadata.Unsigned(
                CredentialIssuerMetadata(
                    credentialIssuer = "https://issuer.example",
                    credentialEndpoint = "https://issuer.example/credential",
                    credentialConfigurationsSupported = configurations,
                )
            ),
            offeredCredentials = configurations.map { (id, configuration) ->
                OfferedCredentialResolver.ResolvedCredentialOffer(id, configuration)
            },
            transactionCode = null,
        )

        val offered = resolution.toDetailedResponse().offeredCredentials.associateBy { it.configurationId }

        assertEquals(emptyList(), offered.getValue("pid").claims)
        assertEquals("Card", offered.getValue("pid").display?.name)
        assertEquals(
            listOf(listOf(JsonPrimitive("org.iso.18013.5.1"), JsonPrimitive("family_name"))),
            offered.getValue("mdl").claims.map { it.path },
        )
    }
}
