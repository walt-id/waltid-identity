package id.walt.trust.service.routes

import id.walt.trust.model.*
import id.walt.trust.service.config.TrustRegistryConfig
import id.walt.trust.service.openapi.TrustRegistryDocs
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

fun Application.trustRegistryRoutes() {
//    install(ContentNegotiation) {
//        json()
//    }
    routing {
        route("/trust-registry") {
            resolveRoutes()
            sourceRoutes()
            entityRoutes()
            healthRoutes()
        }
    }
}

// ---------------------------------------------------------------------------
// Request / response models
// ---------------------------------------------------------------------------

@Serializable
data class ResolveCertificateRequest(
    val certificatePemOrDer: String? = null,
    val certificateSha256Hex: String? = null,
    val instant: String? = null,
    val expectedEntityType: TrustedEntityType? = null,
    val expectedServiceType: String? = null
)

@Serializable
data class ResolveProviderRequest(
    val providerId: String,
    val instant: String? = null,
    val expectedEntityType: TrustedEntityType? = null
)

@Serializable
data class LoadSourceRequest(
    val sourceId: String,
    /** Raw content (TSL XML, LoTE JSON/XML). Provide either content OR url, not both. */
    val content: String? = null,
    /** URL to fetch trust list from. Provide either content OR url, not both. */
    val url: String? = null,
    /** Stored for refresh calls when using content; ignored when using url (url is stored instead). */
    val sourceUrl: String? = null,
    /** Validate XMLDSig signature for TSL sources. Default: true */
    val validateSignature: Boolean = true
)

// ---------------------------------------------------------------------------
// Resolve routes
// ---------------------------------------------------------------------------

private fun Route.resolveRoutes() {
    route("/resolve") {
        post("/certificate", TrustRegistryDocs.resolveCertificateDocs()) {
            val req = call.receive<ResolveCertificateRequest>()
            val instant = req.instant?.let { Instant.parse(it) } ?: Clock.System.now()
            val service = TrustRegistryConfig.service

            val decision = when {
                req.certificateSha256Hex != null -> service.resolveByCertificateSha256(
                    sha256Hex = req.certificateSha256Hex,
                    instant = instant,
                    expectedEntityType = req.expectedEntityType,
                    expectedServiceType = req.expectedServiceType
                )
                req.certificatePemOrDer != null -> service.resolveByCertificate(
                    certificatePemOrDer = req.certificatePemOrDer,
                    instant = instant,
                    expectedEntityType = req.expectedEntityType,
                    expectedServiceType = req.expectedServiceType
                )
                else -> {
                    call.respond(HttpStatusCode.BadRequest, "Provide certificatePemOrDer or certificateSha256Hex")
                    return@post
                }
            }

            call.respond(decision)
        }

        post("/public-key", TrustRegistryDocs.resolvePublicKeyDocs()) {
            val instant = Clock.System.now()
            val service = TrustRegistryConfig.service
            val decision = service.resolveByPublicKey("{}", instant)
            call.respond(decision)
        }

        post("/provider-id", TrustRegistryDocs.resolveProviderIdDocs()) {
            val req = call.receive<ResolveProviderRequest>()
            val instant = req.instant?.let { Instant.parse(it) } ?: Clock.System.now()
            val service = TrustRegistryConfig.service

            val decision = service.resolveByProviderId(
                providerId = req.providerId,
                instant = instant,
                expectedEntityType = req.expectedEntityType
            )

            call.respond(decision)
        }
    }
}

// ---------------------------------------------------------------------------
// Source management routes
// ---------------------------------------------------------------------------

private fun Route.sourceRoutes() {
    route("/sources") {
        get(TrustRegistryDocs.listSourcesDocs()) {
            val sources = TrustRegistryConfig.service.listSources()
            call.respond(sources)
        }

        post("/load", TrustRegistryDocs.loadSourceDocs()) {
            val req = call.receive<LoadSourceRequest>()
            
            val result = when {
                req.url != null -> {
                    // Load from URL
                    TrustRegistryConfig.service.loadSourceFromUrl(
                        sourceId = req.sourceId,
                        url = req.url,
                        validateSignature = req.validateSignature
                    )
                }
                req.content != null -> {
                    // Load from content
                    TrustRegistryConfig.service.loadSourceFromContent(
                        sourceId = req.sourceId,
                        content = req.content,
                        sourceUrl = req.sourceUrl,
                        validateSignature = req.validateSignature
                    )
                }
                else -> {
                    call.respond(HttpStatusCode.BadRequest, "Provide either 'content' or 'url'")
                    return@post
                }
            }
            
            if (result.success) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.UnprocessableEntity, result)
            }
        }

        post("/{sourceId}/refresh", TrustRegistryDocs.refreshSourceDocs()) {
            val sourceId = call.parameters["sourceId"]!!
            val result = TrustRegistryConfig.service.refreshSource(sourceId)
            if (result.success) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.UnprocessableEntity, result)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Entity listing routes
// ---------------------------------------------------------------------------

private fun Route.entityRoutes() {
    get("/entities", TrustRegistryDocs.listEntitiesDocs()) {
        val sourceFamily = call.request.queryParameters["sourceFamily"]?.let {
            runCatching { SourceFamily.valueOf(it) }.getOrNull()
        }
        val entityType = call.request.queryParameters["entityType"]?.let {
            runCatching { TrustedEntityType.valueOf(it) }.getOrNull()
        }
        val country = call.request.queryParameters["country"]
        val onlyTrusted = call.request.queryParameters["onlyCurrentlyTrusted"]?.toBoolean() ?: false

        val filter = EntityFilter(
            sourceFamily = sourceFamily,
            entityType = entityType,
            country = country,
            onlyCurrentlyTrusted = onlyTrusted
        )

        val entities = TrustRegistryConfig.service.listTrustedEntities(filter)
        call.respond(entities)
    }
}

// ---------------------------------------------------------------------------
// Health routes
// ---------------------------------------------------------------------------

private fun Route.healthRoutes() {
    get("/health", TrustRegistryDocs.healthDocs()) {
        val health = TrustRegistryConfig.service.getSourceHealth()
        call.respond(health)
    }
}
