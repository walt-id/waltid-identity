package id.walt.webwallet.service

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.ECKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.registrar.dids.DidCheqdCreateOptions
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.resolver.LocalResolver
import id.walt.did.utils.EnumUtils.enumValueIgnoreCase
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.BatchCredentialResponse
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.httpGet
import id.walt.oid4vc.util.randomUUID
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.SimpleJWTCryptoProvider
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletKeys
import id.walt.webwallet.db.models.WalletOperationHistories
import id.walt.webwallet.db.models.WalletOperationHistory
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.service.dto.LinkedWalletDataTransferObject
import id.walt.webwallet.service.dto.WalletDataTransferObject
import id.walt.webwallet.service.events.*
import id.walt.webwallet.service.issuers.IssuerDataTransferObject
import id.walt.webwallet.service.issuers.IssuersService
import id.walt.webwallet.service.keys.KeysService
import id.walt.webwallet.service.oidc4vc.TestCredentialWallet
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URLDecoder
import kotlin.time.Duration.Companion.seconds


class SSIKit2WalletService(tenant: String?, accountId: UUID, walletId: UUID) :
    WalletService(tenant, accountId, walletId) {

    companion object {
        init {
            runBlocking {
                //WaltidServices.init()
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                    registerRegistrar(LocalRegistrar())
                    updateRegistrarsForMethods()
                }
            }
        }
    }

    override fun listCredentials(): List<WalletCredential> = CredentialsService.list(walletId)

    override suspend fun listRawCredentials(): List<String> = CredentialsService.list(walletId).map {
        it.document
    }

    override suspend fun deleteCredential(id: String) = let {
        CredentialsService.get(walletId, id)?.run {
            logEvent(EventType.Credential.Delete, "wallet", createCredentialEventData(this.parsedDocument, null))
        }
        transaction { CredentialsService.delete(walletId, id) }
    }

    override suspend fun getCredential(credentialId: String): WalletCredential =
        CredentialsService.get(walletId, credentialId)
            ?: throw IllegalArgumentException("WalletCredential not found for credentialId: $credentialId")

    override fun matchCredentialsByPresentationDefinition(presentationDefinition: PresentationDefinition): List<WalletCredential> {
        val credentialList = listCredentials()

        println("WalletCredential list is: ${credentialList.map { it.parsedDocument?.get("type")!!.jsonArray }}")

        data class TypeFilter(val path: String, val type: String? = null, val pattern: String)

        val filters = presentationDefinition.inputDescriptors.mapNotNull {
            it.constraints?.fields?.map {
                val path = it.path.first().removePrefix("$.")
                val filterType = it.filter?.get("type")?.jsonPrimitive?.content
                val filterPattern = it.filter?.get("pattern")?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("No filter pattern in presentation definition constraint")

                TypeFilter(path, filterType, filterPattern)
            }
        }
        println("Using filters: $filters")

        val matchedCredentials = credentialList.filter { credential ->
            filters.any { fields ->
                fields.all { typeFilter ->
                    val credField = credential.parsedDocument!![typeFilter.path] ?: return@all false

                    when (credField) {
                        is JsonPrimitive -> credField.jsonPrimitive.content == typeFilter.pattern
                        is JsonArray -> credField.jsonArray.last().jsonPrimitive.content == typeFilter.pattern
                        else -> false
                    }
                }
            }
        }
        println("Matched credentials: $matchedCredentials")


        return matchedCredentials
    }

    private fun getQueryParams(url: String): Map<String, MutableList<String>> {
        val params: MutableMap<String, MutableList<String>> = HashMap()
        val urlParts = url.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (urlParts.size <= 1)
            return params

        val query = urlParts[1]
        for (param in query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val pair = param.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val key = URLDecoder.decode(pair[0], "UTF-8")
            var value = ""
            if (pair.size > 1) {
                value = URLDecoder.decode(pair[1], "UTF-8")
            }
            var values = params[key]
            if (values == null) {
                values = ArrayList()
                params[key] = values
            }
            values.add(value)
        }
        return params
    }


    /* SIOP */
    @Serializable
    data class PresentationResponse(
        val vp_token: String,
        val presentation_submission: String,
        val id_token: String?,
        val state: String?,
        val fulfilled: Boolean,
        val rp_response: String?
    )

    @Serializable
    data class SIOPv2Response(
        val vp_token: String,
        val presentation_submission: String,
        val id_token: String?,
        val state: String?
    )

    private val ktorClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        followRedirects = false
    }


    data class PresentationError(
        override val message: String,
        val redirectUri: String?
    ) : IllegalArgumentException(message)


    /**
     * @return redirect uri
     */
    override suspend fun usePresentationRequest(
        request: String,
        did: String,
        selectedCredentialIds: List<String>,
        disclosures: Map<String, List<String>>?
    ): Result<String?> {
        val credentialWallet = getCredentialWallet(did)

        val authReq = AuthorizationRequest.fromHttpQueryString(Url(request).encodedQuery)
        println("Auth req: $authReq")

        println("USING PRESENTATION REQUEST, SELECTED CREDENTIALS: $selectedCredentialIds")

        SessionAttributes.HACK_outsideMappedSelectedCredentialsPerSession[authReq.state + authReq.presentationDefinition] =
            selectedCredentialIds
        if (disclosures != null) {
            SessionAttributes.HACK_outsideMappedSelectedDisclosuresPerSession[authReq.state + authReq.presentationDefinition] =
                disclosures
        }

        val presentationSession =
            credentialWallet.initializeAuthorization(authReq, 60.seconds, selectedCredentialIds.toSet())
        println("Initialized authorization (VPPresentationSession): $presentationSession")

        println("Resolved presentation definition: ${presentationSession.authorizationRequest!!.presentationDefinition!!.toJSONString()}")

        val tokenResponse = credentialWallet.processImplicitFlowAuthorization(presentationSession.authorizationRequest)
        val resp = ktorClient.submitForm(
            presentationSession.authorizationRequest.responseUri!!,
            parameters {
                tokenResponse.toHttpParameters().forEach { entry ->
                    entry.value.forEach { append(entry.key, it) }
                }
            })
        val httpResponseBody = runCatching { resp.bodyAsText() }.getOrNull()
        val isResponseRedirectUrl =
            httpResponseBody != null && httpResponseBody.take(10).lowercase().let {
                @Suppress("HttpUrlsUsage")
                it.startsWith("http://") || it.startsWith("https://")
            }
        println("HTTP Response: $resp, body: $httpResponseBody")
        selectedCredentialIds.forEach {
            CredentialsService.get(walletId, it)?.run {
                logEvent(
                    EventType.Credential.Present,
                    presentationSession.presentationDefinition?.name ?: EventDataNotAvailable,
                    createCredentialEventData(this.parsedDocument, null)
                )
            }
        }

        return if (resp.status.isSuccess()) {
            Result.success(if (isResponseRedirectUrl) httpResponseBody else null)
        } else {
            if (isResponseRedirectUrl) {
                Result.failure(
                    PresentationError(
                        message = "Presentation failed - redirecting to error page",
                        redirectUri = httpResponseBody
                    )
                )
            } else {
                println("Response body: $httpResponseBody")
                Result.failure(
                    PresentationError(
                        message = if (httpResponseBody != null) "Presentation failed:\n $httpResponseBody" else "Presentation failed",
                        redirectUri = ""
                    )
                )
            }
        }
    }

    override suspend fun resolvePresentationRequest(request: String): String {
        val credentialWallet = getAnyCredentialWallet()

        return Url(request).protocolWithAuthority.plus("?")
            .plus(credentialWallet.parsePresentationRequest(request).toHttpQueryString())
    }


    private val credentialWallets = HashMap<String, TestCredentialWallet>()

    private fun getCredentialWallet(did: String) = credentialWallets.getOrPut(did) {
        TestCredentialWallet(
            CredentialWalletConfig("http://blank"),
            this,
            did
        )
    }

    private fun getAnyCredentialWallet() =
        credentialWallets.values.firstOrNull() ?: getCredentialWallet("did:test:test")

    private val testCIClientConfig = OpenIDClientConfig("test-client", null, redirectUri = "http://blank")

    val TEST_WALLET_KEY = "{\"kty\":\"EC\",\"d\":\"uD-uxub011cplvr5Bd6MrIPSEUBsgLk-C1y3tnmfetQ\",\"use\":\"sig\",\"crv\":\"secp256k1\",\"kid\":\"48d8a34263cf492aa7ff61b6183e8bcf\",\"x\":\"TKaQ6sCocTDsmuj9tTR996tFXpEcS2EJN-1gOadaBvk\",\"y\":\"0TrIYHcfC93VpEuvj-HXTnyKt0snayOMwGSJA1XiDX8\"}"
    val TEST_WALLET_DID = "did:ion:EiDh0EL8wg8oF-7rRiRzEZVfsJvh4sQX4Jock2Kp4j_zxg:eyJkZWx0YSI6eyJwYXRjaGVzIjpbeyJhY3Rpb24iOiJyZXBsYWNlIiwiZG9jdW1lbnQiOnsicHVibGljS2V5cyI6W3siaWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsInB1YmxpY0tleUp3ayI6eyJjcnYiOiJzZWNwMjU2azEiLCJraWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsImt0eSI6IkVDIiwidXNlIjoic2lnIiwieCI6IlRLYVE2c0NvY1REc211ajl0VFI5OTZ0RlhwRWNTMkVKTi0xZ09hZGFCdmsiLCJ5IjoiMFRySVlIY2ZDOTNWcEV1dmotSFhUbnlLdDBzbmF5T013R1NKQTFYaURYOCJ9LCJwdXJwb3NlcyI6WyJhdXRoZW50aWNhdGlvbiJdLCJ0eXBlIjoiRWNkc2FTZWNwMjU2azFWZXJpZmljYXRpb25LZXkyMDE5In1dfX1dLCJ1cGRhdGVDb21taXRtZW50IjoiRWlCQnlkZ2R5WHZkVERob3ZsWWItQkV2R3ExQnR2TWJSLURmbDctSHdZMUhUZyJ9LCJzdWZmaXhEYXRhIjp7ImRlbHRhSGFzaCI6IkVpRGJxa05ldzdUcDU2cEJET3p6REc5bThPZndxamlXRjI3bTg2d1k3TS11M1EiLCJyZWNvdmVyeUNvbW1pdG1lbnQiOiJFaUFGOXkzcE1lQ2RQSmZRYjk1ZVV5TVlfaUdCRkMwdkQzeDNKVTB6V0VjWUtBIn19"
    val http = HttpClient(Java) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
        followRedirects = false
    }
    private suspend fun processMSEntraIssuanceOffer(authReq: AuthorizationRequest, did: String): List<CredentialResponse> {
        // Load key:
        val testWalletKey = LocalKey.importJWK(TEST_WALLET_KEY).getOrThrow()
        // 3) Load and parse manifest, to find return address (weird concept)
        val manifestUrl = authReq.claims!!["vp_token"]!!.jsonObject["presentation_definition"]!!.jsonObject["input_descriptors"]!!.jsonArray.first().jsonObject["issuance"]!!.jsonArray.first().jsonObject["manifest"]!!.jsonPrimitive.content
        val manifest = Json.parseToJsonElement(httpGet(manifestUrl)).jsonObject["token"]!!.jsonPrimitive.content.let {
            SDJwt.parse(it).fullPayload
        }
        println("Manifest: ${manifest}")
        val issuerReturnAddress = manifest["input"]!!.jsonObject["credentialIssuer"]!!.jsonPrimitive.content

        // 4) Get id_token_hint, if any, to add to response, or else generate id_token/input claims according to alternative attestation mode
        // Attestation modes: https://learn.microsoft.com/en-us/entra/verified-id/rules-and-display-definitions-model

        // Assume we have id_token_hint for now:
        val idTokenHint = authReq.idTokenHint!!

        // 5. ignore PIN for now
        val hashedPin = null
        // 6) Create response JWT token, signed by key for folder DID
        //credentialWallet.
        val responseTokenPayload = SDPayload.createSDPayload(buildJsonObject {
            put("sub", testWalletKey.getThumbprint()) // key thumbprint
            put("aud", issuerReturnAddress)
            put("did", TEST_WALLET_DID) // holder DID
            hashedPin?.let { put("pin", it) }
            put("sub_jwk", testWalletKey.getPublicKey().jwk!!.let { Json.parseToJsonElement(it) }) // pub key JWK
            Clock.System.now().epochSeconds.let {
                put("iat", it)
                put("exp", it + 3600)
            }
            put("jti", UUID.generateUUID().toString())
            put("attestations", buildJsonObject {
                put("idTokens", buildJsonObject {
                    put("https://self-issued.me", idTokenHint)
                })
            })
            put("iss", "https://self-issued.me")
            put("contract", manifestUrl)
        }, SDMap.fromJSON("{}"))
        val jwtCryptoProvider = runBlocking {
            val key = ECKey.parse(TEST_WALLET_KEY)
            SimpleJWTCryptoProvider(JWSAlgorithm.ES256K, ECDSASigner(key).apply {
                jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
            }, ECDSAVerifier(key.toPublicJWK()).apply {
                jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
            })
        }
        val responseToken = SDJwt.sign(responseTokenPayload, jwtCryptoProvider, TEST_WALLET_DID + "#${testWalletKey.getKeyId()}").toString()

        // 7) POST response JWT token to return address found in manifest
//        val issuerReturnAddress = "https://beta.did.msidentity.com/v1.0/tenants/3c32ed40-8a10-465b-8ba4-0b1e86882668/verifiableCredentials/issue"
//        val responseToken = "eyJraWQiOiJkaWQ6aW9uOkVpRGgwRUw4d2c4b0YtN3JSaVJ6RVpWZnNKdmg0c1FYNEpvY2syS3A0al96eGc6ZXlKa1pXeDBZU0k2ZXlKd1lYUmphR1Z6SWpwYmV5SmhZM1JwYjI0aU9pSnlaWEJzWVdObElpd2laRzlqZFcxbGJuUWlPbnNpY0hWaWJHbGpTMlY1Y3lJNlczc2lhV1FpT2lJME9HUTRZVE0wTWpZelkyWTBPVEpoWVRkbVpqWXhZall4T0RObE9HSmpaaUlzSW5CMVlteHBZMHRsZVVwM2F5STZleUpqY25ZaU9pSnpaV053TWpVMmF6RWlMQ0pyYVdRaU9pSTBPR1E0WVRNME1qWXpZMlkwT1RKaFlUZG1aall4WWpZeE9ETmxPR0pqWmlJc0ltdDBlU0k2SWtWRElpd2lkWE5sSWpvaWMybG5JaXdpZUNJNklsUkxZVkUyYzBOdlkxUkVjMjExYWpsMFZGSTVPVFowUmxod1JXTlRNa1ZLVGkweFowOWhaR0ZDZG1zaUxDSjVJam9pTUZSeVNWbElZMlpET1ROV2NFVjFkbW90U0ZoVWJubExkREJ6Ym1GNVQwMTNSMU5LUVRGWWFVUllPQ0o5TENKd2RYSndiM05sY3lJNld5SmhkWFJvWlc1MGFXTmhkR2x2YmlKZExDSjBlWEJsSWpvaVJXTmtjMkZUWldOd01qVTJhekZXWlhKcFptbGpZWFJwYjI1TFpYa3lNREU1SW4xZGZYMWRMQ0oxY0dSaGRHVkRiMjF0YVhSdFpXNTBJam9pUldsQ1FubGtaMlI1V0haa1ZFUm9iM1pzV1dJdFFrVjJSM0V4UW5SMlRXSlNMVVJtYkRjdFNIZFpNVWhVWnlKOUxDSnpkV1ptYVhoRVlYUmhJanA3SW1SbGJIUmhTR0Z6YUNJNklrVnBSR0p4YTA1bGR6ZFVjRFUyY0VKRVQzcDZSRWM1YlRoUFpuZHhhbWxYUmpJM2JUZzJkMWszVFMxMU0xRWlMQ0p5WldOdmRtVnllVU52YlcxcGRHMWxiblFpT2lKRmFVRkdPWGt6Y0UxbFEyUlFTbVpSWWprMVpWVjVUVmxmYVVkQ1JrTXdka1F6ZUROS1ZUQjZWMFZqV1V0QkluMTkjNDhkOGEzNDI2M2NmNDkyYWE3ZmY2MWI2MTgzZThiY2YiLCJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NksifQ.eyJzdWIiOiJKb1I1T1hicGVldlRpeVM4S0Zma2NSN3NlMGMwSVhLbGU0REk1YlVXTncwIiwiYXVkIjoiaHR0cHM6Ly9iZXRhLmRpZC5tc2lkZW50aXR5LmNvbS92MS4wL3RlbmFudHMvM2MzMmVkNDAtOGExMC00NjViLThiYTQtMGIxZTg2ODgyNjY4L3ZlcmlmaWFibGVDcmVkZW50aWFscy9pc3N1ZSIsInN1Yl9qd2siOnsia3R5IjoiRUMiLCJraWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsInVzZSI6InNpZyIsImNydiI6InNlY3AyNTZrMSIsIngiOiJUS2FRNnNDb2NURHNtdWo5dFRSOTk2dEZYcEVjUzJFSk4tMWdPYWRhQnZrIiwieSI6IjBUcklZSGNmQzkzVnBFdXZqLUhYVG55S3Qwc25heU9Nd0dTSkExWGlEWDgifSwiaWF0IjoxNzAzMTAzNDg4LCJleHAiOjE3MDMxMDcwNzIsImp0aSI6IjkwYjkzMThlLWVkMGEtNGMzZC1hMGJiLTg2OTYwOGE3OWYzNCIsImlzcyI6Imh0dHBzOi8vc2VsZi1pc3N1ZWQubWUiLCJwaW4iOiJTQ2NVaHo1MThscWZxbVFQVkpIVVdaOU9scTVVeUZPL2dQYmNOaW93cTNNPSIsImNvbnRyYWN0IjoiaHR0cHM6Ly92ZXJpZmllZGlkLmRpZC5tc2lkZW50aXR5LmNvbS92MS4wL3RlbmFudHMvM2MzMmVkNDAtOGExMC00NjViLThiYTQtMGIxZTg2ODgyNjY4L3ZlcmlmaWFibGVDcmVkZW50aWFscy9jb250cmFjdHMvMDVkN2JhNTctZjRlNi0yNjBjLWNjYjYtMGNkNzQxNzk3NzljL21hbmlmZXN0IiwiYXR0ZXN0YXRpb25zIjp7ImlkVG9rZW5zIjp7Imh0dHBzOi8vc2VsZi1pc3N1ZWQubWUiOiJleUpoYkdjaU9pSkZVekkxTmtzaUxDSnJhV1FpT2lKa2FXUTZkMlZpT21ScFpDNTNiMjlrWjNKdmRtVmtaVzF2TG1OdmJTTTVZMlZoTVRVeU5XWmtOV1kwWkRKak9URTROMlF6TXpBMU9HRTNaVFExT1haalUybG5ibWx1WjB0bGVTMDJaR1V3T1NJc0luUjVjQ0k2SWtwWFZDSjkuZXlKemRXSWlPaUozYkVOeGRYQnhaWGt3Y2xoeGNtdHVSbE5oVW1wQk56QkNNMHRUT1dST05WQllXVjluUXkxS2FuWmpJaXdpWVhWa0lqb2lhSFIwY0hNNkx5OWlaWFJoTG1ScFpDNXRjMmxrWlc1MGFYUjVMbU52YlM5Mk1TNHdMM1JsYm1GdWRITXZNMk16TW1Wa05EQXRPR0V4TUMwME5qVmlMVGhpWVRRdE1HSXhaVGcyT0RneU5qWTRMM1psY21sbWFXRmliR1ZEY21Wa1pXNTBhV0ZzY3k5cGMzTjFaU0lzSW01dmJtTmxJam9pU2tSRk5FVktOa1Y0WWpWQ1VFNDVOMm8zTVZWSFFUMDlJaXdpYzNWaVgycDNheUk2ZXlKamNuWWlPaUp6WldOd01qVTJhekVpTENKcmFXUWlPaUprYVdRNmQyVmlPbVJwWkM1M2IyOWtaM0p2ZG1Wa1pXMXZMbU52YlNNNVkyVmhNVFV5Tldaa05XWTBaREpqT1RFNE4yUXpNekExT0dFM1pUUTFPWFpqVTJsbmJtbHVaMHRsZVMwMlpHVXdPU0lzSW10MGVTSTZJa1ZESWl3aWVDSTZJa3RNYmpWRmFuZFlaazlxZVhkaVZIbzFiRU5hVVdGbFdWbDNObmxEUkROMFlURTNkak5ZVDNOaVJVa2lMQ0o1SWpvaVNFRkdjRlJYWDJNMWJGOUhaamhQVlhKelkzbGZabE5KUjFkUGVWbGxSMDFxZVVkU1lrbzJOemhZYXlKOUxDSmthV1FpT2lKa2FXUTZkMlZpT21ScFpDNTNiMjlrWjNKdmRtVmtaVzF2TG1OdmJTSXNJbVpwY25OMFRtRnRaU0k2SWsxaGRIUm9aWGNpTENKc1lYTjBUbUZ0WlNJNklrMXBZMmhoWld3aUxDSnpZMkZ1Ym1Wa1pHOWpJam9pVGxrZ1UzUmhkR1VnUkhKcGRtVnljeUJNYVdObGJuTmxJaXdpYzJWc1ptbGxJam9pVm1WeWFXWnBaV1FnVTJWc1ptbGxJaXdpZG1WeWFXWnBZMkYwYVc5dUlqb2lSblZzYkhrZ1ZtVnlhV1pwWldRaUxDSmhaR1J5WlhOeklqb2lNak0wTlNCQmJubDNhR1Z5WlNCVGRISmxaWFFzSUZsdmRYSWdRMmwwZVN3Z1Rsa2dNVEl6TkRVaUxDSmhaMlYyWlhKcFptbGxaQ0k2SWs5c1pHVnlJSFJvWVc0Z01qRWlMQ0pwYzNNaU9pSm9kSFJ3Y3pvdkwzTmxiR1l0YVhOemRXVmtMbTFsSWl3aWFXRjBJam94TnpBek1UQXpORFV5TENKcWRHa2lPaUpqWlRSaFpqRXpZeTAwWTJVeUxUUTBNbUV0WVROaVlTMDFaR0V3WVdOa056RmpZamNpTENKbGVIQWlPakUzTURNeE1ETTNOVElzSW5CcGJpSTZleUpzWlc1bmRHZ2lPalFzSW5SNWNHVWlPaUp1ZFcxbGNtbGpJaXdpWVd4bklqb2ljMmhoTWpVMklpd2lhWFJsY21GMGFXOXVjeUk2TVN3aWMyRnNkQ0k2SWpBNU1HTTRabVl6TVdKbU1qUTRaamc0T1RSaU5UaGxZemc0TlRnM1l6Z3dJaXdpYUdGemFDSTZJblZWUW5sNVJXWnpNVzFXYlRBMlZtaGhNelIwT1U5eU5tOVJXVEoyV0RWaGJrZFFiMDVuZGxselUwVTlJbjE5Ll9vUTR1TzVnVDJ2WmZRWGdpcm5YX1BEdG9POS1nZGF0dERqQlpSeEZVZklLUGpYbXJYRjU4RmlFdGNseWxpWHhGTHZ2dnNZeDBFeDhiNWQ3YzZ0ZWFBIn19LCJkaWQiOiJkaWQ6aW9uOkVpRGgwRUw4d2c4b0YtN3JSaVJ6RVpWZnNKdmg0c1FYNEpvY2syS3A0al96eGc6ZXlKa1pXeDBZU0k2ZXlKd1lYUmphR1Z6SWpwYmV5SmhZM1JwYjI0aU9pSnlaWEJzWVdObElpd2laRzlqZFcxbGJuUWlPbnNpY0hWaWJHbGpTMlY1Y3lJNlczc2lhV1FpT2lJME9HUTRZVE0wTWpZelkyWTBPVEpoWVRkbVpqWXhZall4T0RObE9HSmpaaUlzSW5CMVlteHBZMHRsZVVwM2F5STZleUpqY25ZaU9pSnpaV053TWpVMmF6RWlMQ0pyYVdRaU9pSTBPR1E0WVRNME1qWXpZMlkwT1RKaFlUZG1aall4WWpZeE9ETmxPR0pqWmlJc0ltdDBlU0k2SWtWRElpd2lkWE5sSWpvaWMybG5JaXdpZUNJNklsUkxZVkUyYzBOdlkxUkVjMjExYWpsMFZGSTVPVFowUmxod1JXTlRNa1ZLVGkweFowOWhaR0ZDZG1zaUxDSjVJam9pTUZSeVNWbElZMlpET1ROV2NFVjFkbW90U0ZoVWJubExkREJ6Ym1GNVQwMTNSMU5LUVRGWWFVUllPQ0o5TENKd2RYSndiM05sY3lJNld5SmhkWFJvWlc1MGFXTmhkR2x2YmlKZExDSjBlWEJsSWpvaVJXTmtjMkZUWldOd01qVTJhekZXWlhKcFptbGpZWFJwYjI1TFpYa3lNREU1SW4xZGZYMWRMQ0oxY0dSaGRHVkRiMjF0YVhSdFpXNTBJam9pUldsQ1FubGtaMlI1V0haa1ZFUm9iM1pzV1dJdFFrVjJSM0V4UW5SMlRXSlNMVVJtYkRjdFNIZFpNVWhVWnlKOUxDSnpkV1ptYVhoRVlYUmhJanA3SW1SbGJIUmhTR0Z6YUNJNklrVnBSR0p4YTA1bGR6ZFVjRFUyY0VKRVQzcDZSRWM1YlRoUFpuZHhhbWxYUmpJM2JUZzJkMWszVFMxMU0xRWlMQ0p5WldOdmRtVnllVU52YlcxcGRHMWxiblFpT2lKRmFVRkdPWGt6Y0UxbFEyUlFTbVpSWWprMVpWVjVUVmxmYVVkQ1JrTXdka1F6ZUROS1ZUQjZWMFZqV1V0QkluMTkifQ.RwwcxrVxu_S5V_tWGbBBq-09o8OeQ92ueA8tGJSPjkG7YsmKq1oXOKsL3-hsq0gl30c9tb7O-P4YyZegNoomOA"
        val resp = http.post(issuerReturnAddress,{
            contentType(ContentType.Text.Plain)
            setBody(responseToken)
        })
        println("Resp: $resp")
        println(resp.bodyAsText())
        val vc = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["vc"]!!.jsonPrimitive.content
        return listOf(CredentialResponse.Companion.success(CredentialFormat.jwt_vc_json, vc))
    }

    override suspend fun useOfferRequest(offer: String, did: String) {
        val reqParams = parseQueryString(Url(offer).encodedQuery).toMap()
        val authReq = AuthorizationRequest.fromHttpParametersAuto(reqParams)
//        val credentialWallet = getCredentialWallet(did)
//
//        println("// -------- WALLET ----------")
//        println("// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code")
//        println("// parse credential URI")
//        val parsedOfferReq = CredentialOfferRequest.fromHttpParameters(Url(offer).parameters.toMap())
//        println("parsedOfferReq: $parsedOfferReq")
//
//        println("// get issuer metadata")
//        val providerMetadataUri =
//            credentialWallet.getCIProviderMetadataUrl(parsedOfferReq.credentialOffer!!.credentialIssuer)
//        println("Getting provider metadata from: $providerMetadataUri")
//        val providerMetadataResult = ktorClient.get(providerMetadataUri)
//        println("Provider metadata returned: " + providerMetadataResult.bodyAsText())
//
//        val providerMetadata = providerMetadataResult.body<JsonObject>().let { OpenIDProviderMetadata.fromJSON(it) }
//        println("providerMetadata: $providerMetadata")
//
//        println("// resolve offered credentials")
//        val offeredCredentials = parsedOfferReq.credentialOffer!!.resolveOfferedCredentials(providerMetadata)
//        println("offeredCredentials: $offeredCredentials")
//
//        //val offeredCredential = offeredCredentials.first()
//        //println("offeredCredentials[0]: $offeredCredential")
//
//        println("// fetch access token using pre-authorized code (skipping authorization step)")
//        val tokenReq = TokenRequest(
//            grantType = GrantType.pre_authorized_code,
//            clientId = testCIClientConfig.clientID,
//            redirectUri = credentialWallet.config.redirectUri,
//            preAuthorizedCode = parsedOfferReq.credentialOffer!!.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
//            userPin = null
//        )
//        println("tokenReq: $tokenReq")
//
//        val tokenResp = ktorClient.submitForm(
//            providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
//        ).let {
//            println("tokenResp raw: $it")
//            it.body<JsonObject>().let { TokenResponse.fromJSON(it) }
//        }
//
//        println("tokenResp: $tokenResp")
//
//        println(">>> Token response = success: ${tokenResp.isSuccess}")
//
//        println("// receive credential")
//        val nonce = tokenResp.cNonce
//
//
//        println("Using issuer URL: ${parsedOfferReq.credentialOfferUri ?: parsedOfferReq.credentialOffer!!.credentialIssuer}")
//        val credReqs = offeredCredentials.map { offeredCredential ->
//            CredentialRequest.forOfferedCredential(
//                offeredCredential = offeredCredential,
//                proof = credentialWallet.generateDidProof(
//                    did = credentialWallet.did,
//                    issuerUrl =  /*ciTestProvider.baseUrl*/ parsedOfferReq.credentialOfferUri
//                        ?: parsedOfferReq.credentialOffer!!.credentialIssuer,
//                    nonce = nonce
//                )
//            )
//        }
//        println("credReqs: $credReqs")
//
//
//        val credentialResponses = when {
//            credReqs.size >= 2 -> {
//                val batchCredentialRequest = BatchCredentialRequest(credReqs)
//
//                val credentialResponses = ktorClient.post(providerMetadata.batchCredentialEndpoint!!) {
//                    contentType(ContentType.Application.Json)
//                    bearerAuth(tokenResp.accessToken!!)
//                    setBody(batchCredentialRequest.toJSON())
//                }.body<JsonObject>().let { BatchCredentialResponse.fromJSON(it) }
//                println("credentialResponses: $credentialResponses")
//
//                credentialResponses.credentialResponses
//                    ?: throw IllegalArgumentException("No credential responses returned")
//            }
//
//            credReqs.size == 1 -> {
//                val credReq = credReqs.first()
//
//                val credentialResponse = ktorClient.post(providerMetadata.credentialEndpoint!!) {
//                    contentType(ContentType.Application.Json)
//                    bearerAuth(tokenResp.accessToken!!)
//                    setBody(credReq.toJSON())
//                }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }
//                println("credentialResponse: $credentialResponse")
//
//                listOf(credentialResponse)
//            }
//
//            else -> throw IllegalStateException("No credentials offered")
//        }
        val credentialResponses = processMSEntraIssuanceOffer(authReq, did)
        println("// parse and verify credential(s)")
        if (credentialResponses.all { it.credential == null }) {
            throw IllegalStateException("No credential was returned from credentialEndpoint: $credentialResponses")
        }

        val addableCredentials: List<WalletCredential> = credentialResponses.map { credentialResp ->
            val credential = credentialResp.credential!!.jsonPrimitive.content

            val credentialJwt = credential.decodeJws(withSignature = true)

            val credentialResultPair =
                when (val typ = credentialJwt.header["typ"]?.jsonPrimitive?.content?.lowercase()) {
                    "jwt" -> {
                        val credentialId =
                            credentialJwt.payload["vc"]!!.jsonObject["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                ?: randomUUID()

                        println("Got JWT credential: $credentialJwt")

                        Pair(
                            WalletCredential(
                                wallet = walletId,
                                id = credentialId,
                                document = credential,
                                disclosures = null,
                                addedOn = Clock.System.now()
                            ), createCredentialEventData(credentialJwt.payload["vc"]?.jsonObject, typ)
                        )
                    }

                    "vc+sd-jwt" -> {
                        val credentialId =
                            credentialJwt.payload["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                ?: randomUUID()

                        println("Got SD-JWT credential: $credentialJwt")

                        val disclosures = credentialJwt.signature.split("~").drop(1)
                        println("Disclosures (${disclosures.size}): $disclosures")

                        val disclosuresString = disclosures.joinToString("~")

                        val credentialWithoutDisclosures = credential.substringBefore("~")

                        Pair(
                            WalletCredential(
                                wallet = walletId,
                                id = credentialId,
                                document = credentialWithoutDisclosures,
                                disclosures = disclosuresString,
                                addedOn = Clock.System.now()
                            ), createCredentialEventData(credentialJwt.payload["vc"]!!.jsonObject, typ)
                        )
                    }

                    null -> throw IllegalArgumentException("WalletCredential JWT does not have \"typ\"")
                    else -> throw IllegalArgumentException("Invalid credential \"typ\": $typ")
                }
            logEvent(
                EventType.Credential.Accept,
                "", //parsedOfferReq.credentialOffer!!.credentialIssuer,
                credentialResultPair.second
            )
            credentialResultPair.first
        }

        transaction {
            CredentialsService.addAll(
                wallet = walletId,
                credentials = addableCredentials
            )
        }
    }

    /* DIDs */

    override suspend fun createDid(method: String, args: Map<String, JsonPrimitive>): String {
        val keyId = args["keyId"]?.content?.takeIf { it.isNotEmpty() } ?: generateKey(KeyType.Ed25519.name)
        val key = getKey(keyId)
        val options = getDidOptions(method, args)
        val result = DidService.registerByKey(method, key, options)
        DidsService.add(
            wallet = walletId,
            did = result.did,
            document = Json.encodeToString(result.didDocument),
            alias = args["alias"]?.content,
            keyId = keyId
        )
        logEvent(
            EventType.Did.Create, "wallet", DidEventData(
                did = result.did, document = result.didDocument.toString()
            )
        )
        return result.did
    }

    override suspend fun listDids() = transaction { DidsService.list(walletId) }

    override suspend fun loadDid(did: String): JsonObject = DidsService.get(walletId, did)?.let {
        Json.parseToJsonElement(it.document).jsonObject
    } ?: throw IllegalArgumentException("Did not found: $did for account: $walletId")


    override suspend fun deleteDid(did: String): Boolean {
        DidsService.get(walletId, did).also {
            logEvent(
                EventType.Did.Delete, "wallet", DidEventData(
                    did = it?.did ?: did, document = it?.document ?: EventDataNotAvailable
                )
            )
        }
        return DidsService.delete(walletId, did)
    }

    override suspend fun setDefault(did: String) = DidsService.makeDidDefault(walletId, did)

    /* Keys */

    private fun getKey(keyId: String) = KeysService.get(walletId, keyId)?.let {
        KeySerialization.deserializeKey(it.document)
            .getOrElse { throw IllegalArgumentException("Could not deserialize resolved key: ${it.message}", it) }
    } ?: throw IllegalArgumentException("Key not found: $keyId")

    suspend fun getKeyByDid(did: String): Key = DidService.resolveToKey(did).fold(onSuccess = {
        getKey(it.getKeyId())
    }, onFailure = {
        throw it
    })

    override suspend fun exportKey(alias: String, format: String, private: Boolean): String = let {
        runCatching {
            getKey(alias).also {
                logEvent(
                    EventType.Key.Export, "wallet", KeyEventData(
                        id = it.getKeyId(), algorithm = it.keyType.name, keyManagementService = EventDataNotAvailable
                    )
                )
            }
        }.fold(onSuccess = {
            when (format.lowercase()) {
                "jwk" -> it.exportJWK()
                "pem" -> it.exportPEM()
                else -> throw IllegalArgumentException("Unknown format: $format")
            }
        }, onFailure = {
            throw it
        })
    }

    override suspend fun loadKey(alias: String): JsonObject = getKey(alias).exportJWKObject()

    override suspend fun listKeys(): List<SingleKeyResponse> = KeysService.list(walletId).map {
        val key = KeySerialization.deserializeKey(it.document).getOrThrow()

        SingleKeyResponse(
            keyId = SingleKeyResponse.KeyId(it.keyId),
            algorithm = key.keyType.name,
            cryptoProvider = key.toString(),
            keyPair = JsonObject(emptyMap()),
            keysetHandle = JsonNull
        )
    }

    override suspend fun generateKey(type: String): String =
        LocalKey.generate(KeyType.valueOf(type)).let { createdKey ->
            logEvent(
                EventType.Key.Create, "wallet", KeyEventData(
                    id = createdKey.getKeyId(),
                    algorithm = createdKey.keyType.name,
                    keyManagementService = "local",
                )
            )
            KeysService.add(walletId, createdKey.getKeyId(), KeySerialization.serializeKey(createdKey))
            createdKey.getKeyId()
        }

    override suspend fun importKey(jwkOrPem: String): String {
        val type = when {
            jwkOrPem.lines().first().contains("BEGIN ") -> "pem"
            else -> "jwk"
        }

        val keyResult = when (type) {
            "pem" -> LocalKey.importPEM(jwkOrPem)
            "jwk" -> LocalKey.importJWK(jwkOrPem)
            else -> throw IllegalArgumentException("Unknown key type: $type")
        }

        if (keyResult.isFailure) {
            throw IllegalArgumentException("Could not import key as: $type; error message: " + keyResult.exceptionOrNull()?.message)
        }

        val key = keyResult.getOrThrow()
        val keyId = key.getKeyId()
        logEvent(
            EventType.Key.Import, "wallet", KeyEventData(
                id = keyId, algorithm = key.keyType.name, keyManagementService = EventDataNotAvailable
            )
        )
        KeysService.add(walletId, keyId, KeySerialization.serializeKey(key))
        return keyId
    }

    override suspend fun deleteKey(alias: String): Boolean = runCatching {
        KeysService.get(walletId, alias)?.let { Json.parseToJsonElement(it.document) }?.run {
            logEvent(
                EventType.Key.Delete, "wallet", KeyEventData(
                    id = this.jsonObject["jwk"]?.jsonObject?.get("kid")?.jsonPrimitive?.content
                        ?: EventDataNotAvailable,
                    algorithm = this.jsonObject["jwk"]?.jsonObject?.get("kty")?.jsonPrimitive?.content
                        ?: EventDataNotAvailable,
                    keyManagementService = EventDataNotAvailable
                )
            )
        }
    }.let {
        KeysService.delete(walletId, alias)
    }

    fun addToHistory() {
        // data from
        // https://wallet.walt-test.cloud/api/wallet/issuance/info?sessionId=SESSION_ID
        // after taking up issuance offer
    }
// TODO
//fun infoAboutOfferRequest

    override fun getHistory(limit: Int, offset: Long): List<WalletOperationHistory> =
        WalletOperationHistories.select { WalletOperationHistories.wallet eq walletId }
            .orderBy(WalletOperationHistories.timestamp).limit(10).map { row ->
                WalletOperationHistory(row)
            }

    override suspend fun addOperationHistory(operationHistory: WalletOperationHistory) {
        transaction {
            WalletOperationHistories.insert {
                it[tenant] = operationHistory.tenant
                it[accountId] = operationHistory.account
                it[wallet] = operationHistory.wallet
                it[timestamp] = operationHistory.timestamp.toJavaInstant()
                it[operation] = operationHistory.operation
                it[data] = Json.encodeToString(operationHistory.data)
            }
        }
    }

    override fun filterEventLog(filter: EventLogFilter): EventLogFilterResult = runCatching {
        val startingAfterItemIndex = filter.startingAfter?.toLongOrNull()?.takeIf { it >= 0 } ?: -1L
        val pageSize = filter.limit
        val count = EventService.count(walletId, filter.data)
        val offset = startingAfterItemIndex + 1
        val events = EventService.get(
            accountId, walletId, filter.limit, offset, filter.sortOrder ?: "asc", filter.sortBy ?: "", filter.data
        )
        EventLogFilterDataResult(
            items = events,
            count = events.size,
            currentStartingAfter = computeCurrentStartingAfter(startingAfterItemIndex),
            nextStartingAfter = computeNextStartingAfter(startingAfterItemIndex, pageSize, count)
        )
    }.fold(onSuccess = {
        it
    }, onFailure = {
        EventLogFilterErrorResult(reason = it.localizedMessage)
    })

    override suspend fun linkWallet(wallet: WalletDataTransferObject): LinkedWalletDataTransferObject =
        Web3WalletService.link(tenant, walletId, wallet)

    override suspend fun unlinkWallet(wallet: UUID) = Web3WalletService.unlink(tenant, walletId, wallet)

    override suspend fun getLinkedWallets(): List<LinkedWalletDataTransferObject> =
        Web3WalletService.getLinked(tenant, walletId)

    override suspend fun connectWallet(walletId: UUID) = Web3WalletService.connect(tenant, this.walletId, walletId)

    override suspend fun disconnectWallet(wallet: UUID) = Web3WalletService.disconnect(tenant, walletId, wallet)

    override suspend fun listIssuers(): List<IssuerDataTransferObject> = IssuersService.list(walletId)

    override suspend fun getIssuer(name: String): IssuerDataTransferObject =
        IssuersService.get(walletId, name) ?: throw IllegalArgumentException("Issuer: $name not found for: $walletId")

    override fun getCredentialsByIds(credentialIds: List<String>): List<WalletCredential> {
        // todo: select by SQL
        return listCredentials().filter { it.id in credentialIds }
    }

    private fun getDidOptions(method: String, args: Map<String, JsonPrimitive>) = when (method.lowercase()) {
        "key" -> DidKeyCreateOptions(
            args["key"]?.let { enumValueIgnoreCase<KeyType>(it.content) } ?: KeyType.Ed25519,
            args["useJwkJcsPub"]?.let { it.content.toBoolean() } ?: false
        )

        "jwk" -> DidJwkCreateOptions()
        "web" -> DidWebCreateOptions(domain = args["domain"]?.content ?: "", path = args["path"]?.content ?: "")
        "cheqd" -> DidCheqdCreateOptions(
            network = args["network"]?.content ?: "testnet",
        )

        else -> throw IllegalArgumentException("Did method not supported: $method")
    }

    private fun logEvent(action: EventType.Action, originator: String, data: EventData) = EventService.add(
        Event(
            action = action,
            tenant = tenant ?: "global",
            originator = originator,
            account = accountId,
            wallet = walletId,
            data = data,
        )
    )

    //TODO: move to related entity
    private fun createCredentialEventData(json: JsonObject?, type: String?) = CredentialEventData(
        ecosystem = EventDataNotAvailable,
        issuerId = json?.jsonObject?.get("issuer")?.let {
            if(it is JsonObject)
                it.jsonObject["id"]?.jsonPrimitive?.content
            else
                it.jsonPrimitive.content
        } ?: EventDataNotAvailable,
        subjectId = json?.jsonObject?.get("credentialSubject")?.jsonObject?.get(
            "id"
        )?.jsonPrimitive?.content ?: EventDataNotAvailable,
        issuerKeyId = EventDataNotAvailable,
        issuerKeyType = EventDataNotAvailable,
        subjectKeyType = EventDataNotAvailable,
        credentialType = type ?: EventDataNotAvailable,
        credentialFormat = "W3C",
        credentialProofType = EventDataNotAvailable,
        policies = emptyList(),
        protocol = "oid4vp",
    )

    //TODO: move to related entity
    private fun computeCurrentStartingAfter(afterItemIndex: Long): String? = let {
        afterItemIndex.takeIf { it >= 0 }?.toString()
    }

    //TODO: move to related entity
    private fun computeNextStartingAfter(afterItemIndex: Long, pageSize: Int, count: Long): String? = let {
        val itemIndex = afterItemIndex + pageSize
        itemIndex.takeIf { it < count }?.toString()
    }
}