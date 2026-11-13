package id.walt.issuer2.openid4vci

import id.walt.commons.persistence.InMemoryPersistence
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.config.*
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.models.*
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.repository.ConfiguredIssuanceSessionRepository
import id.walt.issuer2.service.CredentialOfferService
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.openid4vci.IssuerTypeMetadataCatalogue
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueResult
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssuer
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import id.walt.sdjwt.metadata.type.TypeMetadataValidationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration

class TypeMetadataOfferPreflightTest {
    @Test
    fun `preflight uses effective overrides checks every item and leaves rejected offers uncreated`() = runBlocking {
        val metadata = Issuer2MetadataConfig(
            credentialConfigurations = mapOf("identity" to json("""{"format":"dc+sd-jwt","vct":"vctBaseUrl"}""")),
            sdJwtVcTypeMetadataConfiguration = mapOf("identity" to Json.decodeFromJsonElement<SdJwtVcTypeMetadataDraft04>(json("""
                {"claims":[{"path":["family_name"],"mandatory":true,"sd":"always"}],
                 "schema":{"type":"object","required":["iss"]}}
            """))),
        )
        val data = json("""{"family_name":"Doe"}""")
        val profiles = Issuer2ProfilesConfig(profiles = mapOf("person" to CredentialProfileConfig(
            name = "Person", credentialConfigurationId = "identity",
            issuerKey = json(KeySerialization.serializeKey(JWKKey.generate(KeyType.secp256r1))),
            credentialData = data, selectiveDisclosure = SDMap.generateSDMap(listOf("family_name")),
        )))
        val repository = ConfiguredIssuanceSessionRepository(
            InMemoryPersistence<IssuanceSession>("sessions", Duration.INFINITE),
            InMemoryPersistence<String>("keys", Duration.INFINITE),
        )
        var codes = 0
        val codeIssuer = object : PreAuthorizedCodeIssuer {
            override suspend fun issue(request: PreAuthorizedCodeIssueRequest): PreAuthorizedCodeIssueResult {
                codes++
                return PreAuthorizedCodeIssueResult("code-$codes", Clock.System.now())
            }
        }
        val config = Issuer2ServiceConfig(baseUrl = "https://issuer.example")
        IssuerTypeMetadataCatalogue(config, metadata,
            InMemoryPersistence("types", Duration.INFINITE)).use { catalogue ->
            val service = CredentialOfferService(CredentialProfileService(profiles, metadata), IssuanceSessionService(repository),
                codeIssuer, config, IssuanceNotificationService(), catalogue)
            val invalid = CredentialOfferRuntimeOverrides(selectiveDisclosure = SDMap(emptyMap()))
            assertFailsWith<TypeMetadataValidationException> {
                service.createCredentialOffer(MultiCredentialOfferCreateRequest(
                    listOf(CredentialOfferCredential("person"), CredentialOfferCredential("person", invalid)),
                    AuthenticationMethod.PRE_AUTHORIZED,
                ), "rejected")
            }
            assertEquals(0, codes)
            assertTrue(repository.list().isEmpty())

            // Generated schema requirements and dynamic mapping stay deferred to final preparation.
            val mapping = json("""{"family_name":"<timestamp>"}""")
            val receipt = service.createCredentialOffer(CredentialOfferCreateRequest("person", AuthenticationMethod.PRE_AUTHORIZED,
                runtimeOverrides = CredentialOfferRuntimeOverrides(mapping = mapping)), "accepted")
            val saved = assertNotNull(repository.get(receipt.offerId)).issuanceRequests.single()
            assertEquals(data, saved.credentialData)
            assertEquals(mapping, saved.mapping, "Preflight must not execute dynamic mapping")
            assertEquals(1, codes)
        }
    }

    private fun json(value: String): JsonObject = Json.parseToJsonElement(value).jsonObject
}
