package id.walt.wallet2.server.handlers

import id.walt.credentials.CredentialParser
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidService
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.StoredCredentialMetadata
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.handlers.ExchangeCodeRequest
import id.walt.wallet2.handlers.FetchCredentialRequest
import id.walt.wallet2.handlers.FetchCredentialResult
import id.walt.wallet2.handlers.GenerateAuthorizationUrlRequest
import id.walt.wallet2.handlers.GenerateAuthorizationUrlResult
import id.walt.wallet2.handlers.PollDeferredRequest
import id.walt.wallet2.handlers.MatchCredentialsRequest
import id.walt.wallet2.handlers.MatchCredentialsResult
import id.walt.wallet2.handlers.PresentCredentialIsolatedRequest
import id.walt.wallet2.handlers.PresentCredentialRequest
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import id.walt.wallet2.handlers.ReceiveCredentialResult
import id.walt.wallet2.handlers.RequestTokenRequest
import id.walt.wallet2.handlers.RequestTokenResult
import id.walt.wallet2.handlers.ResolveOfferRequest
import id.walt.wallet2.handlers.ResolveOfferResult
import id.walt.wallet2.handlers.ResolveVpRequestRequest
import id.walt.wallet2.handlers.ResolveVpRequestResult
import id.walt.wallet2.handlers.SignProofRequest
import id.walt.wallet2.handlers.SignProofResult
import id.walt.wallet2.handlers.WalletIssuanceHandler
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.walt.wallet2.server.WalletResolver
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
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
    val hasStaticDid: Boolean
)

@Serializable
data class GenerateKeyRequest(
    /** Key type: Ed25519, secp256r1, secp256k1, RSA. Defaults to Ed25519. */
    val keyType: String = "Ed25519"
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

@Serializable
data class ImportCredentialRequest(
    /** Raw credential string (JWT, SD-JWT, base64url mdoc, etc.). */
    val rawCredential: String,
    val label: String? = null
)

// ---------------------------------------------------------------------------
// Route handler
// ---------------------------------------------------------------------------

/**
 * Shared Ktor route handler for the Wallet2 API.
 *
 * Both the OSS service and the Enterprise service call [registerWallet2Routes],
 * providing their own [WalletResolver]. All HTTP logic lives here.
 */
@OptIn(ExperimentalUuidApi::class)
object Wallet2RouteHandler {

    private val noopOnEvent: suspend (WalletSessionEvent) -> Unit = {}

    fun Route.registerWallet2Routes(
        resolver: WalletResolver,
        /**
         * Optional: extract the authenticated account ID from the current call.
         * When provided, wallet routes check that the authenticated account owns
         * the requested wallet. When null, all wallets are accessible (dev / no-auth mode).
         */
        getAccountId: (suspend RoutingCall.() -> String?)? = null
    ) {
        route("/wallet", { tags = listOf("Wallet Management") }) {
            registerWalletManagementRoutes(resolver, getAccountId)
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
        getAccountId: (suspend RoutingCall.() -> String?)?
    ) {

        post("", {
            summary = "Create a new wallet"
            description =
                "Creates a wallet with optional named stores. " +
                "Omitting store IDs auto-creates one in-memory store of each type."
            request { body<CreateWalletRequest> { required = false } }
            response {
                HttpStatusCode.Created to { body<WalletCreatedResponse>() }
            }
        }) {
            val req = runCatching { call.receive<CreateWalletRequest>() }
                .getOrElse { CreateWalletRequest() }
            val id = Uuid.random().toString()

            val keyStores: List<WalletKeyStore> = when {
                req.keyStoreIds != null ->
                    req.keyStoreIds.map {
                        resolver.resolveKeyStore(it) ?: error("Key store '$it' not found")
                    }
                req.staticKey != null -> emptyList()
                else -> listOf(InMemoryKeyStore())
            }

            val credentialStores: List<WalletCredentialStore> = when {
                req.credentialStoreIds != null ->
                    req.credentialStoreIds.map {
                        resolver.resolveCredentialStore(it) ?: error("Credential store '$it' not found")
                    }
                else -> listOf(InMemoryCredentialStore())
            }

            val didStore: WalletDidStore? = when {
                req.noDidStore -> null
                req.didStoreId != null ->
                    resolver.resolveDidStore(req.didStoreId) ?: error("DID store '${req.didStoreId}' not found")
                req.staticDid != null -> null
                else -> InMemoryDidStore()
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
            summary = "List wallet IDs"
            description = "When auth is enabled, returns only wallets owned by the authenticated account."
            response { HttpStatusCode.OK to { body<List<String>>() } }
        }) {
            val ids = if (getAccountId != null) {
                val accountId = call.getAccountId()
                if (accountId != null) resolver.getWalletIdsForAccount(accountId) ?: resolver.listWalletIds()
                else resolver.listWalletIds()
            } else {
                resolver.listWalletIds()
            }
            call.respond(ids)
        }

        route("/{walletId}") {

            get("", {
                summary = "Get wallet info"
                request { pathParameter<String>("walletId") }
                response {
                    HttpStatusCode.OK to { body<WalletInfoResponse>() }
                    HttpStatusCode.NotFound to { description = "Wallet not found" }
                }
            }) {
                val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@get
                call.respond(wallet.toInfoResponse())
            }

            delete("", {
                summary = "Delete a wallet"
                request { pathParameter<String>("walletId") }
                response {
                    HttpStatusCode.NoContent to { description = "Deleted" }
                    HttpStatusCode.NotFound to { description = "Wallet not found" }
                }
            }) {
                val walletId = call.parameters["walletId"] ?: return@delete
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

                post("/generate", {
                    summary = "Generate a new key"
                    request { pathParameter<String>("walletId"); body<GenerateKeyRequest>() }
                    response { HttpStatusCode.Created to { body<WalletKeyInfo>() } }
                }) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                    val req = call.receive<GenerateKeyRequest>()
                    val keyType = KeyType.valueOf(req.keyType)
                    val key = id.walt.crypto.keys.jwk.JWKKey.generate(keyType)
                    val store = wallet.keyStores.firstOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Wallet has no key stores")
                    val keyId = store.addKey(key)
                    call.respond(HttpStatusCode.Created, WalletKeyInfo(keyId = keyId, keyType = keyType.name))
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

                post("/create", {
                    summary = "Create a DID"
                    request { pathParameter<String>("walletId"); body<CreateDidRequest>() }
                    response { HttpStatusCode.Created to { body<WalletDidEntry>() } }
                }) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                    val req = call.receive<CreateDidRequest>()
                    val didStore = wallet.didStore
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Wallet has no DID store")
                    val key = (req.keyId?.let { wallet.findKey(it) } ?: wallet.defaultKey())
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "No key available for DID creation")
                    val did = DidService.registerByKey(req.method, key)
                    val entry = WalletDidEntry(did = did.did, document = did.didDocument.toString())
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
                    val entry = WalletDidEntry(did = req.did, document = req.document)
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
                }
            }

            // -----------------------------------------------------------------
            // Credential management + issuance + presentation
            // -----------------------------------------------------------------

            route("/credentials", { tags = listOf("Credentials") }) {

                get("", {
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
                    summary = "Import a raw credential directly"
                    request { pathParameter<String>("walletId"); body<ImportCredentialRequest>() }
                    response { HttpStatusCode.Created to { body<StoredCredential>() } }
                }) {
                    val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                    val req = call.receive<ImportCredentialRequest>()
                    val (_, parsed) = CredentialParser.detectAndParse(req.rawCredential)
                    val entry = StoredCredential(
                        id = Uuid.random().toString(),
                        credential = parsed,
                        label = req.label,
                        addedAt = Clock.System.now()
                    )
                    wallet.addCredential(entry)
                    call.respond(HttpStatusCode.Created, entry)
                }

                route("/{credentialId}") {

                    get("", {
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
                        summary = "Receive credential(s) — full pre-authorized code flow"
                        description =
                            "Resolves the offer, requests a token, signs proof-of-possession, " +
                            "fetches the credential(s) and stores them. " +
                            "Returns a stream of stored credentials as they arrive."
                        request { pathParameter<String>("walletId"); body<ReceiveCredentialRequest>() }
                        response { HttpStatusCode.OK to { body<ReceiveCredentialResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<ReceiveCredentialRequest>()
                        val result = WalletIssuanceHandler.receiveCredential(wallet, req, noopOnEvent)
                        call.respond(result)
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
                        val req = call.receive<RequestTokenRequest>()
                        call.respond(WalletIssuanceHandler.requestToken(req))
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
                        request { pathParameter<String>("walletId"); body<FetchCredentialRequest>() }
                        response { HttpStatusCode.OK to { body<FetchCredentialResult>() } }
                    }) {
                        val req = call.receive<FetchCredentialRequest>()
                        call.respond(WalletIssuanceHandler.fetchCredential(req))
                    }

                    // Auth-code grant isolated steps

                    post("/authorization-url", {
                        summary = "Auth-code grant: generate authorization redirect URL"
                        description =
                            "Resolves the offer and builds the OAuth authorization URL. " +
                            "The caller must redirect to this URL and capture the returned code."
                        request { pathParameter<String>("walletId"); body<GenerateAuthorizationUrlRequest>() }
                        response { HttpStatusCode.OK to { body<GenerateAuthorizationUrlResult>() } }
                    }) {
                        val req = call.receive<GenerateAuthorizationUrlRequest>()
                        call.respond(WalletIssuanceHandler.generateAuthorizationUrl(req))
                    }

                    post("/exchange-code", {
                        summary = "Auth-code grant: exchange authorization code for access token"
                        request { pathParameter<String>("walletId"); body<ExchangeCodeRequest>() }
                        response { HttpStatusCode.OK to { body<RequestTokenResult>() } }
                    }) {
                        val req = call.receive<ExchangeCodeRequest>()
                        call.respond(WalletIssuanceHandler.exchangeCode(req))
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
                        WalletIssuanceHandler.pollDeferredFlow(wallet, req, noopOnEvent)
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
                        call.respond(WalletPresentationHandler.presentCredential(wallet, req, noopOnEvent))
                    }

                    post("/isolated", {
                        summary = "Present credential(s) — stateless, inline credentials"
                        request { pathParameter<String>("walletId"); body<PresentCredentialIsolatedRequest>() }
                        response { HttpStatusCode.OK to { body<id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult>() } }
                    }) {
                        val wallet = call.resolveOrRespond(resolver, getAccountId) ?: return@post
                        val req = call.receive<PresentCredentialIsolatedRequest>()
                        call.respond(WalletPresentationHandler.presentCredentialIsolated(wallet, req, noopOnEvent))
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
                        request { pathParameter<String>("walletId"); body<MatchCredentialsRequest>() }
                        response { HttpStatusCode.OK to { body<MatchCredentialsResult>() } }
                    }) {
                        val req = call.receive<MatchCredentialsRequest>()
                        call.respond(WalletPresentationHandler.matchCredentials(req))
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
                call.respond(resolver.listKeyStoreIds())
            }
            post("/{storeId}", {
                summary = "Create a named key store"
                request { pathParameter<String>("storeId") }
                response { HttpStatusCode.Created to { description = "Store created" } }
            }) {
                val storeId = call.parameters["storeId"]!!
                resolver.storeKeyStore(storeId, InMemoryKeyStore())
                call.respond(HttpStatusCode.Created, mapOf("storeId" to storeId))
            }
        }

        route("/credentials") {
            get("", {
                summary = "List named credential store IDs"
                response { HttpStatusCode.OK to { body<List<String>>() } }
            }) {
                call.respond(resolver.listCredentialStoreIds())
            }
            post("/{storeId}", {
                summary = "Create a named credential store"
                request { pathParameter<String>("storeId") }
                response { HttpStatusCode.Created to { description = "Store created" } }
            }) {
                val storeId = call.parameters["storeId"]!!
                resolver.storeCredentialStore(storeId, InMemoryCredentialStore())
                call.respond(HttpStatusCode.Created, mapOf("storeId" to storeId))
            }
        }

        route("/dids") {
            get("", {
                summary = "List named DID store IDs"
                response { HttpStatusCode.OK to { body<List<String>>() } }
            }) {
                call.respond(resolver.listDidStoreIds())
            }
            post("/{storeId}", {
                summary = "Create a named DID store"
                request { pathParameter<String>("storeId") }
                response { HttpStatusCode.Created to { description = "Store created" } }
            }) {
                val storeId = call.parameters["storeId"]!!
                resolver.storeDidStore(storeId, InMemoryDidStore())
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
                val ownedIds = resolver.getWalletIdsForAccount(accountId)
                if (ownedIds != null && walletId !in ownedIds) {
                    respond(HttpStatusCode.Forbidden, "Wallet '$walletId' does not belong to this account")
                    return null
                }
            }
        }
        return resolver.resolveWallet(walletId)
            ?: run { respond(HttpStatusCode.NotFound, "Wallet '$walletId' not found"); return null }
    }

    private fun Wallet.toInfoResponse() = WalletInfoResponse(
        walletId = id,
        keyStoreCount = keyStores.size,
        credentialStoreCount = credentialStores.size,
        hasDidStore = didStore != null,
        hasStaticKey = staticKey != null,
        hasStaticDid = staticDid != null
    )
}
