package id.walt.issuer2.service.openid4vci

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.commons.persistence.Persistence
import id.walt.issuer2.config.Issuer2EndpointPaths
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.handlers.credential.SdJwtVcMetadataContext
import id.walt.openid4vci.handlers.credential.SdJwtVcMetadataResolver
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.claimDescriptionConflicts
import id.walt.sdjwt.metadata.type.*
import id.walt.webdatafetching.BoundedDocumentSession
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.net.URI

/** Application-owned configuration, retained publications and transport shared by routes and issuance. */
class IssuerTypeMetadataCatalogue(
    serviceConfig: Issuer2ServiceConfig,
    metadataConfig: Issuer2MetadataConfig,
    private val store: Persistence<String> = ConfiguredPersistence("issuer2_type_metadata", { it }, { it }),
    private val fetcher: WebDataFetcher = WebDataFetcher(WebDataFetcherId.VCT_INTEGRITY_POLICY),
) : SdJwtVcMetadataResolver, AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val issuerHttpBaseUrl = serviceConfig.baseUrl.trimEnd('/')
    private val baseUrl = serviceConfig.openId4VciBaseUrl()
    private val scope = "${baseUrl.length}:$baseUrl:"
    private val mutex = Mutex()

    val credentialConfigurations: Map<String, CredentialConfiguration> =
        metadataConfig.credentialConfigurations.mapValues { (configurationId, value) ->
            json.decodeFromJsonElement(
                CredentialConfiguration.serializer(),
                DisplayUriResolver.resolve(value, issuerHttpBaseUrl),
            ).withResolvedVct(configurationId).also { configuration ->
                configuration.vct?.let { validateVct(configurationId, it) }
                val claimConflicts = configuration.claimDescriptionConflicts()
                require(claimConflicts.isEmpty()) {
                    "Credential configuration '$configurationId' has repeated or contradictory claim descriptions " +
                        "(OpenID4VCI Appendix B.3): ${claimConflicts.joinToString("; ") { it.message }}"
                }
            }
        }

    private val configuredTypeMetadata: Map<String, SdJwtVcTypeMetadataDraft04> = buildMap {
        metadataConfig.sdJwtVcTypeMetadataConfiguration.forEach { (configurationId, metadata) ->
            val configuration = requireNotNull(credentialConfigurations[configurationId]) {
                "Type metadata references unknown credential configuration '$configurationId'"
            }
            require(configuration.format == CredentialFormat.SD_JWT_VC) {
                "Type metadata requires an SD-JWT credential configuration: '$configurationId'"
            }
            val vct = requireNotNull(configuration.vct) { "SD-JWT configuration '$configurationId' requires a VCT" }
            val suppliedVct =
                metadata.vct?.let { if (it == INTERNAL_VCT_BASE_URL) selfHostedVct(configurationId) else it }
            require(suppliedVct == null || suppliedVct == vct) {
                "Type metadata VCT does not match credential configuration '$configurationId'"
            }
            val resolved = metadata.copy(vct = vct)
            val previous = put(vct, resolved)
            require(previous == null || previous == resolved) { "Conflicting type metadata for VCT '$vct'" }
        }
    }

    private val hostedSources = buildMap {
        for (configuration in credentialConfigurations.values) {
            if (configuration.format != CredentialFormat.SD_JWT_VC) continue
            val vct = configuration.vct ?: continue
            val name = hostedTypeName(vct) ?: continue
            val source = (configuredTypeMetadata[vct] ?: defaultTypeMetadata(name, vct)).toJSON()
            val previous = put(vct, source)
            require(previous == null || previous == source) { "Conflicting type metadata for one VCT" }
        }
    }

    suspend fun publications(): Map<String, TypeMetadataPublication> = mutex.withLock {
        repeat(8) {
            val previous = store.listAllKeys().filter { it.startsWith(scope) }.associate { key ->
                val record = TypeMetadataPublication.restore(Json.parseToJsonElement(requireNotNull(store[key])).jsonObject)
                record.published.vct to record
            }
            val next = BoundedDocumentSession(fetcher, initialCredentialOrigin = baseUrl).use { documents ->
                TypeMetadataPublisher(documents::read).retain(hostedSources, previous)
            }
            var raced = false
            for ((vct, record) in next) if (vct !in previous &&
                !store.compareAndSet(scope + vct, null, record.snapshot().toString())) {
                raced = true
                break
            }
            if (!raced) return@withLock next
        }
        throw TypeMetadataException(TypeMetadataFailure.UNAVAILABLE, "Concurrent type publication could not complete")
    }

    suspend fun publication(credentialType: String): PublishedTypeMetadata {
        if (credentialType in Issuer2EndpointPaths.reservedVctNames) throw NotFoundException("Credential type metadata not found")
        val types = publications()
        return (types[credentialType] ?: types[selfHostedVct(credentialType)])?.published
            ?: throw NotFoundException("Credential type metadata not found")
    }

    override suspend fun resolve(configuration: CredentialConfiguration): SdJwtVcMetadataContext? {
        val vct = configuration.vct ?: return null
        if (configuration.format != CredentialFormat.SD_JWT_VC) return null
        if (hostedTypeName(vct) == null && vct !in configuredTypeMetadata) return null
        val documents = BoundedDocumentSession(fetcher, initialCredentialOrigin = baseUrl)
        try {
            val retained = publications()[vct]
            val resolved = retained?.resolved ?: TypeMetadataResolver(documents::read).resolve(vct)
            val published = retained?.published ?: PublishedTypeMetadata.fromRetrieved(vct, resolved.documents.first().bytes())
            return object : SdJwtVcMetadataContext {
                override val publication = published
                override val resolved = resolved
                override suspend fun readDocument(uri: String) = documents.read(uri)
                override fun close() = documents.close()
            }
        } catch (cause: CancellationException) {
            documents.close()
            throw cause
        } catch (cause: TypeMetadataException) {
            documents.close()
            throw cause
        } catch (cause: Exception) {
            documents.close()
            throw TypeMetadataException(TypeMetadataFailure.INVALID_METADATA, "Issuer metadata preparation failed", cause)
        }
    }

    override fun close() {
        try { fetcher.close() } finally { (store as? AutoCloseable)?.close() }
    }

    fun getTypeMetadata(credentialType: String): SdJwtVcTypeMetadataDraft04 {
        if (credentialType in Issuer2EndpointPaths.reservedVctNames) {
            throw NotFoundException("Credential type metadata not found: $credentialType")
        }
        val hostedVct = selfHostedVct(credentialType)
        val resolvedVct = credentialConfigurations.values.firstNotNullOfOrNull { configuration ->
            val vct = configuration.vct ?: return@firstNotNullOfOrNull null
            when {
                vct == hostedVct -> vct
                vct == credentialType && vctKind(vct) == VctKind.URN -> vct
                else -> null
            }
        } ?: throw NotFoundException("Credential type metadata not found: $credentialType")

        return configuredTypeMetadata[resolvedVct] ?: defaultTypeMetadata(credentialType, resolvedVct)
    }

    private fun CredentialConfiguration.withResolvedVct(credentialType: String): CredentialConfiguration =
        if (vct == INTERNAL_VCT_BASE_URL) copy(vct = selfHostedVct(credentialType)) else this

    private fun hostedTypeName(vct: String): String? = when {
        vctKind(vct) == VctKind.URN -> vct
        vct.startsWith("$baseUrl/") -> vct.removePrefix("$baseUrl/").takeIf { name ->
            name.isNotBlank() && name.none { it in "/?#" }
        }
        else -> null
    }

    private fun selfHostedVct(credentialType: String): String =
        "$baseUrl/$credentialType"

    private fun validateVct(configurationId: String, vct: String) {
        require(vct.isNotBlank()) {
            "Credential configuration '$configurationId' requires a non-blank vct"
        }
        when (vctKind(vct)) {
            VctKind.HTTP -> validateSelfHostedVct(configurationId, vct)
            VctKind.URN -> Unit
            null -> throw IllegalArgumentException(
                "Credential configuration '$configurationId' has invalid vct '$vct'. " +
                    "Use an http(s) URL with a host, or a URN such as urn:eudi:pid:1."
            )
        }
    }

    /**
     * Classifies a resolved vct. HTTP(S) requires a hierarchical URI with a host, compared
     * case-insensitively on the scheme. A URN requires a namespace identifier and a non-empty
     * namespace-specific string. Anything else, including a bare string or `https:foo`, is rejected.
     */
    private fun vctKind(vct: String): VctKind? {
        if (vct.isBlank()) return null
        val uri = runCatching { URI(vct) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        return when {
            scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true) ->
                if (!uri.isOpaque && !uri.host.isNullOrBlank()) VctKind.HTTP else null
            scheme.equals("urn", ignoreCase = true) && URN_WITH_NAMESPACE.matches(uri.schemeSpecificPart.orEmpty()) ->
                VctKind.URN
            else -> null
        }
    }

    private fun defaultTypeMetadata(credentialType: String, resolvedVct: String): SdJwtVcTypeMetadataDraft04 =
        SdJwtVcTypeMetadataDraft04(
            vct = resolvedVct,
            name = credentialType,
            description = "$credentialType Verifiable Credential",
        )

    private fun validateSelfHostedVct(configurationId: String, vct: String) {
        // Only HTTP(S) VCTs that share this issuer's origin can collide with protocol routes.
        val vctUri = runCatching { URI(vct) }.getOrNull() ?: return
        val issuerUri = URI(baseUrl)
        if (!vctUri.scheme.equals(issuerUri.scheme, ignoreCase = true) ||
            !vctUri.host.equals(issuerUri.host, ignoreCase = true) ||
            vctUri.effectivePort() != issuerUri.effectivePort()
        ) return

        val prefix = issuerUri.normalize().path.trimEnd('/') + "/"
        val path = vctUri.normalize().path ?: return
        if (!path.startsWith(prefix)) return
        val name = path.removePrefix(prefix).substringBefore('/')
        require(name !in Issuer2EndpointPaths.reservedVctNames) {
            "Credential configuration '$configurationId' has self-hosted VCT '$vct' under reserved " +
                    "OpenID4VCI path '$name'. Choose a different VCT path or an externally hosted VCT URL."
        }
    }

    private fun URI.effectivePort(): Int =
        if (port >= 0) port else if (scheme.equals("https", ignoreCase = true)) 443 else 80

    private enum class VctKind { HTTP, URN }
    private companion object {
        const val INTERNAL_VCT_BASE_URL = "vctBaseUrl"
        val URN_WITH_NAMESPACE = Regex("^[^:\\s]+:\\S+$")
    }
}
