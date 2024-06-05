package id.walt.webwallet.service.credentials.status.fetch

import TestUtils
import id.walt.webwallet.service.JwsDecoder
import id.walt.webwallet.service.dids.DidResolverService
import id.walt.webwallet.service.endpoint.ServiceEndpointProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class EntraStatusListCredentialFetchStrategyTest {

    private val serviceEndpointProviderMock = mockk<ServiceEndpointProvider>()
    private val didResolverServiceMock = mockk<DidResolverService>()
    private val jwsDecoderMock = mockk<JwsDecoder>()
    private val sut = EntraStatusListCredentialFetchStrategy(
        serviceEndpointProvider = serviceEndpointProviderMock,
        didResolverService = didResolverServiceMock,
        jwsDecoder = jwsDecoderMock
    )
    private val url =
        "did:ion:EiD...<SNIP>?service=IdentityHub&queries=W3sibWV0aG9kIjoiQ29sbGVjdGlvbnNRdWVyeSIsInNjaGVtYSI6Imh0dHBzOi8vdzNpZC5vcmcvdmMtc3RhdHVzLWxpc3QtMjAyMS92MSIsIm9iamVjdElkIjoiZDkwMDQyNTEtZjg1ZS00MjIzLTg5ZjQtMTI1NGFmMjBhYTJlIn1d"

    @Test
    fun test() = runTest {
        val did = Json.decodeFromString<JsonObject>(TestUtils.loadResource("credential-status/entra-did-doc.json"))
        val credential =
            Json.decodeFromString<JsonObject>(TestUtils.loadResource("credential-status/status-list-credential/entra-missing-purpose.json"))
        val serviceEndpointResponse = TestUtils.loadResource("credential-status/entra-service-endpoint-response.json")
        coEvery { didResolverServiceMock.resolve(any()) } returns did
        coEvery { serviceEndpointProviderMock.get(any(), any(), any()) } returns serviceEndpointResponse
        every { jwsDecoderMock.payload(any()) } returns credential
        val result = sut.fetch(url)
        assertEquals(expected = credential, actual = result)
    }
}