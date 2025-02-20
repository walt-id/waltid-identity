package id.walt.oid4vc.openID4VC

import id.walt.credentials.CredentialBuilder
import id.walt.credentials.CredentialBuilderType
import id.walt.credentials.issuance.Issuer.baseIssue
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.testCredentialIssuanceIsolatedFunctionsAuthCodeFlowRedirectWithCode
import id.walt.oid4vc.testIsolatedFunctionsCreateCredentialOffer
import id.walt.oid4vc.util.JwtUtils
import id.walt.oid4vc.util.randomUUID
import id.walt.policies.policies.JwtSignaturePolicy
import id.walt.sdjwt.SDJwt
import io.ktor.http.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertDoesNotThrow
import java.net.URLEncoder
import kotlin.test.*

class TestOID4VCI {
    val ISSUER_BASE_URL = "https://example.com"
    val CREDENTIAL_OFFER_BASE_URL = "openid-credential-offer://test"

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() = runTest {
            DidService.minimalInit()
            assertContains(DidService.registrarMethods.keys, "jwk")
        }
    }

    val ISSUER_TOKEN_KEY = runBlocking { JWKKey.generate(KeyType.RSA) }
    val ISSUER_DID_KEY = runBlocking { JWKKey.generate(KeyType.Ed25519) }
    val ISSUER_DID = runBlocking { DidService.registerByKey("jwk", ISSUER_DID_KEY).did }
    val WALLET_CLIENT_ID = "test-client"
    val WALLET_REDIRECT_URI = "http://blank"
    val WALLET_KEY =
        "{\"kty\":\"EC\",\"d\":\"uD-uxub011cplvr5Bd6MrIPSEUBsgLk-C1y3tnmfetQ\",\"use\":\"sig\",\"crv\":\"secp256k1\",\"kid\":\"48d8a34263cf492aa7ff61b6183e8bcf\",\"x\":\"TKaQ6sCocTDsmuj9tTR996tFXpEcS2EJN-1gOadaBvk\",\"y\":\"0TrIYHcfC93VpEuvj-HXTnyKt0snayOMwGSJA1XiDX8\"}"
    val WALLET_DID = "did:jwk:eyJrdHkiOiJFQyIsInVzZSI6InNpZyIsImNydiI6InNlY3AyNTZrMSIsImtpZCI6IjQ4ZDhhMzQyNjNjZjQ5MmFhN2ZmNjFiNjE4M2U4YmNmIiwieCI6IlRLYVE2c0NvY1REc211ajl0VFI5OTZ0RlhwRWNTMkVKTi0xZ09hZGFCdmsiLCJ5IjoiMFRySVlIY2ZDOTNWcEV1dmotSFhUbnlLdDBzbmF5T013R1NKQTFYaURYOCJ9"

    @Test
    fun testFlow() = runTest {

        //
        // Issuer Creates Metadata
        //
        val credentialSupported = mapOf(
                "VerifiableId" to CredentialSupported(
                    format = CredentialFormat.jwt_vc_json,
                    cryptographicBindingMethodsSupported = setOf("did:key", "jwk"),
                    credentialSigningAlgValuesSupported = setOf("ES256K"),
                    credentialDefinition = CredentialDefinition(
                        type = listOf("VerifiableCredential", "VerifiableId")
                    ),
                    customParameters = mapOf("foo" to JsonPrimitive("bar"))
                ),
                "VerifiableDiploma" to CredentialSupported(
                    format = CredentialFormat.jwt_vc_json,
                    cryptographicBindingMethodsSupported = setOf("did:key", "cose_key", "jwk"),
                    credentialSigningAlgValuesSupported = setOf("ES256K"),
                    credentialDefinition = CredentialDefinition(
                        type = listOf("VerifiableCredential", "VerifiableAttestation", "VerifiableDiploma")
                    )
                )
        )

        val metadata = OpenID4VCI.createDefaultProviderMetadata(
            baseUrl = ISSUER_BASE_URL,
            credentialSupported = credentialSupported,
            version = OpenID4VCIVersion.DRAFT13
        )

        // Validate Metadata
        val issuerMetadata = metadata.castOrNull<OpenIDProviderMetadata.Draft13>() ?: throw IllegalArgumentException("Invalid Metadata")

        assertDoesNotThrow { validateIssuerMetadata(issuerMetadata) }

        //
        // Issuer Creates Offer
        //
        val credOffer = CredentialOffer.Draft13.Builder(ISSUER_BASE_URL)
            .addOfferedCredential("VerifiableId")
            .addPreAuthorizedCodeGrant("test-pre-auth-code")
            .build()

        assertDoesNotThrow { validateCredentialOffer(credOffer, issuerMetadata)}

        val credOfferURIByValue = OpenID4VCI.getCredentialOfferRequestUrl(
            credOffer = credOffer
        )

        assertDoesNotThrow { validateCredentialOfferURI(credOfferURIByValue, credOffer) }

        // Validate Offer URI By Reference


        //
        // Wallet Gets Offer
        //


    }


    private fun validateCredentialOfferURI(credOfferURI: String, credentialOffer: CredentialOffer.Draft13? = null, credentialOfferUri: String? = null) {
        val expectedURL = when {
            credentialOffer != null -> {
                val expectedEncodedOffer = URLEncoder.encode(credentialOffer.toJSONString(), "UTF-8")
                "openid-credential-offer://?credential_offer=$expectedEncodedOffer"
            }
            credentialOfferUri != null -> {
                val expectedEncodedUri = URLEncoder.encode(credentialOfferUri, "UTF-8")
                "openid-credential-offer://?credential_offer_uri=$expectedEncodedUri"
            }
            else -> throw IllegalArgumentException("❌ Either credentialOffer or credentialOfferUri must be provided!")
        }

        assertEquals(expectedURL, credOfferURI, "❌ Credential Offer URI does not match expected output")
    }

    private fun validateCredentialOffer(
        offer: CredentialOffer.Draft13,
        metadata: OpenIDProviderMetadata.Draft13
    ) {
        assertNotNull(offer.credentialIssuer)
        assertNotNull(offer.credentialConfigurationIds)
        assertNotNull(offer.grants)

        assertEquals(ISSUER_BASE_URL, offer.credentialIssuer)
        assertEquals("VerifiableId", offer.credentialConfigurationIds.first())
        assertContains(offer.grants.keys, GrantType.pre_authorized_code.value)
        assertFalse(offer.grants.keys.contains(GrantType.authorization_code.value))

        assertEquals(metadata.credentialIssuer, offer.credentialIssuer)

        val matchingCredentialConfigurationId = metadata.credentialConfigurationsSupported
            ?.keys
            ?.find { it == offer.credentialConfigurationIds.first() }
            ?: throw AssertionError("No matching credential found for the credentialConfigurationId '${offer.credentialConfigurationIds.first()}'.")

        assertEquals(
            CredentialFormat.jwt_vc_json,
            metadata.credentialConfigurationsSupported!![matchingCredentialConfigurationId]!!.format
        )

        assertNotNull(offer.grants[GrantType.pre_authorized_code.value]?.preAuthorizedCode)
        assertEquals("test-pre-auth-code", offer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode)
        assertNull(offer.grants[GrantType.pre_authorized_code.value]?.txCode)
        assertNull(offer.grants[GrantType.pre_authorized_code.value]?.interval)
        assertNull(offer.grants[GrantType.pre_authorized_code.value]?.authorizationServer)
        assertNull(offer.grants[GrantType.pre_authorized_code.value]?.issuerState)

    }

    private fun validateIssuerMetadata(metadata: OpenIDProviderMetadata.Draft13) {
        val credentialIssuerUrl = metadata.credentialIssuer ?: throw IllegalArgumentException("credentialIssuerUrl cannot be null")

        val expectedResponseTypes = setOf(
            ResponseType.Code.value,
            ResponseType.VpToken.value,
            ResponseType.IdToken.value
        )

        // Validate OID4VCI
        OpenID4VCI.validateCredentialIssuerUrl(credentialIssuerUrl)

        require(isValidHttpsUrl(credentialIssuerUrl)) { "Invalid credential_issuer: must be a valid HTTPS URL" }

        assertEquals(ISSUER_BASE_URL, credentialIssuerUrl)
        assertEquals(ISSUER_BASE_URL, metadata.issuer)

        assertNull(metadata.authorizationServers)

        assertNotNull(metadata.responseTypesSupported, "responseTypesSupported is missing")
        assertEquals(expectedResponseTypes, metadata.responseTypesSupported, "responseTypesSupported does not match expected values")

        validateEndpoint(credentialIssuerUrl, metadata.credentialEndpoint, "credential")
        validateEndpoint(credentialIssuerUrl, metadata.batchCredentialEndpoint, "batch_credential")
        validateEndpoint(credentialIssuerUrl, metadata.deferredCredentialEndpoint, "credential_deferred")

        assertNotNull(metadata.credentialConfigurationsSupported)

        metadata.credentialConfigurationsSupported!!.forEach { (id, credentialsSupported) ->
            assertNotNull(credentialsSupported.format) { "credential_configurations_supported[$id].format is required" }

            assertNotNull(credentialsSupported.cryptographicBindingMethodsSupported)

            assertNotNull(credentialsSupported.credentialSigningAlgValuesSupported)
            assertEquals(setOf("ES256K"), credentialsSupported.credentialSigningAlgValuesSupported)

            assertNotNull(credentialsSupported.credentialDefinition?.type)

            when (id) {
                "VerifiableId" -> {
                    assertEquals(setOf("did:key", "jwk"), credentialsSupported.cryptographicBindingMethodsSupported)
                    assertEquals( listOf("VerifiableCredential", "VerifiableId"), credentialsSupported.credentialDefinition!!.type)
                }
                "VerifiableDiploma" -> {
                    assertEquals(setOf("did:key", "cose_key", "jwk"), credentialsSupported.cryptographicBindingMethodsSupported)
                    assertEquals(listOf("VerifiableCredential", "VerifiableAttestation", "VerifiableDiploma"), credentialsSupported.credentialDefinition!!.type)
                }
            }
        }

        OpenID4VCI.validateCryptographicBindingMethods(metadata.credentialConfigurationsSupported!!)

        // Validate OAuth
        assertContains(metadata.grantTypesSupported, GrantType.authorization_code)
        assertContains(metadata.grantTypesSupported, GrantType.pre_authorized_code)

        assertNotNull(metadata.jwksUri)

        validateEndpoint(credentialIssuerUrl, metadata.authorizationEndpoint, "authorize")
        validateEndpoint(credentialIssuerUrl, metadata.tokenEndpoint, "token")
        validateEndpoint(credentialIssuerUrl, metadata.jwksUri, "jwks")

        // TODO: continue with others.
    }

    private fun validateEndpoint(
        baseUrl: String,
        endpoint: String?,
        endpointName: String,
        required: Boolean = false
    ) {
        if (required) require(endpoint != null) { "Missing required field: $endpointName" }

        endpoint?.let {
            require(isValidHttpsUrl(it)) { "Invalid $endpointName: must be a valid HTTPS URL" }

            val expectedUrl = "$baseUrl/$endpointName"
            require(it == expectedUrl) { "$endpointName must be exactly $expectedUrl" }
        }
    }

    private fun isValidHttpsUrl(url: String): Boolean {
        return try {
            val parsedUrl = Url(url)
            (parsedUrl.protocol.name == "https" || parsedUrl.protocol.name == "http") && parsedUrl.host.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

}
