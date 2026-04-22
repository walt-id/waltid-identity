package id.walt.trust.service.openapi

import id.walt.trust.model.*
import id.walt.trust.service.routes.LoadSourceFromUrlRequest
import id.walt.trust.service.routes.LoadSourceRequest
import id.walt.trust.service.routes.ResolveCertificateRequest
import id.walt.trust.service.routes.ResolveProviderRequest
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*

object TrustRegistryDocs {

    // ---------------------------------------------------------------------------
    // Resolve: Certificate
    // ---------------------------------------------------------------------------

    fun resolveCertificateDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Trust Resolution")
        summary = "Resolve trust for a certificate"
        description = """
            Determines whether a certificate is trusted by looking it up across all loaded trust sources.
            
            Provide **either** `certificateSha256Hex` (preferred, fastest) **or** `certificatePemOrDer` (PEM or
            base64-encoded DER). If both are given, the SHA-256 hex path takes precedence.
            
            Optionally filter by `expectedEntityType` (e.g. WALLET_PROVIDER, PID_PROVIDER) and/or
            `expectedServiceType` to narrow the resolution scope.
            
            The `instant` parameter controls the point in time for freshness evaluation. If omitted,
            the current server time is used.
        """.trimIndent()

        request {
            body<ResolveCertificateRequest> {
                required = true
                description = "Certificate resolution request"
                example("Resolve by SHA-256 fingerprint") {
                    value = TrustRegistryExamples.resolveBySha256
                }
                example("Resolve by PEM certificate") {
                    value = TrustRegistryExamples.resolveByPem
                }
                example("Resolve with entity type filter") {
                    value = TrustRegistryExamples.resolveWithEntityFilter
                }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Trust decision result"
                body<TrustDecision> {
                    example("Trusted") {
                        value = TrustRegistryExamples.trustDecisionTrusted
                    }
                    example("Not trusted") {
                        value = TrustRegistryExamples.trustDecisionNotTrusted
                    }
                    example("Stale source") {
                        value = TrustRegistryExamples.trustDecisionStale
                    }
                }
            }
            HttpStatusCode.BadRequest to {
                description = "Neither certificatePemOrDer nor certificateSha256Hex was provided"
                body<String> {
                    example("Missing parameter") {
                        value = "Provide certificatePemOrDer or certificateSha256Hex"
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Resolve: Public Key
    // ---------------------------------------------------------------------------

    fun resolvePublicKeyDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Trust Resolution")
        summary = "Resolve trust by public key"
        description = """
            Resolves whether a public key is trusted across loaded sources.
            Uses the current server time as the evaluation instant.
        """.trimIndent()

        response {
            HttpStatusCode.OK to {
                description = "Trust decision result"
                body<TrustDecision>()
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Resolve: Provider ID
    // ---------------------------------------------------------------------------

    fun resolveProviderIdDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Trust Resolution")
        summary = "Resolve trust by provider/entity ID"
        description = """
            Determines whether a provider is trusted by looking up its entity ID across all loaded
            trust sources. This is useful when the caller knows the provider identifier (e.g.
            from a credential's `iss` field) but does not have the raw certificate.
            
            Optionally filter by `expectedEntityType` to require a specific role.
        """.trimIndent()

        request {
            body<ResolveProviderRequest> {
                required = true
                description = "Provider resolution request"
                example("Simple lookup") {
                    value = TrustRegistryExamples.resolveProviderSimple
                }
                example("With entity type filter") {
                    value = TrustRegistryExamples.resolveProviderFiltered
                }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Trust decision result"
                body<TrustDecision> {
                    example("Trusted provider") {
                        value = TrustRegistryExamples.trustDecisionTrusted
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Sources: List
    // ---------------------------------------------------------------------------

    fun listSourcesDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Source Management")
        summary = "List all loaded trust sources"
        description = """
            Returns metadata for every trust source currently registered in the service.
            Each source includes its family (TSL, LOTE, PILOT), territory, freshness state,
            authenticity state, and timing information.
        """.trimIndent()

        response {
            HttpStatusCode.OK to {
                description = "List of trust sources"
                body<List<TrustSource>> {
                    example("Two sources loaded") {
                        value = TrustRegistryExamples.sourcesList
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Sources: Load
    // ---------------------------------------------------------------------------

    fun loadSourceDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Source Management")
        summary = "Load a trust source from content"
        description = """
            Parses and loads a trust list into the registry. The service auto-detects the format:
            
            - **TSL XML** — EU Trusted List XML (ETSI TS 119 612)
            - **LoTE JSON** — EUDI List of Trusted Entities JSON (ETSI TS 119 602)
            - **LoTE XML** — EUDI List of Trusted Entities XML
            
            The `sourceId` is a caller-chosen identifier used to reference this source in
            subsequent refresh or health queries. If a source with the same ID already exists,
            it is replaced.
            
            The optional `sourceUrl` is stored as metadata and used as the fetch URL when
            `POST /sources/{sourceId}/refresh` is called later.
        """.trimIndent()

        request {
            body<LoadSourceRequest> {
                required = true
                description = "Source content to load"
                example("Load LoTE JSON source") {
                    value = TrustRegistryExamples.loadSourceLoteJson
                }
                example("Load TSL XML source") {
                    value = TrustRegistryExamples.loadSourceTslXml
                }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Source loaded successfully"
                body<RefreshResult> {
                    example("Successful load") {
                        value = TrustRegistryExamples.refreshResultSuccess
                    }
                }
            }
            HttpStatusCode.UnprocessableEntity to {
                description = "Source could not be parsed or loaded"
                body<RefreshResult> {
                    example("Parse failure") {
                        value = TrustRegistryExamples.refreshResultFailure
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Sources: Load from URL
    // ---------------------------------------------------------------------------

    fun loadSourceFromUrlDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Source Management")
        summary = "Load a trust source from URL"
        description = """
            Fetches and loads a trust list directly from a URL. This is the recommended way to
            load production trust sources. The service will:
            
            1. Fetch the content via HTTP(S)
            2. Auto-detect the format (TSL XML, LoTE JSON, LoTE XML)
            3. Parse and validate the content
            4. Validate XMLDSig signature for TSL sources (if enabled)
            5. Store the entities, services, and identities
            6. Register the URL for future `refresh` calls
            
            **Working EU National Trust Lists:**
            - Austria: `https://www.signatur.rtr.at/currenttl.xml` (fast, ~9 TSPs)
            - Italy: `https://eidas.agid.gov.it/TL/TSL-IT.xml` (large, ~57 TSPs)
            - Belgium: `https://tsl.belgium.be/tsl-be.xml`
            - Finland: `https://dp.trustedlist.fi/fi-tl.xml`
            
            **EUDI Wallet Pilot Trust List (EWC LSP):**
            - EWC: `https://ewc-consortium.github.io/ewc-trust-list/EWC-TL`
              - Contains WALLET_PROVIDER, PID_PROVIDER, ATTESTATION_PROVIDER (EAA)
              - Unsigned (use `validateSignature: false`)
            
            **Note:** The EU LoTL (`eu-lotl.xml`) is a "List of Lists" — it contains pointers
            to member state trust lists, not actual Trust Service Providers. Use national
            TSLs to get actual entities.
            
            Set `validateSignature: false` for testing or when working with unsigned lists.
        """.trimIndent()

        request {
            body<LoadSourceFromUrlRequest> {
                required = true
                description = "URL-based source load request"
                example("Load Austrian TSL (recommended - fast & reliable)") {
                    value = TrustRegistryExamples.loadSourceFromUrlAustriaTsl
                }
                example("Load Italian TSL (large - 57 TSPs)") {
                    value = TrustRegistryExamples.loadSourceFromUrlItalyTsl
                }
                example("Load Belgian TSL") {
                    value = TrustRegistryExamples.loadSourceFromUrlBelgiumTsl
                }
                example("Load EWC Pilot (Wallet/PID/EAA providers)") {
                    value = TrustRegistryExamples.loadSourceFromUrlEwcPilot
                }
                example("Load EU LoTL (pointers only, no TSPs)") {
                    value = TrustRegistryExamples.loadSourceFromUrlEuLotl
                }
                example("Load without signature validation") {
                    value = TrustRegistryExamples.loadSourceFromUrlNoValidation
                }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Source loaded successfully"
                body<RefreshResult> {
                    example("Successful load from Austrian TSL") {
                        value = TrustRegistryExamples.loadSourceFromUrlSuccessAustria
                    }
                    example("Successful load from EWC Pilot") {
                        value = TrustRegistryExamples.loadSourceFromUrlSuccessEwcPilot
                    }
                    example("Successful load from EU LoTL (pointers only)") {
                        value = TrustRegistryExamples.loadSourceFromUrlSuccessEuLotl
                    }
                }
            }
            HttpStatusCode.UnprocessableEntity to {
                description = "Source could not be fetched, parsed, or validated"
                body<RefreshResult> {
                    example("Fetch failure") {
                        value = TrustRegistryExamples.loadSourceFromUrlFetchFailure
                    }
                    example("Signature validation failure") {
                        value = TrustRegistryExamples.loadSourceFromUrlSignatureFailure
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Sources: Refresh
    // ---------------------------------------------------------------------------

    fun refreshSourceDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Source Management")
        summary = "Refresh a trust source"
        description = """
            Re-fetches and re-parses a previously loaded source using its stored URL.
            The source must have been loaded with a `sourceUrl` for refresh to succeed.
        """.trimIndent()

        request {
            pathParameter<String>("sourceId") {
                description = "ID of the source to refresh"
                required = true
                example("Source ID") {
                    value = "eu-wallets"
                }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Source refreshed successfully"
                body<RefreshResult> {
                    example("Successful refresh") {
                        value = TrustRegistryExamples.refreshResultSuccess
                    }
                }
            }
            HttpStatusCode.UnprocessableEntity to {
                description = "Refresh failed (fetch or parse error)"
                body<RefreshResult> {
                    example("Refresh failure") {
                        value = TrustRegistryExamples.refreshResultFailure
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Entities
    // ---------------------------------------------------------------------------

    fun listEntitiesDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Trust Entities")
        summary = "List trusted entities"
        description = """
            Returns all trusted entities across all loaded sources. Results can be filtered
            by query parameters:
            
            - `sourceFamily` — `TSL`, `LOTE`, or `PILOT`
            - `entityType` — `TRUST_SERVICE_PROVIDER`, `WALLET_PROVIDER`, `PID_PROVIDER`,
              `ATTESTATION_PROVIDER`, `ACCESS_CERTIFICATE_PROVIDER`, `RELYING_PARTY_PROVIDER`, `OTHER`
            - `country` — ISO 3166-1 alpha-2 country code (e.g. `AT`, `DE`, `FR`)
            - `onlyCurrentlyTrusted` — if `true`, return only entities that have at least one
              service with an active trust status (GRANTED, RECOGNIZED, ACCREDITED, SUPERVISED)
        """.trimIndent()

        request {
            queryParameter<String?>("sourceFamily") {
                description = "Filter by trust source family"
                required = false
                example("LOTE") { value = "LOTE" }
                example("TSL") { value = "TSL" }
            }
            queryParameter<String?>("entityType") {
                description = "Filter by entity type"
                required = false
                example("Wallet Provider") { value = "WALLET_PROVIDER" }
                example("PID Provider") { value = "PID_PROVIDER" }
            }
            queryParameter<String?>("country") {
                description = "Filter by ISO 3166-1 alpha-2 country code"
                required = false
                example("Austria") { value = "AT" }
                example("Germany") { value = "DE" }
            }
            queryParameter<Boolean?>("onlyCurrentlyTrusted") {
                description = "Only return entities with at least one active trust status"
                required = false
                example("Active only") { value = true }
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "List of trusted entities matching the filter"
                body<List<TrustedEntity>> {
                    example("Wallet providers") {
                        value = TrustRegistryExamples.entitiesList
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Health
    // ---------------------------------------------------------------------------

    fun healthDocs(): RouteConfig.() -> Unit = {
        tags = listOf("Health & Monitoring")
        summary = "Get trust source health status"
        description = """
            Returns health and freshness information for all loaded trust sources.
            This includes entity/service counts, freshness state (FRESH, STALE, EXPIRED),
            authenticity state, and the expected next update time.
            
            Use this endpoint for monitoring and alerting on stale trust data.
        """.trimIndent()

        response {
            HttpStatusCode.OK to {
                description = "Health status for all loaded sources"
                body<List<TrustSourceHealth>> {
                    example("Healthy sources") {
                        value = TrustRegistryExamples.healthResponse
                    }
                }
            }
        }
    }
}
