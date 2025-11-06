package id.walt.oid4vc

import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.responses.CredentialResponse
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.*

class EBSIConformanceTest {

    private val PRE_AUTHORIZED_ISSUANCE_PIN = "3818"

    /**
     * CTWalletCrossInTime, CTWalletSameInTime
     */
    @ParameterizedTest
    @MethodSource
    @EnabledIf("vcTestsEnabled")
    suspend fun `issue in-time credential`(
        url: String,
        clientId: String,
        offerRequestCaller: credentialOfferRequestCaller
    ) {
        val credentialOfferRequest = getCredentialOfferRequest(url, clientId, offerRequestCaller)
        val credentialOffer = credentialWallet.resolveCredentialOffer(credentialOfferRequest)
        val credentialResponses =
            credentialWallet.executeFullAuthIssuance(credentialOffer, credentialWallet.TEST_DID, ebsiClientConfig)
        assertEquals(expected = 1, actual = credentialResponses.size)
        assertFalse(actual = credentialResponses[0].isDeferred)
        assertNotNull(actual = credentialResponses[0].credential)
        storeCredentials(credentialResponses[0])
    }

    /**
     * CTWalletCrossDeferred, CTWalletSameDeferred
     */
    @ParameterizedTest
    @MethodSource
    @EnabledIf("vcTestsEnabled")
    suspend fun `issue deferred credential`(
        url: String,
        clientId: String,
        offerRequestCaller: credentialOfferRequestCaller
    ) {
        val deferredCredentialOfferRequest = getCredentialOfferRequest(url, clientId, offerRequestCaller)
        val deferredCredentialOffer = credentialWallet.resolveCredentialOffer(deferredCredentialOfferRequest)
        val deferredCredentialResponses = credentialWallet.executeFullAuthIssuance(
            deferredCredentialOffer, credentialWallet.TEST_DID, ebsiClientConfig
        )
        assertEquals(expected = 1, actual = deferredCredentialResponses.size)
        assertTrue(actual = deferredCredentialResponses[0].isDeferred)
        println("Waiting for deferred credential to be issued (5 seconds delay)")
        Thread.sleep(5500)
        println("Trying to fetch deferred credential")
        val credentialResponse =
            credentialWallet.fetchDeferredCredential(deferredCredentialOffer, deferredCredentialResponses[0])
        assertFalse(actual = credentialResponse.isDeferred)
        assertTrue(actual = credentialResponse.isSuccess)
        assertNotNull(actual = credentialResponse.credential)
        storeCredentials(credentialResponse)
    }

    /**
     * CTWalletCrossPreAuthorised, CTWalletSamePreAuthorised
     */
    @ParameterizedTest
    @MethodSource
    @EnabledIf("vcTestsEnabled")
    suspend fun `issue pre-authorized code credential`(
        url: String, clientId: String, offerRequestCaller: credentialOfferRequestCaller
    ) {
        val preAuthCredentialOfferRequest = getCredentialOfferRequest(url, clientId, offerRequestCaller)
        val preAuthCredentialOffer = credentialWallet.resolveCredentialOffer(preAuthCredentialOfferRequest)
        val preAuthCredentialResponses = credentialWallet.executePreAuthorizedCodeFlow(
            preAuthCredentialOffer, credentialWallet.TEST_DID, ebsiClientConfig, PRE_AUTHORIZED_ISSUANCE_PIN
        )
        assertEquals(expected = 1, actual = preAuthCredentialResponses.size)
        assertTrue(actual = preAuthCredentialResponses[0].isSuccess)
        assertNotNull(actual = preAuthCredentialResponses[0].credential)
        storeCredentials(preAuthCredentialResponses[0])
    }

    /**
     * CTWalletQualificationCredential
     * Requires all VCs from above
     */
    @Test
    @EnabledIf("vpTestsEnabled")
    fun `issue credential using presentation exchange`() = runTest {
        val initIssuanceWithPresentationExchangeUrl =
            URLBuilder("https://api-conformance.ebsi.eu/conformance/v3/issuer-mock/initiate-credential-offer?credential_type=CTWalletQualificationCredential").run {
                parameters.appendAll(StringValues.build {
                    append("client_id", credentialWallet.TEST_DID)
                    append("credential_offer_endpoint", "openid-credential-offer://")
                })
                build()
            }
        val credentialOfferRequestUri =
            runBlocking { ktorClient.get(initIssuanceWithPresentationExchangeUrl).bodyAsText() }
        val credentialOfferRequest =
            CredentialOfferRequest.fromHttpQueryString(Url(credentialOfferRequestUri).encodedQuery)
        val credentialOffer = credentialWallet.resolveCredentialOffer(credentialOfferRequest)
        val credentialResponses =
            credentialWallet.executeFullAuthIssuance(credentialOffer, credentialWallet.TEST_DID, ebsiClientConfig)
        assertEquals(expected = 1, actual = credentialResponses.size)
        assertFalse(actual = credentialResponses[0].isDeferred)
        assertNotNull(actual = credentialResponses[0].credential)
        storeCredentials(credentialResponses[0])
    }

    companion object {
        val ktorClient = HttpClient {
            install(ContentNegotiation) {
                json()
            }
            followRedirects = false
        }
        const val credentialOfferUrl =
            "https://api-conformance.ebsi.eu/conformance/v3/issuer-mock/initiate-credential-offer?credential_type="
        lateinit var crossDeviceCredentialOfferRequestCaller: credentialOfferRequestCaller
        lateinit var sameDeviceCredentialOfferRequestCaller: credentialOfferRequestCaller
        lateinit var credentialWallet: EBSITestWallet
        lateinit var ebsiClientConfig: OpenIDClientConfig

        @BeforeAll
        @JvmStatic
        fun setup() {
            crossDeviceCredentialOfferRequestCaller = { initCredentialOfferUrl ->
                val inTimeCredentialOfferRequestUri =
                    runBlocking { ktorClient.get(initCredentialOfferUrl).bodyAsText() }
                CredentialOfferRequest.fromHttpQueryString(Url(inTimeCredentialOfferRequestUri).encodedQuery)
            }

            sameDeviceCredentialOfferRequestCaller = { initCredentialOfferUrl ->
                val httpResp = runBlocking { ktorClient.get(initCredentialOfferUrl) }
                assertEquals(expected = HttpStatusCode.Found, actual = httpResp.status)
                val inTimeCredentialOfferRequestUri = httpResp.headers[HttpHeaders.Location]!!
                CredentialOfferRequest.fromHttpQueryString(Url(inTimeCredentialOfferRequestUri).encodedQuery)
            }
            credentialWallet = EBSITestWallet(CredentialWalletConfig("https://blank/"))
            ebsiClientConfig = OpenIDClientConfig(
                credentialWallet.TEST_DID, null, credentialWallet.config.redirectUri, useCodeChallenge = true
            )
        }

        @JvmStatic
        fun `issue in-time credential`(): Stream<Arguments> = Stream.of(
            arguments(
                "${credentialOfferUrl}CTWalletSameInTime",
                credentialWallet.TEST_DID,
                sameDeviceCredentialOfferRequestCaller
            ),
            arguments(
                "${credentialOfferUrl}CTWalletCrossInTime",
                credentialWallet.TEST_DID,
                crossDeviceCredentialOfferRequestCaller
            ),
        )

        @JvmStatic
        fun `issue deferred credential`(): Stream<Arguments> = Stream.of(
            arguments(
                "${credentialOfferUrl}CTWalletSameDeferred",
                credentialWallet.TEST_DID,
                sameDeviceCredentialOfferRequestCaller
            ),
            arguments(
                "${credentialOfferUrl}CTWalletCrossDeferred",
                credentialWallet.TEST_DID,
                crossDeviceCredentialOfferRequestCaller
            ),
        )

        @JvmStatic
        fun `issue pre-authorized code credential`(): Stream<Arguments> = Stream.of(
            arguments(
                "${credentialOfferUrl}CTWalletSamePreAuthorised",
                credentialWallet.TEST_DID,
                sameDeviceCredentialOfferRequestCaller
            ),
            arguments(
                "${credentialOfferUrl}CTWalletCrossPreAuthorised",
                credentialWallet.TEST_DID,
                crossDeviceCredentialOfferRequestCaller
            ),
        )

        @JvmStatic
        fun vcTestsEnabled() = false

        @JvmStatic
        fun vpTestsEnabled() = false
    }
}

internal typealias credentialOfferRequestCaller = (initCredentialOfferUrl: Url) -> CredentialOfferRequest

internal fun storeCredentials(vararg credentialResponses: CredentialResponse) = credentialResponses.forEach { _ ->
//    val cred = VerifiableCredential.fromString(it.credential!!.jsonPrimitive.content)
//    Custodian.getService().storeCredential(cred.id ?: randomUUID(), cred)
}

internal fun getCredentialOfferRequest(
    url: String, clientId: String? = null, credentialOfferRequestCall: credentialOfferRequestCaller? = null
) = clientId?.let {
    val initCredentialOfferUrl = URLBuilder(url).run {
        parameters.appendAll(StringValues.build {
            append("client_id", clientId)
            append("credential_offer_endpoint", "openid-credential-offer://")
        })
        build()
    }
    credentialOfferRequestCall!!(initCredentialOfferUrl)
} ?: CredentialOfferRequest(credentialOfferUri = url)
