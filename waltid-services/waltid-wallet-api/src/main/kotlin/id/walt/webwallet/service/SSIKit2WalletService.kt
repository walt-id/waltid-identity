package id.walt.webwallet.service

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.Base64URL
import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureManager.whenFeature
import id.walt.commons.web.ConflictException
import id.walt.commons.web.UnsupportedMediaTypeException
import id.walt.commons.web.WebException
import id.walt.crypto.keys.*
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.registrar.dids.DidCheqdCreateOptions
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.resolver.LocalResolver
import id.walt.did.utils.EnumUtils.enumValueIgnoreCase
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.TokenResponse
import id.walt.webwallet.FeatureCatalog
import id.walt.webwallet.config.KeyGenerationDefaultsConfig
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.models.WalletCategoryData
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.db.models.WalletOperationHistories
import id.walt.webwallet.db.models.WalletOperationHistory
import id.walt.webwallet.service.category.CategoryService
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.dids.DidsService
import id.walt.webwallet.service.dto.LinkedWalletDataTransferObject
import id.walt.webwallet.service.dto.WalletDataTransferObject
import id.walt.webwallet.service.events.EventDataNotAvailable
import id.walt.webwallet.service.events.EventService
import id.walt.webwallet.service.events.EventType
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.service.keys.KeysService
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.service.oidc4vc.TestCredentialWallet
import id.walt.webwallet.service.oidc4vc.VPresentationSession
import id.walt.webwallet.service.report.ReportRequestParameter
import id.walt.webwallet.service.report.ReportService
import id.walt.webwallet.service.settings.SettingsService
import id.walt.webwallet.service.settings.WalletSetting
import id.walt.webwallet.usecase.event.EventLogUseCase
import id.walt.webwallet.utils.JsonUtils.toJsonPrimitive
import id.walt.webwallet.utils.StringUtils.couldBeJsonObject
import id.walt.webwallet.utils.StringUtils.parseAsJsonObject
import id.walt.webwallet.web.controllers.exchange.PresentationRequestParameter
import id.walt.webwallet.web.parameter.CredentialRequestParameter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class SSIKit2WalletService(
    tenant: String,
    accountId: Uuid,
    walletId: Uuid,
    private val categoryService: CategoryService,
    private val settingsService: SettingsService,
    private val eventUseCase: EventLogUseCase,
    private val http: HttpClient,
) : WalletService(tenant, accountId, walletId) {
    private val logger = KotlinLogging.logger { }
    private val credentialService = CredentialsService()
    private val eventService = EventService()
    private val credentialReportsService = ReportService.Credentials(credentialService, eventService)

    companion object {
        val defaultGenerationConfig by lazy { ConfigManager.getConfig<RegistrationDefaultsConfig>() }

        init {
            runBlocking {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                    registerRegistrar(LocalRegistrar())
                    updateRegistrarsForMethods()
                }
            }
        }

        val testCIClientConfig = OpenIDClientConfig("test-client", null, redirectUri = "http://blank")
        private val credentialWallets = HashMap<String, TestCredentialWallet>()
        fun getCredentialWallet(did: String) = credentialWallets.getOrPut(did) {
            TestCredentialWallet(
                CredentialWalletConfig("http://blank"), did
            )
        }
    }

    override fun listCredentials(filter: CredentialFilterObject): List<WalletCredential> =
        credentialService.list(walletId, filter)

    override suspend fun listRawCredentials(): List<String> =
        credentialService.list(walletId, CredentialFilterObject.default).map {
            it.document
        }

    override suspend fun deleteCredential(id: String, permanent: Boolean) = let {
        credentialService.get(walletId, id)?.run {
            eventUseCase.log(
                action = EventType.Credential.Delete,
                originator = "wallet",
                tenant = tenant,
                accountId = accountId,
                walletId = walletId,
                data = eventUseCase.credentialEventData(this),
                credentialId = this.id
            )
        }
        credentialService.delete(walletId, id, permanent)
    }

    override suspend fun restoreCredential(id: String): WalletCredential =
        credentialService.restore(walletId, id) ?: throw NotFoundException("Credential with id $id not found")

    override suspend fun getCredential(credentialId: String): WalletCredential =
        credentialService.get(walletId, credentialId)
            ?: throw NotFoundException("WalletCredential not found for credentialId: $credentialId")

    override suspend fun attachCategory(credentialId: String, categories: List<String>): Boolean =
        credentialService.categoryService.add(
            wallet = walletId, credentialId = credentialId, category = categories.toTypedArray()
        ) == categories.size

    override suspend fun detachCategory(credentialId: String, categories: List<String>): Boolean =
        credentialService.categoryService.delete(walletId, credentialId, *categories.toTypedArray()) > 0

    override suspend fun renameCategory(oldName: String, newName: String): Boolean =
        categoryService.rename(walletId, oldName, newName) > 0

    override suspend fun acceptCredential(parameter: CredentialRequestParameter): Boolean =
        credentialService.get(walletId, parameter.credentialId)?.takeIf { it.deletedOn == null }?.let {
            credentialService.setPending(walletId, parameter.credentialId, false) > 0
        } ?: throw NotFoundException("Credential not found: ${parameter.credentialId}")

    override suspend fun rejectCredential(parameter: CredentialRequestParameter): Boolean =
        credentialService.delete(walletId, parameter.credentialId, true)

    /* SIOP */
    @Serializable
    data class PresentationResponse(
        val vp_token: String,
        val presentation_submission: String,
        val id_token: String?,
        val state: String?,
        val fulfilled: Boolean,
        val rp_response: String?,
    )

    @Serializable
    data class SIOPv2Response(
        val vp_token: String,
        val presentation_submission: String,
        val id_token: String?,
        val state: String?,
    )

    data class PresentationError(override val message: String, val redirectUri: String?) :
        IllegalArgumentException(message)


    /**
     * @return redirect uri
     */
    override suspend fun usePresentationRequest(parameter: PresentationRequestParameter): Result<String?> {
        val credentialWallet = getCredentialWallet(parameter.did)

        val authReq =
            AuthorizationRequest.fromHttpParametersAuto(parseQueryString(Url(parameter.request).encodedQuery).toMap())
        logger.debug { "Auth req: $authReq" }

        logger.debug { "Using presentation request, selected credentials: ${parameter.selectedCredentials}" }

        val presentationSession =
            credentialWallet.initializeAuthorization(authReq, 60.seconds, parameter.selectedCredentials.toSet())
        logger.debug { "Initialized authorization (VPPresentationSession): $presentationSession" }

        logger.debug { "Resolved presentation definition: ${presentationSession.authorizationRequest!!.presentationDefinition!!.toJSONString()}" }

        SessionAttributes.HACK_outsideMappedSelectedCredentialsPerSession[presentationSession.authorizationRequest!!.state + presentationSession.authorizationRequest.presentationDefinition?.id] =
            parameter.selectedCredentials
        if (parameter.disclosures != null) {
            SessionAttributes.HACK_outsideMappedSelectedDisclosuresPerSession[presentationSession.authorizationRequest!!.state + presentationSession.authorizationRequest.presentationDefinition?.id] =
                parameter.disclosures
        }

        val tokenResponse =
            credentialWallet.processImplicitFlowAuthorization(presentationSession.authorizationRequest!!)
        val submitFormParams =
            getFormParameters(presentationSession.authorizationRequest, tokenResponse, presentationSession)

        val resp = this.http.submitForm(
            presentationSession.authorizationRequest.responseUri
                ?: presentationSession.authorizationRequest.redirectUri ?: throw AuthorizationError(
                    presentationSession.authorizationRequest,
                    AuthorizationErrorCode.invalid_request,
                    "No response_uri or redirect_uri found on authorization request"
                ), parameters {
                submitFormParams.forEach { entry ->
                    entry.value.forEach { append(entry.key, it) }
                }
            })
        val httpResponseBody = runCatching { resp.bodyAsText() }.getOrNull()
        val isResponseRedirectUrl = httpResponseBody != null && httpResponseBody.take(10).lowercase().let {
            @Suppress("HttpUrlsUsage")
            it.startsWith("http://") || it.startsWith("https://")
        }
        logger.debug { "HTTP Response: $resp, body: $httpResponseBody" }
        parameter.selectedCredentials.forEach {
            credentialService.get(walletId, it)?.run {
                eventUseCase.log(
                    action = EventType.Credential.Present,
                    originator = presentationSession.authorizationRequest.clientMetadata?.clientName
                        ?: EventDataNotAvailable,
                    tenant = tenant,
                    accountId = accountId,
                    walletId = walletId,
                    data = eventUseCase.credentialEventData(
                        credential = this,
                        subject = eventUseCase.subjectData(this),
                        organization = eventUseCase.verifierData(authReq),
                        type = null
                    ),
                    credentialId = this.id,
                    note = parameter.note,
                )
            }
        }



        return if (resp.status.value == 302 && !resp.headers["location"].toString().contains("error")) {
            Result.success(if (isResponseRedirectUrl) httpResponseBody else null)
        } else if (resp.status.isSuccess()) {
            Result.success(if (isResponseRedirectUrl) httpResponseBody else null)
        } else {
            logger.debug { "Presentation failed, return = $httpResponseBody" }
            if (isResponseRedirectUrl) {
                Result.failure(
                    PresentationError(
                        message = "Presentation failed - redirecting to error page",
                        redirectUri = httpResponseBody
                    )
                )
            } else {
                logger.debug { "Response body: $httpResponseBody" }
                Result.failure(
                    PresentationError(
                        message =
                            httpResponseBody?.let {
                                if (it.couldBeJsonObject()) it.parseAsJsonObject().getOrNull()
                                    ?.get("message")?.jsonPrimitive?.content
                                    ?: "Presentation failed"
                                else it
                            } ?: "Presentation failed",
                        redirectUri = ""
                    )
                )
            }
        }
    }

    override suspend fun resolvePresentationRequest(request: String): String {
        val credentialWallet = getAnyCredentialWallet()

        return Url(request)
            .protocolWithAuthority
            .plus("?")
            .plus(credentialWallet.parsePresentationRequest(request).toHttpQueryString())
    }

    private fun getAnyCredentialWallet() =
        credentialWallets.values.firstOrNull() ?: getCredentialWallet("did:test:test")

    suspend fun useOfferRequest(
        offer: String, did: String, requireUserInput: Boolean,
    ): List<WalletCredential> {
        val addableCredentials =
            IssuanceService.useOfferRequest(offer, getCredentialWallet(did), testCIClientConfig.clientID).map {
                WalletCredential(
                    wallet = walletId,
                    id = it.id,
                    document = it.document,
                    disclosures = it.disclosures,
                    addedOn = Clock.System.now(),
                    manifest = it.manifest,
                    deletedOn = null,
                    pending = requireUserInput,
                    format = it.format
                ).also { credential ->
                    eventUseCase.log(
                        action = EventType.Credential.Receive,
                        originator = "", //parsedOfferReq.credentialOffer!!.credentialIssuer,
                        tenant = tenant,
                        accountId = accountId,
                        walletId = walletId,
                        data = eventUseCase.credentialEventData(credential = credential, type = it.type),
                        credentialId = credential.id,
                    )
                }
            }
        credentialService.add(
            wallet = walletId, credentials = addableCredentials.toTypedArray()
        )
        return addableCredentials
    }

    override suspend fun resolveVct(vct: String) = IssuanceService.resolveVct(vct)

    override suspend fun resolveCredentialOffer(
        offerRequest: CredentialOfferRequest,
    ): CredentialOffer {
        return getAnyCredentialWallet().resolveCredentialOffer(offerRequest)
    }

    /* DIDs */

    override suspend fun createDid(method: String, args: Map<String, JsonPrimitive>): String {
        val keyId = args["keyId"]?.content?.takeIf { it.isNotEmpty() } ?: generateKey(
            ({ defaultGenerationConfig.defaultKeyConfig } whenFeature FeatureCatalog.registrationDefaultsFeature)
                ?: throw IllegalArgumentException("No valid keyId provided and no default key available."))
        val key = getKey(keyId)
        val result = DidService.registerDefaultDidMethodByKey(method, key, args)

        DidsService.add(
            wallet = walletId,
            did = result.did,
            document = result.didDocument.toString(),
            alias = args["alias"]?.content,
            keyId = keyId
        )
        eventUseCase.log(
            action = EventType.Did.Create,
            originator = "wallet",
            tenant = tenant,
            accountId = accountId,
            walletId = walletId,
            data = eventUseCase.didEventData(result.did, result.didDocument)
        )
        return result.did
    }

    override suspend fun listDids() = DidsService.list(walletId)

    override suspend fun loadDid(did: String): JsonObject =
        DidsService.get(walletId, did)?.let { Json.parseToJsonElement(it.document).jsonObject }
            ?: throw NotFoundException("The DID ($did) could not be found for Wallet ID: $walletId")

    override suspend fun deleteDid(did: String): Boolean {
        DidsService.get(walletId, did).also {
            eventUseCase.log(
                action = EventType.Did.Delete,
                originator = "wallet",
                tenant = tenant,
                accountId = accountId,
                walletId = walletId,
                data = eventUseCase.didEventData(
                    did = it?.did ?: did, document = it?.document ?: EventDataNotAvailable
                ),
            )
        }
        return DidsService.delete(walletId, did)
    }

    override suspend fun setDefault(did: String) = DidsService.makeDidDefault(walletId, did)

    /* Keys */

    private suspend fun getKey(keyId: String) = KeysService.get(walletId, keyId)?.let {
        KeyManager.resolveSerializedKey(it.document)
    } ?: throw NotFoundException("Key not found: $keyId")

    private suspend fun findKey(alias: String): Key? {

        val resolveSerializedKey: suspend (String) -> Key? = { kid ->
            KeysService.get(walletId, kid)?.let {
                KeyManager.resolveSerializedKey(it.document)
            }
        }

        val resolveSerializedKeyFromDid: suspend (WalletDid) -> Key? = { did ->
            DidService.resolveToKey(did.did).getOrNull()?.let { k ->
                resolveSerializedKey(k.getKeyId())
            }
        }

        // Try to resolve alias as keyId
        resolveSerializedKey(alias)?.let {
            logger.info { "Found key by kid: $alias" }
            return it
        }

        // Try to resolve alias as did
        val match = DidsService.list(walletId).firstOrNull { it.did == alias }
        match?.let {
            logger.info { "Found key by did: $alias" }
            return resolveSerializedKeyFromDid(it)
        }

        // Try to resolve alias as verificationMethod.id
        DidsService.list(walletId).firstNotNullOfOrNull { did ->
            val doc = Json.parseToJsonElement(did.document).jsonObject
            val methods = doc["verificationMethod"]?.jsonArray.orEmpty()
            val match = methods.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == alias }
            match?.let {
                logger.info { "Found key by verificationMethod: $alias" }
                return resolveSerializedKeyFromDid(did)
            }
        }
        return null
    }

    suspend fun getKeyByDid(did: String): Key =
        DidService.resolveToKey(did)
            .fold(onSuccess = { getKey(it.getKeyId()) }, onFailure = { throw it })

    override suspend fun exportKey(alias: String, format: String, private: Boolean): String = let {
        runCatching {
            getKey(alias).also {
                eventUseCase.log(
                    action = EventType.Key.Export,
                    originator = "wallet",
                    tenant = tenant,
                    accountId = accountId,
                    walletId = walletId,
                    data = eventUseCase.keyEventData(it, EventDataNotAvailable)
                )
            }
        }
            .fold(
                onSuccess = {
                    when (format.lowercase()) {
                        "jwk" -> if (private) it.exportJWK() else it.getPublicKey().exportJWK()
                        "pem" -> if (private) it.exportPEM() else it.getPublicKey().exportPEM()
                        else -> throw IllegalArgumentException("Unknown format: $format")
                    }
                },
                onFailure = { throw it })
    }

    override suspend fun loadKey(alias: String): JsonObject = getKey(alias).exportJWKObject()
    override suspend fun getKeyMeta(alias: String): JsonObject =
        Json.encodeToJsonElement(getKey(alias).getMeta()).jsonObject

    override suspend fun listKeys(): List<SingleKeyResponse> =
        KeysService.list(walletId).map {
            val key = KeyManager.resolveSerializedKey(it.document)

            SingleKeyResponse(
                keyId = SingleKeyResponse.KeyId(it.keyId),
                algorithm = key.keyType.name,
                cryptoProvider = key.toString(),
                keyPair = JsonObject(emptyMap()),
                keysetHandle = JsonNull
            )
        }

    override suspend fun generateKey(request: KeyGenerationRequest): String = let {
        if (request.config == null) {
            ConfigManager.getConfig<KeyGenerationDefaultsConfig>().getConfigForBackend(request.backend)?.let {
                request.config = it
            }
        }

        KeyManager.createKey(request)
            .also {
                logger.trace { "Generated key: $it" }
                KeysService.add(walletId, it.getKeyId(), KeySerialization.serializeKey(it))
                eventUseCase.log(
                    action = EventType.Key.Create,
                    originator = "wallet",
                    tenant = tenant,
                    accountId = accountId,
                    walletId = walletId,
                    data = eventUseCase.keyEventData(it, "jwk")
                )
            }.getKeyId()
    }

    private fun getKeyType(jwkOrPem: String): String {
        return when {
            jwkOrPem.trim().startsWith("{") -> "jwk"
            jwkOrPem.trim().startsWith("-----BEGIN ") -> "pem"
            else -> throw UnsupportedMediaTypeException("Unknown key format")
        }
    }


    override suspend fun importKey(jwkOrPem: String): String {
        return runCatching {
            val keyType = getKeyType(jwkOrPem)
            val key = when (keyType) {
                "pem" -> JWKKey.importPEM(jwkOrPem)
                "jwk" -> JWKKey.importJWK(jwkOrPem)
                else -> throw UnsupportedMediaTypeException("Unknown key type: $keyType")
            }.getOrThrow()

            val keyId = key.getKeyId()

            if (KeysService.exists(walletId, keyId)) {
                throw ConflictException("Key with ID $keyId already exists in the database")
            }

            runBlocking {
                eventUseCase.log(
                    action = EventType.Key.Import,
                    originator = "wallet",
                    tenant = tenant,
                    accountId = accountId,
                    walletId = walletId,
                    data = eventUseCase.keyEventData(key, EventDataNotAvailable)
                )
            }

            KeysService.add(walletId, keyId, KeySerialization.serializeKey(key))
            keyId
        }.getOrElse { throwable ->
            when (throwable) {
                is IllegalArgumentException -> throw throwable
                is UnsupportedMediaTypeException -> throw throwable
                is ConflictException -> throw throwable
                is IllegalStateException -> throw throwable
                else -> throw BadRequestException("Unexpected error occurred: ${throwable.message}", throwable)
            }
        }
    }

    override suspend fun deleteKey(alias: String): Boolean =
        performKeyDelete(alias = alias, isTotalDelete = true).getOrThrow().first


    override suspend fun removeKey(alias: String): Boolean =
        performKeyDelete(alias = alias, isTotalDelete = false).getOrThrow().first


    private suspend fun performKeyDelete(alias: String, isTotalDelete: Boolean) = runCatching {
        val key = getKey(alias)

        val canDeleteFromStorage = isTotalDelete && key.deleteKey() || !isTotalDelete
        val operationSucceeded = canDeleteFromStorage && KeysService.delete(walletId, alias)

        if (isTotalDelete && !operationSucceeded) throw WebException(
            HttpStatusCode.BadRequest.value,
            "Failed to delete remote key : $alias"
        )
        Pair(operationSucceeded, key)
    }.onSuccess { result ->
        val (operationSucceeded, key) = result
        if (operationSucceeded) {
            eventUseCase.log(
                action = (if (isTotalDelete) EventType.Key.Delete else EventType.Key.Remove),
                originator = "wallet",
                tenant = tenant,
                accountId = accountId,
                walletId = walletId,
                data = eventUseCase.keyEventData(
                    id = alias,
                    algorithm = key.keyType.name,
                    kmsType = EventDataNotAvailable
                )
            )
        } else {
            logger.warn { "Key delete operation not performed for alias: $alias" }
            throw WebException(HttpStatusCode.BadRequest.value, "Failed to delete key: $alias")
        }
    }.onFailure {
        val errorMessage = "Failed to delete key: ${it.message}"
        logger.error(it) { errorMessage }
        throw WebException(HttpStatusCode.BadRequest.value, errorMessage)
    }

    override suspend fun sign(alias: String, data: JsonElement): String {
        val key = findKey(alias) ?: throw NotFoundException("Key not found: $alias")

        // Check whether the given data is a partially initialised Flattened JWS+Json object
        // https://datatracker.ietf.org/doc/html/rfc7515#section-7.2.2
        val jwsObj = (data as? JsonObject)
            ?.takeIf { it.keys == setOf("protected", "payload") }
            ?.let {
                val headerB64 = it["protected"]?.jsonPrimitive?.content
                val payloadB64 = it["payload"]?.jsonPrimitive?.content
                if (!headerB64.isNullOrBlank() && !payloadB64.isNullOrBlank()) {
                    val header = JWSHeader.parse(Base64URL.from(headerB64))
                    JWSObject(header, Payload(Base64.getUrlDecoder().decode(payloadB64)))
                } else null
            }

        // For JWS+Json we take the header as given
        val signature = if (jwsObj != null) {
            val headers = jwsObj.header.toJSONObject().toMutableMap()
            headers["kid"]?.toJsonPrimitive()?.content?.also { kid ->
                if (kid != alias) throw IllegalArgumentException("JWT headers.kid must match alias: $alias")
            }
            headers["kid"] = JsonPrimitive(alias)
            val dataBytes = jwsObj.payload.toString().toByteArray(Charsets.UTF_8)
            key.signJws(dataBytes, headers.toJsonObject())
        }

        // Otherwise, we generate the header and use the given alias as kid
        else {
            val headers = linkedMapOf(
                "typ" to JOSEObjectType.JWT.type,
                "kid" to alias
            ).toJsonObject()
            val dataBytes = data.toString().toByteArray(Charsets.UTF_8)
            key.signJws(dataBytes, headers)
        }

        eventUseCase.log(
            action = EventType.Key.Sign,
            originator = "wallet",
            tenant = tenant,
            accountId = accountId,
            walletId = walletId,
            data = eventUseCase.keyEventData(
                id = alias,
                algorithm = key.keyType.name,
                kmsType = EventDataNotAvailable
            )
        )

        return signature
    }

    override suspend fun verify(jwk: String, signature: String): Boolean {
        val key = JWKKey.importJWK(jwk).getOrNull() ?: throw IllegalArgumentException("Key import failed")

        val verify = key.verifyJws(signature)
        eventUseCase.log(
            action = EventType.Key.Verify,
            originator = "wallet",
            tenant = tenant,
            accountId = accountId,
            walletId = walletId,
            data = eventUseCase.keyEventData(
                id = jwk,
                algorithm = key.keyType.name,
                kmsType = EventDataNotAvailable
            )
        )
        return verify.isSuccess
    }

    override fun getHistory(limit: Int, offset: Long): List<WalletOperationHistory> =
        WalletOperationHistories.selectAll()
            .where { WalletOperationHistories.wallet eq walletId.toJavaUuid() }
            .orderBy(WalletOperationHistories.timestamp)
            .limit(10)
            .map { row -> WalletOperationHistory(row) }

    override suspend fun addOperationHistory(operationHistory: WalletOperationHistory) {
        transaction {
            WalletOperationHistories.insert {
                it[tenant] = operationHistory.tenant
                it[accountId] = operationHistory.account
                it[wallet] = operationHistory.wallet.toJavaUuid()
                it[timestamp] = operationHistory.timestamp.toJavaInstant()
                it[operation] = operationHistory.operation
                it[data] = Json.encodeToString(operationHistory.data)
            }
        }
    }

    override suspend fun linkWallet(
        wallet: WalletDataTransferObject,
    ): LinkedWalletDataTransferObject = Web3WalletService.link(tenant, walletId, wallet)

    override suspend fun unlinkWallet(wallet: Uuid) =
        Web3WalletService.unlink(tenant, walletId, wallet)

    override suspend fun getLinkedWallets(): List<LinkedWalletDataTransferObject> =
        Web3WalletService.getLinked(tenant, walletId)

    override suspend fun connectWallet(walletId: Uuid) =
        Web3WalletService.connect(tenant, this.walletId, walletId)

    override suspend fun disconnectWallet(wallet: Uuid) =
        Web3WalletService.disconnect(tenant, walletId, wallet)

    override fun getCredentialsByIds(credentialIds: List<String>): List<WalletCredential> {
        // todo: select by SQL
        return listCredentials(CredentialFilterObject.default).filter { it.id in credentialIds }
    }

    override suspend fun listCategories(): List<WalletCategoryData> = categoryService.list(walletId)

    override suspend fun addCategory(name: String): Boolean = categoryService.add(walletId, name) == 1

    override suspend fun deleteCategory(name: String): Boolean = categoryService.delete(walletId, name) == 1
    override suspend fun getFrequentCredentials(parameter: ReportRequestParameter): List<WalletCredential> =
        credentialReportsService.frequent(parameter)

    override suspend fun getSettings(): WalletSetting = settingsService.get(walletId)

    override suspend fun setSettings(settings: JsonObject): Boolean = settingsService.set(walletId, settings) > 0

    private fun getDidOptions(method: String, args: Map<String, JsonPrimitive>) =
        when (method.lowercase()) {
            "key" ->
                DidKeyCreateOptions(
                    args["key"]?.let { enumValueIgnoreCase<KeyType>(it.content) } ?: KeyType.Ed25519,
                    args["useJwkJcsPub"]?.let { it.content.toBoolean() } == true)

            "jwk" -> DidJwkCreateOptions()
            "web" ->
                DidWebCreateOptions(
                    domain = args["domain"]?.content ?: "", path = args["path"]?.content ?: ""
                )

            "cheqd" ->
                DidCheqdCreateOptions(
                    network = args["network"]?.content ?: "testnet",
                )

            else -> throw IllegalArgumentException("Did method not supported: $method")
        }

    private fun getFormParameters(
        authorizationRequest: AuthorizationRequest,
        tokenResponse: TokenResponse,
        presentationSession: VPresentationSession
    ) = if (authorizationRequest.responseMode == ResponseMode.direct_post_jwt) {
        directPostJwtParameters(authorizationRequest, tokenResponse, presentationSession)
    } else tokenResponse.toHttpParameters()

    private fun directPostJwtParameters(
        authorizationRequest: AuthorizationRequest,
        tokenResponse: TokenResponse,
        presentationSession: VPresentationSession
    ): Map<String, List<String>> {
        val encKey = authorizationRequest.clientMetadata?.jwks?.get("keys")?.jsonArray?.first { jwk ->
            JWK.parse(jwk.toString()).keyUse?.equals(KeyUse.ENCRYPTION) == true
        }?.jsonObject ?: throw Exception("No ephemeral reader key found")
        val ephemeralWalletKey = runBlocking { KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1)) }
        return tokenResponse.toDirecPostJWTParameters(
            encKey,
            alg = authorizationRequest.clientMetadata!!.authorizationEncryptedResponseAlg!!,
            enc = authorizationRequest.clientMetadata!!.authorizationEncryptedResponseEnc!!,
            mapOf(
                "epk" to runBlocking { ephemeralWalletKey.getPublicKey().exportJWKObject() },
                "apu" to JsonPrimitive(Base64URL.encode(presentationSession.nonce).toString()),
                "apv" to JsonPrimitive(Base64URL.encode(authorizationRequest.nonce!!).toString())
            )
        )
    }
}

