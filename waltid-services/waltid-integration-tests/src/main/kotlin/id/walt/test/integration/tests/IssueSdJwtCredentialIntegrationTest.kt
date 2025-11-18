@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.util.JwtUtils
import id.walt.sdjwt.SDJwtVC
import id.walt.test.integration.expectLooksLikeJwt
import id.walt.test.integration.loadJsonResource
import id.walt.test.integration.toSdMap
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

private val sdjwtCredential = IssuanceRequest(
    issuerKey = loadJsonResource("issuance/key.json"),
    issuerDid = loadResource("issuance/did.txt"),
    credentialConfigurationId = "OpenBadgeCredential_jwt_vc_json", //format= jwt_vc_json (W3c)
    credentialData = loadJsonResource("issuance/openbadgecredential.json"),
    mapping = loadJsonResource("issuance/mapping.json"),
    selectiveDisclosure = loadJsonResource("issuance/disclosure.json").toSdMap()
)

@TestMethodOrder(OrderAnnotation::class)
class IssueSdJwtCredentialIntegrationTest : AbstractIntegrationTest() {

    companion object {
        var newCredential: WalletCredential? = null
    }

    @Order(0)
    @Test
    fun shouldIssueCredential() = runTest {
        val offerUrl = issuerApi.issueSdJwtCredential(sdjwtCredential)
        defaultWalletApi.resolveCredentialOffer(offerUrl)
        newCredential = defaultWalletApi.claimCredential(offerUrl).let {
            assertEquals(1, it.size)
            it.first()
        }
        assertEquals(defaultWalletApi.getWallet().id, newCredential!!.wallet)
        assertNotNull(newCredential?.parsedDocument) { parsedDocument ->
            // issuerDid should be set by data function <issuerDid>
            assertEquals(sdjwtCredential.issuerDid, parsedDocument["issuer"]?.jsonObject["id"]?.jsonPrimitive?.content)
            // subject did should e set by data function <subjectDid>
            assertEquals(
                defaultWalletApi.getDefaultDid().did,
                parsedDocument["credentialSubject"]?.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            )
            // must contain 1 selective disclosure
            assertEquals(1, parsedDocument["_sd"]?.jsonArray?.size)
        }
        assertContains(JwtUtils.parseJWTPayload(newCredential!!.document).keys, JwsSignatureScheme.JwsOption.VC)
    }

    @Order(1)
    @Test
    fun shouldVerifyCredentialSuccessfully() = runTest {
        assertNotNull(newCredential, "Credential ID should not be null - Test Order ??")
        val verificationUrl =
            verifierApi.verify(loadResource("presentation/openbadge-credential-name-field-is-string-schema-validation-policy-presentation-request.json.json"))

        val verificationId = Url(verificationUrl).parameters.getOrFail("state")

        val resolvedPresentationOfferString =
            defaultWalletApi.resolvePresentationRequest(verificationUrl)
        val presentationDefinition =
            Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")

        verifierApi.getSession(verificationId).also {
            assertEquals(
                PresentationDefinition.fromJSONString(presentationDefinition),
                it.presentationDefinition
            )
        }

        defaultWalletApi.matchCredentialsForPresentationDefinition(presentationDefinition).also {
            assertEquals(1, it.size)
            assertEquals(newCredential!!.id, it.first().id)
        }

        defaultWalletApi.unmatchedCredentialsForPresentationDefinition(presentationDefinition).also {
            assertTrue(it.isEmpty())
        }
        defaultWalletApi.usePresentationRequest(
            request = UsePresentationRequest(
                did = defaultWalletApi.getDefaultDid().did,
                presentationRequest = resolvedPresentationOfferString,
                selectedCredentials = listOf(newCredential!!.id),
                disclosures = newCredential!!.disclosures?.let { mapOf(newCredential!!.id to listOf(it)) }
            )
        )

        verifierApi.getSession(verificationId).also {
            assertNotNull(
                it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt(),
                "Received no valid token response!"
            )
            assertNotNull(
                it.tokenResponse?.presentationSubmission,
                "should have a presentation submission after submission"
            )
            assertEquals(true, it.verificationResult, "overall verification should be valid")
            it.policyResults.let {
                assertNotNull(it, "policyResults should be available after running policies")
                assertTrue(it.size > 1, "no policies have run")
            }
        }
    }

    @Order(1)
    @Test
    fun shouldVerifyCredentialWithErrorBecauseShemaPolicyFails() = runTest {
        assertNotNull(newCredential, "Credential ID should not be null - Test Order ??")
        val verificationUrl =
            verifierApi.verify(loadResource("presentation/openbadge-credential-name-field-is-object-schema-validation-policy-presentation-request.json"))

        val verificationId = Url(verificationUrl).parameters.getOrFail("state")

        val resolvedPresentationOfferString =
            defaultWalletApi.resolvePresentationRequest(verificationUrl)
        val presentationDefinition =
            Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")

        verifierApi.getSession(verificationId).also {
            assertEquals(
                PresentationDefinition.fromJSONString(presentationDefinition),
                it.presentationDefinition
            )
        }

        defaultWalletApi.matchCredentialsForPresentationDefinition(presentationDefinition).also {
            assertEquals(1, it.size)
            assertEquals(newCredential!!.id, it.first().id)
        }

        defaultWalletApi.unmatchedCredentialsForPresentationDefinition(presentationDefinition).also {
            assertTrue(it.isEmpty())
        }
        val error = defaultWalletApi.usePresentationRequestExpectError(
            request = UsePresentationRequest(
                did = defaultWalletApi.getDefaultDid().did,
                presentationRequest = resolvedPresentationOfferString,
                selectedCredentials = listOf(newCredential!!.id),
                disclosures = newCredential!!.disclosures?.let { mapOf(newCredential!!.id to listOf(it)) }
            )
        )
        // Why is this a bad request?
        assertEquals(HttpStatusCode.BadRequest, error.httpStatusCode)
        assertEquals(true, error.errorMessage?.startsWith("Verification policies did not succeed"))

        verifierApi.getSession(verificationId).also {
            assertNotNull(
                it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt(),
                "Received no valid token response!"
            )
            assertNotNull(
                it.tokenResponse?.presentationSubmission,
                "should have a presentation submission after submission"
            )
            assertEquals(false, it.verificationResult, "overall verification should be not valid")
            it.policyResults.let {
                assertNotNull(it, "policyResults should be available after running policies")
                assertTrue(it.size > 1, "no policies have run")
            }
        }
    }

    @Test
    fun shouldClaimSdJwtVcWithoutDisclosableClaimsWithAllIssuerSigningMethods() = runTest {
        val idCredentialNoDisclosuresNoMappingRequest = Json.decodeFromJsonElement<IssuanceRequest>(
            loadJsonResource("issuance/identity-credential-no-disclosures-no-mapping.json")
        )
        val idCredentialNoDisclosuresRequest = Json.decodeFromJsonElement<IssuanceRequest>(
            loadJsonResource("issuance/identity-credential-no-disclosures.json")
        )

        val issuerDid = loadJsonResource("issuance/mDLDocumentSignerKey.json").run {
            JWKKey.importJWK(this.toString()).getOrThrow().run {
                DidKeyRegistrar().registerByKey(this, DidKeyCreateOptions()).did
            }
        }

        val issuerDSPem = loadResource("issuance/mDLDocumentSignerCertificate.pem")

        listOf(
            idCredentialNoDisclosuresNoMappingRequest,
            idCredentialNoDisclosuresRequest,
            idCredentialNoDisclosuresNoMappingRequest.copy(
                issuerDid = issuerDid,
            ),
            idCredentialNoDisclosuresRequest.copy(
                issuerDid = issuerDid,
            ),
            idCredentialNoDisclosuresNoMappingRequest.copy(
                x5Chain = listOf(
                    issuerDSPem,
                ),
            ),
            idCredentialNoDisclosuresRequest.copy(
                x5Chain = listOf(
                    issuerDSPem,
                ),
            ),
        ).forEach { issuanceRequest ->

            val offerUrl = issuerApi.issueSdJwtCredential(issuanceRequest)

            val claimedWalletCredentialList = defaultWalletApi.claimCredential(
                offerUrl = offerUrl,
            )

            assertEquals(
                expected = 1,
                actual = claimedWalletCredentialList.size,
            )

            val claimedWalletCredential = claimedWalletCredentialList.first()

            assertEquals(
                expected = true,
                actual = claimedWalletCredential.disclosures.isNullOrEmpty(),
            )

            assertEquals(
                expected = CredentialFormat.sd_jwt_vc,
                actual = claimedWalletCredential.format,
            )

            //implicit guard of successful parsing
            SDJwtVC.parse(claimedWalletCredential.document)
        }
    }
}