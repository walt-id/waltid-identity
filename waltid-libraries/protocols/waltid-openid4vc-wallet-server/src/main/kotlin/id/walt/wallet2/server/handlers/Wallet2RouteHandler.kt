package id.walt.wallet2.server.handlers

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.TypedKeyGenerationRequest
import id.walt.did.dids.DidService
import id.walt.wallet2.data.*
import id.walt.wallet2.handlers.*
import id.walt.wallet2.server.WalletResolver
import id.walt.wallet2.server.openapi.Wallet2OpenApiDocs
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

// ---------------------------------------------------------------------------
// Wallet management request / response types
// ---------------------------------------------------------------------------

/**
 * Request body for POST /wallet.
 *
 * All fields are optional. Omitting store fields auto-creates one in-memory
 * store of each type. Providing explicit store IDs references named stores.
 * Providing staticKey/staticDid creates a store-less wallet for isolated flows.
 *
 * This same shape is accepted by both OSS and Enterprise backends.
 */
@Serializable
data class CreateWalletRequest(
    val keyStoreIds: List<String>? = null,
    val credentialStoreIds: List<String>? = null,
    val didStoreId: String? = null,
    /** Set true to explicitly create a wallet with no DID store. */
    val noDidStore: Boolean = false,
    /** Serialized static key, used when no key stores are attached. */
    val staticKey: JsonObject? = null,
    val staticDid: String? = null
)

@Serializable
data class WalletCreatedResponse(
    val walletId: String
)

@Serializable
data class WalletInfoResponse(
    val walletId: String,
    val keyStoreCount: Int,
    val credentialStoreCount: Int,
    val hasDidStore: Boolean,
    val hasStaticKey: Boolean,
    val hasStaticDid: Boolean,
    val keyStoreIds: List<String> = emptyList(),
    val credentialStoreIds: List<String> = emptyList(),
    val didStoreId: String? = null,
    val defaultKeyId: String? = null,
    val defaultDidId: String? = null,
)

@Serializable
data class ImportKeyRequest(
    val key: JsonObject
)

@Serializable
data class CreateDidRequest(
    val method: String,
    val keyId: String? = null
)

@Serializable
data class ImportDidRequest(
    val did: String,
    val document: String
)

// ---------------------------------------------------------------------------
// Route handler
// ---------------------------------------------------------------------------

/**
 * Shared Ktor route handler for the Wallet2 API.
 *
 * Deployments using descriptor-based wallet lifecycle provide their own [WalletResolver].
 * Enterprise keeps its resource-tree route adapter but delegates operation semantics to the
 * same framework-neutral Wallet2 handlers.
 */
object Wallet2RouteHandler {

    private const val WALLET_MANAGEMENT_TAG = "Wallet Management"
    private const val CREDENTIALS_TAG = "Credentials"

    fun Route.registerWallet2Routes(
        resolver: WalletResolver,
        /**
         * Optional: extract the authenticated account ID from the current call.
         * When provided, wallet routes check that the authenticated account owns
         * the requested wallet. When null, all wallets are accessible (dev / no-auth mode).
         */
        getAccountId: (suspend RoutingCall.() -> String?)? = null,
        attestationAssembler: ClientAttestationAssembler? = null,
    ) {
        route("/wallet") {
            registerWalletManagementRoutes(resolver, getAccountId, attestationAssembler)
        }
        route("/stores", { tags = listOf("Named Store Management") }) {
            registerNamedStoreRoutes(resolver)
        }
    }

    // -------------------------------------------------------------------------
    // Wallet CRUD
    // -------------------------------------------------------------------------

    internal fun Route.registerWalletManagementRoutes(
        resolver: WalletResolver,
        getAccountId: (suspend RoutingCall.() -> String?)?,
        attestationAssembler: ClientAttestationAssembler?,
    ) {

        post("", Wallet2OpenApiDocs.createWallet()) {
            val req = runCatching { call.receive<CreateWalletRequest>() }
                .getOrElse { CreateWalletRequest() }
            val id = Uuid.random().toString()

            // Auto-created stores are registered under a generated store ID so that:
            //  - resolveStoreId() can map the store instance back to an ID when storeWallet()
            //    serializes the Wallet to a WalletDescriptor (otherwise the descriptor would be
            //    persisted with no attached stores — see resolveStoreId default), and
            //  - the same store instance is resolvable later via resolveKeyStore()/etc.
            // Collect registered store IDs upfront when the user provides explicit IDs,
            // so we can validate them before calling resolveKeyStore() (which uses computeIfAbsent
            // and would silently create a new store for any unknown ID instead of rejecting it).
            val registeredKeyStoreIds = if (req.keyStoreIds != null) resolver.listKeyStoreIds().toList() else emptyList()
            val registeredCredentialStoreIds =
                if (req.credentialStoreIds != null) resolver.listCredentialStoreIds().toList() else emptyList()
            val registeredDidStoreIds = if (req.didStoreId != null) resolver.listDidStoreIds().toList() else emptyList()

            val keyStores: List<WalletKeyStore> = when {
                req.keyStoreIds != null ->
                    req.keyStoreIds.map { storeId ->
                        require(storeId in registeredKeyStoreIds) { "Key store '$storeId' not found" }
                        // Non-null: existence validated by require above
                        requireNotNull(resolver.resolveKeyStore(storeId)) { "Key store '$storeId' disappeared between validation and use" }
                    }

                req.staticKey != null -> emptyList()
                else -> listOf(resolver.keyStoreFactory("$id-keys").also { resolver.storeKeyStore("$id-keys", it) })
            }

            val credentialStores: List<WalletCredentialStore> = when {
                req.credentialStoreIds != null ->
                    req.credentialStoreIds.map { storeId ->
                        require(storeId in registeredCredentialStoreIds) { "Credential store '$storeId' not found" }
                        requireNotNull(resolver.resolveCredentialStore(storeId)) { "Credential store '$storeId' disappeared between validation and use" }
                    }

                else -> listOf(
                    resolver.credentialStoreFactory("$id-credentials").also { resolver.storeCredentialStore("$id-credentials", it) })
            }

            val didStore: WalletDidStore? = when {
                req.noDidStore -> null
                req.didStoreId != null -> {
                    require(req.didStoreId in registeredDidStoreIds) { "DID store '${req.didStoreId}' not found" }
                    resolver.resolveDidStore(req.didStoreId)
                }

                req.staticDid != null -> null
                else -> resolver.didStoreFactory("$id-dids").also { resolver.storeDidStore("$id-dids", it) }
            }

            val staticKey = req.staticKey?.let { KeyManager.resolveSerializedKey(it.toString()) }

            val wallet = Wallet(
                id = id,
                keyStores = keyStores,
                credentialStores = credentialStores,
                didStore = didStore,
                staticKey = staticKey,
                staticDid = req.staticDid
            )
            resolver.storeWallet(wallet)
            // If auth is enabled, automatically link the new wallet to the requesting account
            if (getAccountId != null) {
                val accountId = call.getAccountId()
                if (accountId != null) {
                    resolver.linkWalletToAccount(accountId, id)
                }
            }
            log.info { "Created wallet $id (keyStores=${keyStores.size}, credentialStores=${credentialStores.size}, didStore=${didStore != null})" }
            call.respond(HttpStatusCode.Created, WalletCreatedResponse(walletId = id))
        }

        get("", {
            tags = listOf(WALLET_MANAGEMENT_TAG)
            summary = "List wallet IDs"
            description = "When auth is enabled, returns only wallets owned by the authenticated account."
            response { HttpStatusCode.OK to { body<List<String>>() } }
        }) {
            val ids: List<String> = getAccountId
                ?.let { call.it() }
                ?.let { resolver.getWalletIdsForAccount(it) }
                ?: resolver.listWalletIds().toList()
            call.respond(ids)
        }

        route("/{walletId}") {

            get("", {
                tags = listOf(WALLET_MANAGEMENT_TAG)
                summary = "Get wallet info"
                request { pathParameter<String>("walletId") }
                response {
                    HttpStatusCode.OK to {
                        body<WalletInfoResponse> {
                            example("Wallet with named stores") {
                                value = WalletInfoResponse(
                                    walletId = "9bbbc42d-8f3a-4b1c-9e2d-1a2b3c4d5e6f",
                                    keyStoreCount = 1,
                                    credentialStoreCount = 1,
                                    hasDidStore = true,
                                    keyStoreIds = listOf("main-kms"),
                                    credentialStoreIds = listOf("main-credentials"),
                                    didStoreId = "main-dids",
                                    hasStaticKey = false,
                                    hasStaticDid = false,
                                    defaultKeyId = "v_CW0xP256ExampleKeyId",
                                    defaultDidId = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbhnRFvvWUpK",
                                )
                            }
                        }
                    }
                    HttpStatusCode.NotFound to { description = "Wallet not found" }
                }
            }) {
                val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@get
                call.respond(wallet.toInfoResponse(resolver))
            }

            delete("", {
                tags = listOf(WALLET_MANAGEMENT_TAG)
                summary = "Delete a wallet"
                request { pathParameter<String>("walletId") }
                response {
                    HttpStatusCode.NoContent to { description = "Deleted" }
                    HttpStatusCode.NotFound to { description = "Wallet not found" }
                }
            }) {
                val walletId = call.parameters["walletId"] ?: return@delete
                call.resolveOrRespond(resolver, getAccountId) ?: return@delete
                resolver.deleteWallet(walletId)
                call.respond(HttpStatusCode.NoContent)
            }

            // -----------------------------------------------------------------
            // Key management
            // -----------------------------------------------------------------

            route("/keys", { tags = listOf("Key Management") }) {

                get("", {
                    summary = "List keys across all key stores"
                    request { pathParameter<String>("walletId") }
                    response { HttpStatusCode.OK to { body<List<WalletKeyInfo>>() } }
                }) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@get
                    call.respond(wallet.listAllKeys())
                }

                post("/generate", Wallet2OpenApiDocs.generateKey()) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                    val req = call.receive<TypedKeyGenerationRequest>()
                    val store = wallet.keyStores.firstOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Wallet has no key stores")
                    val key = KeyManager.createKey(req)
                    val keyId = store.addKey(key)
                    call.respond(HttpStatusCode.Created, WalletKeyInfo(keyId = keyId, keyType = req.keyType.name))
                }

                post("/import", {
                    summary = "Import an existing key"
                    request { pathParameter<String>("walletId"); body<ImportKeyRequest>() }
                    response { HttpStatusCode.Created to { body<WalletKeyInfo>() } }
                }) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                    val req = call.receive<ImportKeyRequest>()
                    val key = KeyManager.resolveSerializedKey(req.key.toString())
                    val store = wallet.keyStores.firstOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Wallet has no key stores")
                    val keyId = store.addKey(key)
                    call.respond(HttpStatusCode.Created, WalletKeyInfo(keyId = keyId, keyType = key.keyType.name))
                }

                route("/{keyId}") {

                    get("", {
                        summary = "Get key metadata"
                        request { pathParameter<String>("walletId"); pathParameter<String>("keyId") }
                        response {
                            HttpStatusCode.OK to { body<WalletKeyInfo>() }
                            HttpStatusCode.NotFound to { description = "Key not found" }
                        }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@get
                        val keyId = call.parameters["keyId"]!!
                        val key = wallet.findKey(keyId)
                            ?: return@get call.respond(HttpStatusCode.NotFound, "Key '$keyId' not found")
                        call.respond(WalletKeyInfo(keyId = keyId, keyType = key.keyType.name))
                    }

                    delete("", {
                        summary = "Delete a key"
                        request { pathParameter<String>("walletId"); pathParameter<String>("keyId") }
                        response { HttpStatusCode.NoContent to {}; HttpStatusCode.NotFound to {} }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@delete
                        val keyId = call.parameters["keyId"]!!
                        val removed = wallet.keyStores.any { it.removeKey(keyId) }
                        if (removed) call.respond(HttpStatusCode.NoContent)
                        else call.respond(HttpStatusCode.NotFound, "Key '$keyId' not found")
                    }

                    put("/set-default", {
                        summary = "Set this key as the default key for the wallet"
                        description = "After this call, issuance and presentation flows will use this key unless overridden per-request."
                        request { pathParameter<String>("walletId"); pathParameter<String>("keyId") }
                        response {
                            HttpStatusCode.NoContent to { description = "Default updated" }
                            HttpStatusCode.NotFound to { description = "Key not found in wallet" }
                        }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@put
                        val keyId = call.parameters["keyId"]!!
                        require(wallet.findKey(keyId) != null) { "Key '$keyId' not found in wallet '${wallet.id}'" }
                        resolver.setWalletDefaults(wallet.id, defaultKeyId = keyId, defaultDidId = null)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }

            // -----------------------------------------------------------------
            // DID management
            // -----------------------------------------------------------------

            route("/dids", { tags = listOf("DID Management") }) {

                get("", {
                    summary = "List DIDs"
                    request { pathParameter<String>("walletId") }
                    response { HttpStatusCode.OK to { body<List<WalletDidEntry>>() } }
                }) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@get
                    call.respond(wallet.didStore?.listDids()?.toList() ?: emptyList<WalletDidEntry>())
                }

                post("/create", Wallet2OpenApiDocs.createDid()) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                    val req = call.receive<CreateDidRequest>()
                    val didStore = wallet.didStore
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Wallet has no DID store")
                    val key = (req.keyId?.let { wallet.findKey(it) } ?: wallet.defaultKey())
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "No key available for DID creation")
                    val did = DidService.registerByKey(req.method, key)
                    val entry = WalletDidEntry(
                        did = did.did,
                        document = runCatching {
                            Json.parseToJsonElement(did.didDocument.toString()).jsonObject
                        }.getOrElse { JsonObject(emptyMap()) }
                    )
                    didStore.addDid(entry)
                    call.respond(HttpStatusCode.Created, entry)
                }

                post("/import", {
                    summary = "Import a DID and its document"
                    request { pathParameter<String>("walletId"); body<ImportDidRequest>() }
                    response { HttpStatusCode.Created to { body<WalletDidEntry>() } }
                }) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                    val req = call.receive<ImportDidRequest>()
                    val didStore = wallet.didStore
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Wallet has no DID store")
                    val entry = WalletDidEntry(
                        did = req.did,
                        document = runCatching {
                            Json.parseToJsonElement(req.document).jsonObject
                        }.getOrElse { JsonObject(emptyMap()) }
                    )
                    didStore.addDid(entry)
                    call.respond(HttpStatusCode.Created, entry)
                }

                route("/{did}") {

                    get("", {
                        summary = "Get a DID entry"
                        request { pathParameter<String>("walletId"); pathParameter<String>("did") }
                        response {
                            HttpStatusCode.OK to { body<WalletDidEntry>() }
                            HttpStatusCode.NotFound to {}
                        }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@get
                        val did = call.parameters["did"]!!
                        val entry = wallet.didStore?.getDid(did)
                            ?: return@get call.respond(HttpStatusCode.NotFound, "DID '$did' not found")
                        call.respond(entry)
                    }

                    delete("", {
                        summary = "Delete a DID"
                        request { pathParameter<String>("walletId"); pathParameter<String>("did") }
                        response { HttpStatusCode.NoContent to {}; HttpStatusCode.NotFound to {} }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@delete
                        val did = call.parameters["did"]!!
                        val removed = wallet.didStore?.removeDid(did) ?: false
                        if (removed) call.respond(HttpStatusCode.NoContent)
                        else call.respond(HttpStatusCode.NotFound, "DID '$did' not found")
                    }

                    put("/set-default", {
                        summary = "Set this DID as the default DID for the wallet"
                        description = "After this call, issuance and presentation flows will use this DID unless overridden per-request."
                        request { pathParameter<String>("walletId"); pathParameter<String>("did") }
                        response {
                            HttpStatusCode.NoContent to { description = "Default updated" }
                            HttpStatusCode.NotFound to { description = "DID not found in wallet" }
                        }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@put
                        val did = call.parameters["did"]!!
                        require(wallet.didStore?.getDid(did) != null) { "DID '$did' not found in wallet '${wallet.id}'" }
                        resolver.setWalletDefaults(wallet.id, defaultKeyId = null, defaultDidId = did)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }

            // -----------------------------------------------------------------
            // Credential management + issuance + presentation
            // -----------------------------------------------------------------

            route("/credentials") {

                get("", {
                    tags = listOf(CREDENTIALS_TAG)
                    summary = "List credentials (metadata only, no raw credential data)"
                    request { pathParameter<String>("walletId") }
                    response { HttpStatusCode.OK to { body<List<StoredCredentialMetadata>>() } }
                }) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@get
                    val credentials = wallet.streamAllCredentials()
                        .toList()
                        .map { it.toMetadata() }
                    call.respond(credentials)
                }

                post("/import", {
                    tags = listOf(CREDENTIALS_TAG)
                    summary = "Import a raw credential directly"
                    request { pathParameter<String>("walletId"); body<ImportCredentialRequest>() }
                    response { HttpStatusCode.Created to { body<StoredCredential>() } }
                }) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                    val req = call.receive<ImportCredentialRequest>()
                    call.respond(
                        HttpStatusCode.Created,
                        WalletCredentialHandler.importCredential(wallet, req),
                    )
                }

                route("/{credentialId}") {

                    get("", {
                        tags = listOf(CREDENTIALS_TAG)
                        summary = "Get a credential including raw credential data"
                        request { pathParameter<String>("walletId"); pathParameter<String>("credentialId") }
                        response {
                            HttpStatusCode.OK to { body<StoredCredential>() }
                            HttpStatusCode.NotFound to {}
                        }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@get
                        val credentialId = call.parameters["credentialId"]!!
                        val entry = wallet.findCredential(credentialId)
                            ?: return@get call.respond(HttpStatusCode.NotFound, "Credential '$credentialId' not found")
                        call.respond(entry)
                    }

                    delete("", {
                        tags = listOf(CREDENTIALS_TAG)
                        summary = "Delete a credential"
                        request { pathParameter<String>("walletId"); pathParameter<String>("credentialId") }
                        response { HttpStatusCode.NoContent to {}; HttpStatusCode.NotFound to {} }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@delete
                        val credentialId = call.parameters["credentialId"]!!
                        val removed = wallet.credentialStores.any { it.removeCredential(credentialId) }
                        if (removed) call.respond(HttpStatusCode.NoContent)
                        else call.respond(HttpStatusCode.NotFound, "Credential '$credentialId' not found")
                    }
                }

                // --- Issuance (OpenID4VCI 1.0) ---

                route("/receive", { tags = listOf("Issuance (OpenID4VCI 1.0)") }) {

                    post("", {
                        summary = "Receive credential(s) - full pre-authorized code flow"
                        description =
                            "Resolves the offer, requests a token, signs proof-of-possession, " +
                                    "fetches the credential(s) and stores them. " +
                                    "Returns a stream of stored credentials as they arrive."
                        request { pathParameter<String>("walletId"); body<ReceiveCredentialRequest>() }
                        response {
                            HttpStatusCode.OK to { body<ReceiveCredentialResult>() }
                            HttpStatusCode.BadRequest to { description = "Token request failed (e.g. wrong PIN / tx_code)" }
                        }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<ReceiveCredentialRequest>()
                        try {
                            val result = WalletIssuanceHandler.receiveCredential(
                                wallet = wallet,
                                request = req,
                                attestationAssembler = attestationAssembler,
                            )
                            call.respond(result)
                        } catch (e: Exception) {
                            // TokenRequestBuilder throws a plain Exception with this prefix when the
                            // token endpoint returns 4xx (e.g. wrong PIN / tx_code). Reclassify as
                            // IllegalArgumentException so the status pages handler maps it to 400.
                            // TODO: Replace once TokenRequestBuilder throws a typed exception.
                            if (e.message?.startsWith("Token request failed") == true) {
                                throw IllegalArgumentException(e.message, e)
                            }
                            throw e
                        }
                    }

                    post("/resolve-offer", {
                        summary = "Isolated: resolve a credential offer"
                        request { pathParameter<String>("walletId"); body<ResolveOfferRequest>() }
                        response { HttpStatusCode.OK to { body<ResolveOfferResult>() } }
                    }) {
                        val req = call.receive<ResolveOfferRequest>()
                        call.respond(WalletIssuanceHandler.resolveOffer(req))
                    }

                    post("/request-token", {
                        summary = "Isolated: exchange pre-authorized code for access token"
                        request { pathParameter<String>("walletId"); body<RequestTokenRequest>() }
                        response { HttpStatusCode.OK to { body<RequestTokenResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<RequestTokenRequest>()
                        call.respond(
                            WalletIssuanceHandler.requestToken(
                                wallet = wallet,
                                request = req,
                                attestationAssembler = attestationAssembler,
                            )
                        )
                    }

                    post("/sign-proof", {
                        summary = "Isolated: sign a proof-of-possession JWT"
                        request { pathParameter<String>("walletId"); body<SignProofRequest>() }
                        response { HttpStatusCode.OK to { body<SignProofResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<SignProofRequest>()
                        call.respond(WalletIssuanceHandler.signProof(wallet, req))
                    }

                    post("/fetch-credential", {
                        summary = "Isolated: fetch a credential from the issuer's credential endpoint"
                        description = "When storeInWallet is true the fetched credential(s) are automatically " +
                                "stored in the wallet, removing the need to call the import endpoint afterwards."
                        request { pathParameter<String>("walletId"); body<FetchCredentialRequest>() }
                        response { HttpStatusCode.OK to { body<FetchCredentialResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<FetchCredentialRequest>()
                        call.respond(WalletIssuanceHandler.fetchCredential(wallet, req))
                    }

                    // Auth-code grant isolated steps

                    post("/authorization-url", {
                        summary = "Auth-code grant: generate authorization redirect URL"
                        description =
                            "Resolves the offer and builds the OAuth authorization URL. " +
                            "The caller must redirect to this URL and capture the returned code. " +
                            "The response includes continuation data for later token and credential requests."
                        request { pathParameter<String>("walletId"); body<GenerateAuthorizationUrlRequest>() }
                        response { HttpStatusCode.OK to { body<GenerateAuthorizationUrlResult>() } }
                    }) {
                        val req = call.receive<GenerateAuthorizationUrlRequest>()
                        call.respond(WalletIssuanceHandler.generateAuthorizationUrl(req))
                    }

                    post("/exchange-code", {
                        summary = "Auth-code grant: exchange authorization code for access token"
                        description =
                            "Exchanges the authorization code for an access token. " +
                            "credentialIssuerBaseUrl is used to resolve authorization server metadata. " +
                            "If attestation is configured and supported by the issuer, the wallet key is used " +
                            "to create token endpoint client authentication headers."
                        request { pathParameter<String>("walletId"); body<ExchangeCodeRequest>() }
                        response { HttpStatusCode.OK to { body<RequestTokenResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<ExchangeCodeRequest>()
                        call.respond(
                            WalletIssuanceHandler.exchangeCode(
                                wallet = wallet,
                                request = req,
                                attestationAssembler = attestationAssembler,
                            )
                        )
                    }

                    post("/deferred", {
                        summary = "Poll deferred credential endpoint"
                        description =
                            "Polls the issuer's deferred credential endpoint for a previously " +
                                    "deferred credential. Returns immediately with empty list if still pending."
                        request { pathParameter<String>("walletId"); body<PollDeferredRequest>() }
                        response { HttpStatusCode.OK to { body<ReceiveCredentialResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<PollDeferredRequest>()
                        val ids = mutableListOf<String>()
                        WalletIssuanceHandler.pollDeferredFlow(wallet, req)
                            .collect { ids += it.id }
                        call.respond(ReceiveCredentialResult(credentialIds = ids))
                    }
                }

                // --- Presentation (OpenID4VP 1.0, DCQL only) ---

                route("/present", { tags = listOf("Presentation (OpenID4VP 1.0)") }) {

                    post("", {
                        summary = "Present credential(s) — full DCQL flow"
                        description =
                            "Resolves the VP request, DCQL-matches credentials from the wallet " +
                                    "stores, signs the presentation(s) and submits to the verifier."
                        request { pathParameter<String>("walletId"); body<PresentCredentialRequest>() }
                        response { HttpStatusCode.OK to { body<id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<PresentCredentialRequest>()
                        call.respond(WalletPresentationHandler.presentCredential(wallet, req))
                    }

                    post("/isolated", {
                        summary = "Present credential(s) — stateless, inline credentials"
                        request { pathParameter<String>("walletId"); body<PresentCredentialIsolatedRequest>() }
                        response { HttpStatusCode.OK to { body<id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<PresentCredentialIsolatedRequest>()
                        call.respond(WalletPresentationHandler.presentCredentialIsolated(wallet, req))
                    }

                    post("/resolve-request", {
                        summary = "Isolated: parse and resolve a VP authorization request"
                        request { pathParameter<String>("walletId"); body<ResolveVpRequestRequest>() }
                        response { HttpStatusCode.OK to { body<ResolveVpRequestResult>() } }
                    }) {
                        val req = call.receive<ResolveVpRequestRequest>()
                        call.respond(WalletPresentationHandler.resolveRequest(req))
                    }

                    post("/match-credentials", {
                        summary = "Isolated: DCQL-match supplied credentials against a query"
                        description = "Stateless — caller provides credentials inline. " +
                                "Use /match-credentials-from-store to match against the wallet's own credential stores."
                        request { pathParameter<String>("walletId"); body<MatchCredentialsRequest>() }
                        response { HttpStatusCode.OK to { body<MatchCredentialsResult>() } }
                    }) {
                        val req = call.receive<MatchCredentialsRequest>()
                        call.respond(WalletPresentationHandler.matchCredentials(req))
                    }

                    post("/match-credentials-from-store", {
                        summary = "DCQL-match wallet's stored credentials against a query"
                        description = "Loads credentials from the wallet's credential stores and runs DCQL " +
                                "matching — no credentials need to be supplied inline. " +
                                "Use this for consent-preview UIs: show the user what will be shared before calling /present."
                        request { pathParameter<String>("walletId"); body<MatchCredentialsFromStoreRequest>() }
                        response { HttpStatusCode.OK to { body<MatchCredentialsResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<MatchCredentialsFromStoreRequest>()
                        call.respond(WalletPresentationHandler.matchCredentialsFromStore(wallet, req))
                    }

                    post("/build-vp-token", {
                        summary = "Build VP token from selected credentials"
                        description =
                            "Step 3 of the manual presentation flow. " +
                                    "Takes the resolved authorization request (from /resolve-request) and the " +
                                    "credential IDs selected by the user (from /match-credentials-from-store), " +
                                    "then builds and signs the vp_token. " +
                                    "Pass the result to /send-response to complete the flow."
                        request { pathParameter<String>("walletId"); body<BuildVpTokenRequest>() }
                        response { HttpStatusCode.OK to { body<BuildVpTokenResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<BuildVpTokenRequest>()
                        call.respond(WalletPresentationHandler.buildVpToken(wallet, req))
                    }

                    post("/send-response", {
                        summary = "Send authorization response to the verifier"
                        description =
                            "Step 4 of the manual presentation flow. " +
                                    "Transmits the vp_token (from /build-vp-token) to the verifier " +
                                    "according to the response_mode in the authorization request."
                        request { pathParameter<String>("walletId"); body<SendAuthorizationResponseRequest>() }
                        response { HttpStatusCode.OK to { body<WalletPresentResult>() } }
                    }) {
                        val req = call.receive<SendAuthorizationResponseRequest>()
                        call.respond(WalletPresentationHandler.sendAuthorizationResponse(req))
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Named store management routes  (POST/GET /stores/keys|credentials|dids)
    // -------------------------------------------------------------------------

    internal fun Route.registerNamedStoreRoutes(resolver: WalletResolver) {

        route("/keys") {
            get("", {
                summary = "List named key store IDs"
                response { HttpStatusCode.OK to { body<List<String>>() } }
            }) {
                call.respond(resolver.listKeyStoreIds().toList())
            }
            post("/{storeId}", {
                summary = "Create a named key store"
                request { pathParameter<String>("storeId") }
                response {
                    HttpStatusCode.Created to { description = "Store created" }
                    HttpStatusCode.Conflict to { description = "Store with this ID already exists" }
                }
            }) {
                val storeId = call.parameters["storeId"]!!
                if (resolver.listKeyStoreIds().toList().contains(storeId)) {
                    return@post call.respond(HttpStatusCode.Conflict, "Key store '$storeId' already exists")
                }
                val store = resolver.keyStoreFactory(storeId)
                resolver.storeKeyStore(storeId, store)
                call.respond(HttpStatusCode.Created, mapOf("storeId" to storeId))
            }
        }

        route("/credentials") {
            get("", {
                summary = "List named credential store IDs"
                response { HttpStatusCode.OK to { body<List<String>>() } }
            }) {
                call.respond(resolver.listCredentialStoreIds().toList())
            }
            post("/{storeId}", {
                summary = "Create a named credential store"
                request { pathParameter<String>("storeId") }
                response {
                    HttpStatusCode.Created to { description = "Store created" }
                    HttpStatusCode.Conflict to { description = "Store with this ID already exists" }
                }
            }) {
                val storeId = call.parameters["storeId"]!!
                if (resolver.listCredentialStoreIds().toList().contains(storeId)) {
                    return@post call.respond(HttpStatusCode.Conflict, "Credential store '$storeId' already exists")
                }
                val store = resolver.credentialStoreFactory(storeId)
                resolver.storeCredentialStore(storeId, store)
                call.respond(HttpStatusCode.Created, mapOf("storeId" to storeId))
            }
        }

        route("/dids") {
            get("", {
                summary = "List named DID store IDs"
                response { HttpStatusCode.OK to { body<List<String>>() } }
            }) {
                call.respond(resolver.listDidStoreIds().toList())
            }
            post("/{storeId}", {
                summary = "Create a named DID store"
                request { pathParameter<String>("storeId") }
                response {
                    HttpStatusCode.Created to { description = "Store created" }
                    HttpStatusCode.Conflict to { description = "Store with this ID already exists" }
                }
            }) {
                val storeId = call.parameters["storeId"]!!
                if (resolver.listDidStoreIds().toList().contains(storeId)) {
                    return@post call.respond(HttpStatusCode.Conflict, "DID store '$storeId' already exists")
                }
                val store = resolver.didStoreFactory(storeId)
                resolver.storeDidStore(storeId, store)
                call.respond(HttpStatusCode.Created, mapOf("storeId" to storeId))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun RoutingCall.resolveOrRespond(
        resolver: WalletResolver,
        getAccountId: (suspend RoutingCall.() -> String?)?
    ): Wallet? {
        val walletId = parameters["walletId"]
            ?: run { respond(HttpStatusCode.BadRequest, "Missing walletId path parameter"); return null }
        // Auth enforcement: if auth is enabled, verify the account owns this wallet
        if (getAccountId != null) {
            val accountId = getAccountId()
            if (accountId != null) {
                val ownedIds = resolver.getWalletIdsForAccount(accountId) ?: emptyList()
                if (walletId !in ownedIds) {
                    respond(HttpStatusCode.Forbidden, "Wallet '$walletId' does not belong to this account")
                    return null
                }
            }
        }
        return resolver.resolveWallet(walletId)
            ?: run { respond(HttpStatusCode.NotFound, "Wallet '$walletId' not found"); return null }
    }

    private suspend fun Wallet.toInfoResponse(resolver: WalletResolver) = WalletInfoResponse(
        walletId = id,
        keyStoreCount = keyStores.size,
        credentialStoreCount = credentialStores.size,
        hasDidStore = didStore != null,
        keyStoreIds = keyStores.mapNotNull { resolver.resolveStoreId(it) },
        credentialStoreIds = credentialStores.mapNotNull { resolver.resolveStoreId(it) },
        didStoreId = didStore?.let { resolver.resolveStoreId(it) },
        hasStaticKey = staticKey != null,
        hasStaticDid = staticDid != null,
        defaultKeyId = defaultKeyId,
        defaultDidId = defaultDidId,
    )
}
