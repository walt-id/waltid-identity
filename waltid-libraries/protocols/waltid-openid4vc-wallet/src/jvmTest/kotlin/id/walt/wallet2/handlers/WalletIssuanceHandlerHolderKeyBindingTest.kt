@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.wallet2.data.HolderKeyBindingOrigin
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletIssuanceHandlerHolderKeyBindingTest {

    @Test
    fun `isolated fetch stores an mdoc with its proof key binding`() = runTest {
        val fixture = fixture()

        WalletIssuanceHandler.fetchCredential(
            wallet = fixture.wallet,
            request = FetchCredentialRequest(
                credentialEndpoint = Url("https://issuer.example/credential"),
                accessToken = "access-token",
                credentialConfigurationId = "mdl",
                storeInWallet = true,
                keyId = fixture.holderKey.id.value,
            ),
            httpClient = credentialClient(fixture.mdoc),
        )

        assertEquals(
            HolderKeyBindingOrigin.ISSUANCE,
            fixture.credentialStore.listCredentials().single().holderKeyBinding?.origin,
        )
    }

    @Test
    fun `isolated deferred polling stores an mdoc with its original proof key binding`() = runTest {
        val fixture = fixture()

        val stored = WalletIssuanceHandler.pollDeferredFlow(
            wallet = fixture.wallet,
            request = PollDeferredRequest(
                deferredCredentialEndpoint = Url("https://issuer.example/deferred"),
                accessToken = "access-token",
                transactionId = "transaction-1",
                keyId = fixture.holderKey.id.value,
            ),
            httpClient = credentialClient(fixture.mdoc),
        ).single()

        assertEquals(HolderKeyBindingOrigin.ISSUANCE, stored.holderKeyBinding?.origin)
    }

    private suspend fun fixture(): Fixture {
        val holderKey = signingKey("holder")
        val keyStore = InMemoryKeyStore().apply { addCrypto2Key(holderKey) }
        val credentialStore = InMemoryCredentialStore()
        return Fixture(
            holderKey = holderKey,
            credentialStore = credentialStore,
            wallet = Wallet(
                id = "wallet",
                keyStores = listOf(keyStore),
                credentialStores = listOf(credentialStore),
            ),
            mdoc = mdocCredential(holderKey),
        )
    }

    private fun credentialClient(rawCredential: String) = HttpClient(MockEngine) {
        engine {
            addHandler {
                respond(
                    content = """{"credentials":[{"credential":${Json.encodeToString(rawCredential)}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun signingKey(id: String) =
        CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId(id),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )

    private suspend fun mdocCredential(holderKey: id.walt.crypto2.keys.Key): String {
        val issuerKey = signingKey("issuer")
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            issuerKey,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) {
            subjectDn = "CN=Wallet issuance handler test issuer"
        }
        val holderPublicJwk = holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = holderPublicJwk.toCoseKey(),
            docType = MDOC_DOCTYPE,
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf(
                    MDOC_NAMESPACE to JsonObject(mapOf("given_name" to JsonPrimitive("Ada")))
                )
            ),
        )
        return coseCompliantCbor.encodeToByteArray(
            Document.serializer(),
            Document(docType = MDOC_DOCTYPE, issuerSigned = issuerSigned),
        ).encodeToBase64Url()
    }

    private data class Fixture(
        val holderKey: id.walt.crypto2.keys.Key,
        val credentialStore: InMemoryCredentialStore,
        val wallet: Wallet,
        val mdoc: String,
    )

    private companion object {
        const val MDOC_DOCTYPE = "org.iso.18013.5.1.mDL"
        const val MDOC_NAMESPACE = "org.iso.18013.5.1"
    }
}
