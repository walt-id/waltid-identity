package id.walt.trust.service.openapi

import id.walt.trust.model.*
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
        summary = "Load a trust source"
        description = """
            Loads a trust list into the registry. Provide **either** `content` (raw trust list)
            **or** `url` (to fetch from HTTP). The service auto-detects the format:
            
            - **TSL XML** — EU Trusted List XML (ETSI TS 119 612)
            - **LoTE JSON** — EUDI List of Trusted Entities JSON (ETSI TS 119 602)
            - **LoTE XML** — EUDI List of Trusted Entities XML
            
            **Loading from URL (recommended for production):**
            1. Fetches the content via HTTP(S)
            2. Auto-detects the format
            3. Validates XMLDSig signature for TSL sources (if enabled)
            4. Stores entities, services, and identities
            5. Registers the URL for future `refresh` calls
            
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
            to member state trust lists, not actual Trust Service Providers.
            
            Set `validateSignature: false` for unsigned lists or testing.
        """.trimIndent()

        request {
            body<LoadSourceRequest> {
                required = true
                description = "Source load request. Provide either 'content' or 'url'."
                // URL-based examples
                example("Load from URL: Austrian TSL (recommended)") {
                    value = TrustRegistryExamples.loadSourceFromUrlAustriaTsl
                }
                example("Load from URL: Italian TSL (large)") {
                    value = TrustRegistryExamples.loadSourceFromUrlItalyTsl
                }
                example("Load from URL: EWC Pilot (Wallet/PID/EAA)") {
                    value = TrustRegistryExamples.loadSourceFromUrlEwcPilot
                }
                example("Load from URL: EU LoTL (pointers only)") {
                    value = TrustRegistryExamples.loadSourceFromUrlEuLotl
                }
                // Content-based examples
                example("Load from content: LoTE JSON") {
                    value = TrustRegistryExamples.loadSourceLoteJson
                }
                example("Load from content: TSL XML") {
                    value = TrustRegistryExamples.loadSourceTslXml
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
                    example("Successful load from content") {
                        value = TrustRegistryExamples.refreshResultSuccess
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
                    example("Parse failure") {
                        value = TrustRegistryExamples.refreshResultFailure
                    }
                }
            }
            HttpStatusCode.BadRequest to {
                description = "Neither 'content' nor 'url' provided"
                body<String> {
                    example("Missing parameter") {
                        value = "Provide either 'content' or 'url'"
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
