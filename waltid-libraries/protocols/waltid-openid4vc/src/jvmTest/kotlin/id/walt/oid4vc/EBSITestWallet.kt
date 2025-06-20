package id.walt.oid4vc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import id.walt.w3c.PresentationBuilder
import id.walt.w3c.utils.VCFormat
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.DidService
import id.walt.mdoc.dataelement.MapElement
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.errors.PresentationError
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.interfaces.SimpleHttpResponse
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDCredentialWallet
import id.walt.oid4vc.providers.SIOPSession
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.TokenErrorCode
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.SimpleJWTCryptoProvider
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.time.Duration

const val EBSI_WALLET_PORT = 8011
const val EBSI_WALLET_BASE_URL = "http://localhost:${EBSI_WALLET_PORT}"
const val EBSI_WALLET_TEST_KEY_JWK =
    "{\"kty\":\"EC\",\"d\":\"AENUGJiPF4zRlF1uXV1NTWE5zcQPz-8Ie8SGLdQugec\",\"use\":\"sig\",\"crv\":\"P-256\",\"kid\":\"de8aca52c110485a87fa6fda8d1f2f4e\",\"x\":\"hJ0hFBtp72j1V2xugQI51ernWY_vPXzXjnEg7A709Fc\",\"y\":\"-Mm1j5Zz1mWJU7Nqylk0_6qKjZ5fn6ddzziEFscQPhQ\",\"alg\":\"ES256\"}"
const val EBSI_WALLET_TEST_DID =
    "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrksdXfcbvmhgF2h7YfpxWuywkXxDZ7ohTPNPTQpD39Rm9WiBWuEpvvgtfuPHtHi2wTEkZ95KC2ijUMUowyKMueaMhtA5bLYkt9k8Y8Gq4sm6PyTCHTxuyedMMrBKdRXNZS"

class EBSITestWallet(
    config: CredentialWalletConfig,
) : OpenIDCredentialWallet<SIOPSession>(EBSI_WALLET_BASE_URL, config) {
    private val sessionCache = mutableMapOf<String, SIOPSession>()
    private val ktorClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        followRedirects = false
    }

    val TEST_DID = EBSI_WALLET_TEST_DID
    val TEST_KEY = runBlocking { JWKKey.importJWK(EBSI_WALLET_TEST_KEY_JWK).getOrThrow() }

    override fun resolveDID(did: String): String {
        val didObj = runBlocking { DidService.resolve(did) }.getOrThrow()
        return (didObj["authentication"] ?: didObj["assertionMethod"]
        ?: didObj["verificationMethod"])?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content ?: did
    }

    override fun getDidFor(session: SIOPSession): String {
        return TEST_DID
    }

    override fun isPresentationDefinitionSupported(presentationDefinition: PresentationDefinition): Boolean {
        return true
    }

    override fun createSIOPSession(
        id: String,
        authorizationRequest: AuthorizationRequest?,
        expirationTimestamp: Instant,
    ) = SIOPSession(id, authorizationRequest, expirationTimestamp)

    override val metadata
        get() = createDefaultProviderMetadata() as OpenIDProviderMetadata.Draft13

    override fun getSession(id: String): SIOPSession? = sessionCache[id]
    override fun getSessionByAuthServerState(authServerState: String): SIOPSession? {
        TODO("Not yet implemented")
    }

    override fun removeSession(id: String) {
        sessionCache.remove(id)
    }

    val jwtCryptoProvider = runBlocking {
        SimpleJWTCryptoProvider(
            JWSAlgorithm.ES256, ECDSASigner(ECKey.parse(EBSI_WALLET_TEST_KEY_JWK)), ECDSAVerifier(
                ECKey.parse(
                    EBSI_WALLET_TEST_KEY_JWK
                )
            )
        )
    }

    override fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject?, keyId: String?, privKey: Key?) =
        SDJwt.sign(SDPayload.createSDPayload(payload, SDMap.Companion.fromJSON("{}")), jwtCryptoProvider, keyId).jwt

    override fun signCWTToken(
        target: TokenTarget,
        payload: MapElement,
        header: MapElement?,
        keyId: String?,
        privKey: Key?
    ): String {
        TODO("Not yet implemented")
    }

    override fun verifyTokenSignature(target: TokenTarget, token: String) =
        SDJwt.verifyAndParse(token, jwtCryptoProvider).signatureVerified

    override fun verifyCOSESign1Signature(target: TokenTarget, token: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun httpGet(url: Url, headers: Headers?): SimpleHttpResponse {
        return runBlocking {
            ktorClient.get(url) {
                headers {
                    headers?.let { appendAll(it) }
                }
            }.let { httpResponse ->
                SimpleHttpResponse(
                    httpResponse.status,
                    httpResponse.headers,
                    httpResponse.bodyAsText()
                )
            }
        }
    }

    override fun httpPostObject(url: Url, jsonObject: JsonObject, headers: Headers?): SimpleHttpResponse {
        return runBlocking {
            ktorClient.post(url) {
                headers {
                    headers?.let { appendAll(it) }
                }
                contentType(ContentType.Application.Json)
                setBody(jsonObject)
            }.let { httpResponse ->
                SimpleHttpResponse(
                    httpResponse.status,
                    httpResponse.headers,
                    httpResponse.bodyAsText()
                )
            }
        }
    }

    override fun httpSubmitForm(url: Url, formParameters: Parameters, headers: Headers?): SimpleHttpResponse {
        return runBlocking {
            ktorClient.submitForm(url = url.toString(), formParameters = formParameters, encodeInQuery = false) {
                //url(url)
                headers {
                    headers?.let { appendAll(it) }
                }
                parameters {
                    appendAll(formParameters)
                }
            }.let { httpResponse -> SimpleHttpResponse(httpResponse.status, httpResponse.headers, httpResponse.bodyAsText()) }
        }
    }

    override fun generatePresentationForVPToken(session: SIOPSession, tokenRequest: TokenRequest): PresentationResult {
        val presentationDefinition = session.presentationDefinition ?: throw PresentationError(
            TokenErrorCode.invalid_request,
            tokenRequest,
            session.presentationDefinition
        )

        /*
         * Pre-merge:
         val filterString = presentationDefinition.inputDescriptors.flatMap { it.constraints?.fields ?: listOf() }
            .firstOrNull { field -> field.path.any { it.contains("type") } }?.filter?.jsonObject.toString()
        val presentationJwtStr = Custodian.getService()
            .createPresentation(
                Custodian.getService().listCredentials().filter { filterString.contains(it.type.last()) }.map {
                    PresentableCredential(
                        it,
                        selectiveDisclosure = null,
                        discloseAll = false
                    )
                }, TEST_DID, challenge = session.nonce
            )

         */

        val credentialDescriptorMapping = mapCredentialTypes(presentationDefinition)
        val presentationJwtStr = generatePresentationJwt(credentialDescriptorMapping.map { it.credential }, session)

        println("================")
        println("PRESENTATION IS: $presentationJwtStr")
        println("================")

        val presentationJws = presentationJwtStr.decodeJws()
        val jwtCredentials = ((presentationJws.payload["vp"]
            ?: throw IllegalArgumentException("VerifiablePresentation string does not contain `vp` attribute?")).jsonObject["verifiableCredential"]
            ?: throw IllegalArgumentException("VerifiablePresentation does not contain verifiableCredential list?")).jsonArray.map { it.jsonPrimitive.content }
        return PresentationResult(
            presentations = listOf(JsonPrimitive(presentationJwtStr)),
            presentationSubmission = PresentationSubmission(
                id = randomUUIDString(),
                definitionId = session.presentationDefinition!!.id,
                descriptorMap = getDescriptorMap(jwtCredentials, credentialDescriptorMapping)
            )
        )
    }

    override fun putSession(id: String, session: SIOPSession, ttl: Duration?) {
        sessionCache[id] = session
    }

    private fun generatePresentationJwt(credentialTypes: List<String>, session: SIOPSession): String =
        runBlocking {
            PresentationBuilder().apply {
                did = TEST_DID
                nonce = session.nonce
                addCredentials(listOf())
            }.buildAndSign(TEST_KEY)
        }

    private fun mapCredentialTypes(presentationDefinition: PresentationDefinition) =
        presentationDefinition.inputDescriptors.flatMap { descriptor ->
            descriptor.constraints?.fields?.mapNotNull { field ->
                field.takeIf { it.path.any { it.contains("type") } }
            }?.mapNotNull {
                it.filter?.jsonObject?.get("contains")?.jsonObject?.jsonObject?.get("const")?.jsonPrimitive?.content?.let {
                    CredentialDescriptorMapping(it, descriptor.id)
                }
            } ?: emptyList()
        }

    private fun getDescriptorMap(
        jwtCredentials: List<String>, credentialDescriptor: List<CredentialDescriptorMapping>,
    ): List<DescriptorMapping> = jwtCredentials.mapIndexedNotNull { index, vc ->
        vc.decodeJws().let {
            it.payload["vc"]?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
                ?: "VerifiableCredential"
        }.let { c ->
            credentialDescriptor.find { it.credential == c }
        }?.let {
            DescriptorMapping(
                id = it.descriptor,
                format = VCFormat.jwt_vp,  // jwt_vp_json
                path = "$",
                pathNested = DescriptorMapping(
                    id = it.descriptor,
                    format = VCFormat.jwt_vc,
                    path = "$.vp.verifiableCredential[$index]",
                )
            )
        }
    }

    private data class CredentialDescriptorMapping(
        val credential: String,
        val descriptor: String,
    )

}
