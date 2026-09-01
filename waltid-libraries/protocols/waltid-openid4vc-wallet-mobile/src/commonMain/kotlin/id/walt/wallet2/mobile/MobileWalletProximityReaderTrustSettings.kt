@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.certificate.x509.PemUtil
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.SignedRical
import id.walt.mdoc.proximity.X509RicalSignatureValidator
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateAuthorityUsage
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Instant

/** Persisted Reader CA trust material selected by the holder. */
public data class MobileWalletProximityStoredReaderTrustAnchor(
    /** DER certificate encoded as unpadded Base64URL. */
    public val certificateDerBase64Url: String,
    /** Holder-visible name for the authority. */
    public val displayName: String,
) {
    init {
        require(displayName.isNotBlank()) { "Reader authority display name must not be blank" }
        require(certificateDerBase64Url.isNotBlank()) { "Reader authority certificate must not be blank" }
    }
}

/** Persisted, already validated static qualification RICAL provider. */
public data class MobileWalletProximityStoredRicalProvider(
    /** Stable application-owned identifier for this provider. */
    public val providerId: String,
    /** RICAL list types this provider is allowed to supply. */
    public val acceptedTypes: Set<String>,
    /** DER provider trust anchors encoded as unpadded Base64URL values. */
    public val providerTrustAnchorsDerBase64Url: List<String>,
    /** Certificate-policy OIDs accepted for the RICAL signer. */
    public val acceptedSignerCertificatePolicyOids: Set<String>,
    /** Whether a valid list from this provider may establish reader trust. */
    public val establishReaderTrust: Boolean,
    /** Exact untagged COSE_Sign1 bytes encoded as unpadded Base64URL. */
    public val signedRicalBase64Url: String,
) {
    init {
        require(providerId.isNotBlank()) { "RICAL provider identifier must not be blank" }
        require(acceptedTypes.isNotEmpty() && acceptedTypes.none(String::isBlank))
        require(providerTrustAnchorsDerBase64Url.isNotEmpty())
        require(acceptedSignerCertificatePolicyOids.isNotEmpty() &&
            acceptedSignerCertificatePolicyOids.none(String::isBlank))
        require(signedRicalBase64Url.isNotBlank())
    }
}

/** Complete holder-owned Reader Authentication settings snapshot. */
public data class MobileWalletProximityReaderTrustSettings(
    /** Reader policy applied after the configured trust evidence is evaluated. */
    public val readerPolicy: MobileWalletProximityReaderPolicy =
        MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted,
    /** Validated Reader CA trust anchors available to new sessions. */
    public val trustAnchors: List<MobileWalletProximityStoredReaderTrustAnchor> = emptyList(),
    /** Validated static RICAL providers available to new sessions. */
    public val ricalProviders: List<MobileWalletProximityStoredRicalProvider> = emptyList(),
) {
    init {
        require(trustAnchors.distinctBy { it.certificateDerBase64Url }.size == trustAnchors.size) {
            "Reader CA trust anchors must be unique"
        }
        require(ricalProviders.distinctBy { it.providerId }.size == ricalProviders.size) {
            "RICAL provider identifiers must be unique"
        }
    }

    /** Applies this immutable settings snapshot to one new proximity session. */
    public fun applyTo(
        configuration: MobileWalletProximityConfiguration,
    ): MobileWalletProximityConfiguration {
        val trustConfiguration = if (trustAnchors.isEmpty() && ricalProviders.isEmpty()) null else
            MobileWalletProximityReaderTrustConfiguration(
                trustAnchors = trustAnchors.map {
                    MobileWalletProximityReaderTrustAnchor(
                        certificateDerBase64Url = it.certificateDerBase64Url,
                        displayName = it.displayName,
                    )
                },
                ricalProviders = ricalProviders.map { stored ->
                    MobileWalletProximityRicalConfiguration(
                        providerId = stored.providerId,
                        acceptedTypes = stored.acceptedTypes,
                        providerTrustAnchors = stored.providerTrustAnchorsDerBase64Url.map {
                            MobileWalletProximityRicalProviderTrustAnchor(it)
                        },
                        acceptedSignerCertificatePolicyOids =
                            stored.acceptedSignerCertificatePolicyOids,
                        establishReaderTrust = stored.establishReaderTrust,
                        provider = MobileWalletProximityRicalProvider {
                            MobileWalletProximityRicalProviderResult.Available(
                                stored.signedRicalBase64Url
                            )
                        },
                    )
                },
            )
        return configuration.copy(
            readerPolicy = readerPolicy,
            readerTrustEvaluator = trustConfiguration?.let(
                ::MobileWalletProximityConfiguredReaderTrustEvaluator
            ) ?: UnconfiguredMobileWalletProximityReaderTrustEvaluator,
        )
    }
}

/** Kind of public reader-trust material represented by an import preview. */
public enum class MobileWalletProximityReaderTrustImportKind {
    /** One or more public Reader CA certificates. */
    ReaderCa,
    /** A versioned walt.id reader-trust bundle. */
    TrustBundle,
}

/** Display-safe preview of an imported Reader CA. */
public data class MobileWalletProximityReaderTrustAnchorPreview(
    /** Holder-visible authority name proposed for persistence. */
    public val displayName: String,
    /** Display-safe certificate subject. */
    public val subject: String,
    /** Display-safe certificate issuer. */
    public val issuer: String,
    /** Colon-separated SHA-256 certificate fingerprint. */
    public val sha256Fingerprint: String,
    /** Beginning of the certificate validity interval. */
    public val validFrom: Instant,
    /** End of the certificate validity interval. */
    public val validUntil: Instant,
    /** Validated certificate profile shown during import review. */
    public val profile: String = "ISO mdoc Reader CA",
)

/** Display-safe preview of a validated static RICAL provider. */
public data class MobileWalletProximityRicalPreview(
    /** Stable provider identifier from the imported bundle. */
    public val providerId: String,
    /** Holder-visible provider name. */
    public val providerName: String,
    /** Validated RICAL list type. */
    public val type: String,
    /** Time at which the signed list was issued. */
    public val issuedAt: Instant,
    /** Optional time at which the provider expects a newer list. */
    public val nextUpdate: Instant?,
    /** Optional end of the signed list validity interval. */
    public val validUntil: Instant?,
    /** Whether this provider is configured to establish reader trust. */
    public val establishesReaderTrust: Boolean,
)

/** Import review which must be explicitly confirmed before its settings are persisted. */
public data class MobileWalletProximityReaderTrustImportPreview(
    /** Kind of imported public trust material. */
    public val kind: MobileWalletProximityReaderTrustImportKind,
    /** Original display-safe file name supplied by the platform picker. */
    public val sourceName: String,
    /** Reader CA previews added by this import. */
    public val readerAuthorities: List<MobileWalletProximityReaderTrustAnchorPreview>,
    /** RICAL provider previews added by this import. */
    public val ricalProviders: List<MobileWalletProximityRicalPreview>,
    /** Complete immutable settings that will replace the prior value after confirmation. */
    public val resultingSettings: MobileWalletProximityReaderTrustSettings,
) {
    /** Display-safe description of the policy enforced by [resultingSettings]. */
    public val policyEffect: String
        get() = when (resultingSettings.readerPolicy) {
            MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted ->
                "Untrusted readers may still reach holder consent"
            MobileWalletProximityReaderPolicy.RequireTrusted ->
                "Only readers trusted by the configured material may reach holder consent"
        }
}

/** Strict codec and importer for holder-owned Reader Authentication settings. */
public object MobileWalletProximityReaderTrustSettingsCodec {
    /** Maximum accepted encoded settings or import-file size in bytes. */
    public const val MaximumImportBytes: Int = 1_048_576

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }

    /** Encodes validated settings into the versioned app-private persistence representation. */
    public fun encode(settings: MobileWalletProximityReaderTrustSettings): String =
        json.encodeToString(settings.toPersisted())

    /**
     * Decodes and validates the versioned app-private persistence representation.
     *
     * @throws IllegalArgumentException when the representation is malformed or unsupported.
     */
    @Throws(IllegalArgumentException::class)
    public fun decode(encoded: String): MobileWalletProximityReaderTrustSettings = try {
        json.decodeFromString<PersistedSettings>(encoded).toPublic().also(::validateStoredShape)
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Invalid reader trust settings", error)
    }

    /**
     * Validates imported bytes and prepares an immutable review. This function never persists data.
     */
    @Throws(IllegalArgumentException::class, CancellationException::class)
    public suspend fun prepareImport(
        sourceName: String,
        bytes: ByteArray,
        existing: MobileWalletProximityReaderTrustSettings,
        now: Instant = Clock.System.now(),
    ): MobileWalletProximityReaderTrustImportPreview = try {
        require(sourceName.isNotBlank()) { "The imported file must have a name" }
        require(bytes.isNotEmpty()) { "The imported file is empty" }
        require(bytes.size <= MaximumImportBytes) { "The imported file exceeds 1 MiB" }
        val lowercaseName = sourceName.lowercase()
        require(!lowercaseName.endsWith(".p12") && !lowercaseName.endsWith(".pfx")) {
            "PKCS#12/PFX files are not accepted; import public trust material only"
        }
        val text = bytes.decodeToString()
        require(!text.contains("PRIVATE KEY", ignoreCase = true)) {
            "Private keys are not accepted; import public trust material only"
        }
        if (text.trimStart().startsWith("{")) {
            prepareBundleImport(sourceName, text, existing, now)
        } else {
            prepareCertificateImport(sourceName, bytes, text, existing, now)
        }
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Reader trust material could not be imported", error)
    }

    private fun prepareCertificateImport(
        sourceName: String,
        bytes: ByteArray,
        text: String,
        existing: MobileWalletProximityReaderTrustSettings,
        now: Instant,
    ): MobileWalletProximityReaderTrustImportPreview {
        val certificates = if (text.contains("-----BEGIN")) parseStrictCertificatePem(text) else listOf(
            parseCertificate(bytes)
        )
        val existingFingerprints = existing.trustAnchors.mapTo(mutableSetOf()) {
            parseCertificate(it.certificateDerBase64Url.decodeBase64Url()).fingerprintSha256Hex
        }
        val importedFingerprints = mutableSetOf<String>()
        val previews = certificates.mapIndexed { index, certificate ->
            validateReaderCa(certificate, now)
            val fingerprint = certificate.fingerprintSha256Hex
            require(existingFingerprints.add(fingerprint) && importedFingerprints.add(fingerprint)) {
                "Duplicate Reader CA certificate: $fingerprint"
            }
            certificate.preview(defaultDisplayName(sourceName, certificate, index, certificates.size))
        }
        val importedAnchors = certificates.zip(previews).map { (certificate, preview) ->
            MobileWalletProximityStoredReaderTrustAnchor(
                certificateDerBase64Url = certificate.encodedDer.toByteArray().encodeBase64Url(),
                displayName = preview.displayName,
            )
        }
        return MobileWalletProximityReaderTrustImportPreview(
            kind = MobileWalletProximityReaderTrustImportKind.ReaderCa,
            sourceName = sourceName,
            readerAuthorities = previews,
            ricalProviders = emptyList(),
            resultingSettings = existing.copy(trustAnchors = existing.trustAnchors + importedAnchors),
        )
    }

    private suspend fun prepareBundleImport(
        sourceName: String,
        text: String,
        existing: MobileWalletProximityReaderTrustSettings,
        now: Instant,
    ): MobileWalletProximityReaderTrustImportPreview {
        val bundle = json.decodeFromString<TrustBundle>(text)
        require(bundle.version == BundleVersion) { "Unsupported reader trust bundle version" }
        require(bundle.type == BundleType) { "Unsupported reader trust bundle type" }
        require(bundle.readerAuthorities.isNotEmpty() || bundle.ricalProviders.isNotEmpty()) {
            "The reader trust bundle contains no trust material"
        }

        val existingFingerprints = existing.trustAnchors.mapTo(mutableSetOf()) {
            parseCertificate(it.certificateDerBase64Url.decodeBase64Url()).fingerprintSha256Hex
        }
        val readerPreviews = bundle.readerAuthorities.map { anchor ->
            require(anchor.name.isNotBlank()) { "Reader authority name must not be blank" }
            val certificate = parseCertificate(anchor.certificateDerBase64Url.decodeBase64Url())
            validateReaderCa(certificate, now)
            require(existingFingerprints.add(certificate.fingerprintSha256Hex)) {
                "Duplicate Reader CA certificate: ${certificate.fingerprintSha256Hex}"
            }
            certificate.preview(anchor.name)
        }
        val storedAnchors = bundle.readerAuthorities.mapIndexed { index, anchor ->
            MobileWalletProximityStoredReaderTrustAnchor(
                certificateDerBase64Url =
                    parseCertificate(anchor.certificateDerBase64Url.decodeBase64Url())
                        .encodedDer.toByteArray().encodeBase64Url(),
                displayName = readerPreviews[index].displayName,
            )
        }

        val providerIds = existing.ricalProviders.mapTo(mutableSetOf()) { it.providerId }
        val ricalPreviews = mutableListOf<MobileWalletProximityRicalPreview>()
        val storedProviders = bundle.ricalProviders.map { provider ->
            require(providerIds.add(provider.providerId)) {
                "Duplicate RICAL provider identifier: ${provider.providerId}"
            }
            require(provider.providerTrustAnchorsDerBase64Url.distinct().size ==
                provider.providerTrustAnchorsDerBase64Url.size) {
                "Duplicate RICAL provider trust anchor"
            }
            val roots = provider.providerTrustAnchorsDerBase64Url.map { encoded ->
                parseCertificate(encoded.decodeBase64Url()).also { validateReaderCa(it, now) }
            }
            val signedBytes = provider.signedRicalBase64Url.decodeBase64Url()
            val signed = runCatching { SignedRical.decode(signedBytes) }.getOrElse {
                throw IllegalArgumentException("Invalid signed RICAL", it)
            }
            require(signed.rical.provider == provider.providerId) {
                "RICAL provider does not match the configured provider identifier"
            }
            require(signed.rical.type in provider.acceptedTypes) {
                "RICAL type is not accepted by this provider configuration"
            }
            require(signed.rical.date <= now) { "RICAL issue date is in the future" }
            require(signed.rical.nextUpdate == null || now < signed.rical.nextUpdate!!) {
                "RICAL next-update time has passed"
            }
            require(signed.rical.notAfter == null || now < signed.rical.notAfter!!) {
                "RICAL has expired"
            }
            require(signed.rical.extensions.isEmpty() && signed.rical.reserved.isEmpty() &&
                signed.rical.certificateInfos.all {
                    it.trustConstraints.isEmpty() && it.extensions.isEmpty() && it.reserved.isEmpty()
                }) {
                "RICAL contains unsupported extension or trust-constraint semantics"
            }
            val signatureValid = X509RicalSignatureValidator(
                provider.acceptedSignerCertificatePolicyOids,
                now = { now },
            ).validate(
                signed,
                roots.map { ImmutableBytes.of(it.encodedDer.toByteArray()) },
            )
            require(signatureValid) { "RICAL signature, signer profile, or signer path is invalid" }
            ricalPreviews += MobileWalletProximityRicalPreview(
                providerId = provider.providerId,
                providerName = signed.rical.provider,
                type = signed.rical.type,
                issuedAt = signed.rical.date,
                nextUpdate = signed.rical.nextUpdate,
                validUntil = signed.rical.notAfter,
                establishesReaderTrust = provider.establishReaderTrust,
            )
            MobileWalletProximityStoredRicalProvider(
                providerId = provider.providerId,
                acceptedTypes = provider.acceptedTypes,
                providerTrustAnchorsDerBase64Url = roots.map {
                    it.encodedDer.toByteArray().encodeBase64Url()
                },
                acceptedSignerCertificatePolicyOids = provider.acceptedSignerCertificatePolicyOids,
                establishReaderTrust = provider.establishReaderTrust,
                signedRicalBase64Url = signed.exactMessage.copy().encodeBase64Url(),
            )
        }
        return MobileWalletProximityReaderTrustImportPreview(
            kind = MobileWalletProximityReaderTrustImportKind.TrustBundle,
            sourceName = sourceName,
            readerAuthorities = readerPreviews,
            ricalProviders = ricalPreviews,
            resultingSettings = existing.copy(
                trustAnchors = existing.trustAnchors + storedAnchors,
                ricalProviders = existing.ricalProviders + storedProviders,
            ),
        )
    }

    private fun parseStrictCertificatePem(text: String): List<X509Certificate> {
        val blockRegex = Regex(
            "-----BEGIN\\s+([^-]+)-----([\\s\\S]*?)-----END\\s+([^-]+)-----"
        )
        val matches = blockRegex.findAll(text).toList()
        require(matches.isNotEmpty()) { "The PEM file contains no certificates" }
        val remainder = buildString {
            var offset = 0
            for (match in matches) {
                append(text.substring(offset, match.range.first))
                offset = match.range.last + 1
            }
            append(text.substring(offset))
        }
        require(remainder.isBlank()) { "The PEM file contains unsupported content" }
        return matches.map { match ->
            val begin = match.groupValues[1].trim()
            val end = match.groupValues[3].trim()
            require(begin == "CERTIFICATE" && end == "CERTIFICATE") {
                "Only CERTIFICATE PEM blocks are accepted"
            }
            runCatching { X509CertificateUtil.parseCertificatePem(PemUtil.normalizePem(match.value)) }
                .getOrElse { throw IllegalArgumentException("Invalid X.509 certificate", it) }
        }
    }

    private fun parseCertificate(bytes: ByteArray): X509Certificate = runCatching {
        X509CertificateUtil.parseCertificateDerEncoded(ByteString(bytes))
    }.getOrElse { throw IllegalArgumentException("Invalid DER X.509 certificate", it) }

    private fun validateReaderCa(certificate: X509Certificate, now: Instant) {
        runCatching {
            CertificateDer(certificate.encodedDer.toByteArray()).validateCertificateAuthorityUsage(now)
        }.getOrElse { throw IllegalArgumentException("Reader trust anchor is not a valid current CA", it) }
    }

    private fun X509Certificate.preview(displayName: String) =
        MobileWalletProximityReaderTrustAnchorPreview(
            displayName = displayName,
            subject = data.subjectDn,
            issuer = data.issuerDn,
            sha256Fingerprint = fingerprintSha256Hex
                .chunked(2)
                .joinToString(":") { it.uppercase() },
            validFrom = data.validity.notBefore,
            validUntil = data.validity.notAfter,
        )

    private fun defaultDisplayName(
        sourceName: String,
        certificate: X509Certificate,
        index: Int,
        count: Int,
    ): String = certificate.data.subjectDn.takeIf(String::isNotBlank)
        ?: if (count == 1) sourceName else "$sourceName (${index + 1})"

    private fun validateStoredShape(settings: MobileWalletProximityReaderTrustSettings) {
        settings.trustAnchors.forEach {
            parseCertificate(it.certificateDerBase64Url.decodeBase64Url())
        }
        settings.ricalProviders.forEach { provider ->
            provider.providerTrustAnchorsDerBase64Url.forEach {
                parseCertificate(it.decodeBase64Url())
            }
            runCatching { SignedRical.decode(provider.signedRicalBase64Url.decodeBase64Url()) }
                .getOrElse { throw IllegalArgumentException("Invalid persisted RICAL", it) }
        }
    }

    @Serializable
    private data class PersistedSettings(
        val version: Int = SettingsVersion,
        val readerPolicy: PersistedReaderPolicy = PersistedReaderPolicy.AllowAnonymousOrUntrusted,
        val readerAuthorities: List<PersistedReaderAuthority> = emptyList(),
        val ricalProviders: List<PersistedRicalProvider> = emptyList(),
    )

    @Serializable
    private enum class PersistedReaderPolicy {
        @SerialName("allow_anonymous_or_untrusted")
        AllowAnonymousOrUntrusted,

        @SerialName("require_trusted")
        RequireTrusted,
    }

    @Serializable
    private data class PersistedReaderAuthority(
        val name: String,
        val certificateDerBase64Url: String,
    )

    @Serializable
    private data class PersistedRicalProvider(
        val providerId: String,
        val acceptedTypes: Set<String>,
        val providerTrustAnchorsDerBase64Url: List<String>,
        val acceptedSignerCertificatePolicyOids: Set<String>,
        val establishReaderTrust: Boolean,
        val signedRicalBase64Url: String,
    )

    @Serializable
    private data class TrustBundle(
        val version: Int,
        val type: String,
        val readerAuthorities: List<BundleReaderAuthority> = emptyList(),
        val ricalProviders: List<PersistedRicalProvider> = emptyList(),
    )

    @Serializable
    private data class BundleReaderAuthority(
        val name: String,
        val certificateDerBase64Url: String,
    )

    private fun MobileWalletProximityReaderTrustSettings.toPersisted() = PersistedSettings(
        readerPolicy = when (readerPolicy) {
            MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted ->
                PersistedReaderPolicy.AllowAnonymousOrUntrusted
            MobileWalletProximityReaderPolicy.RequireTrusted -> PersistedReaderPolicy.RequireTrusted
        },
        readerAuthorities = trustAnchors.map {
            PersistedReaderAuthority(it.displayName, it.certificateDerBase64Url)
        },
        ricalProviders = ricalProviders.map {
            PersistedRicalProvider(
                providerId = it.providerId,
                acceptedTypes = it.acceptedTypes,
                providerTrustAnchorsDerBase64Url = it.providerTrustAnchorsDerBase64Url,
                acceptedSignerCertificatePolicyOids = it.acceptedSignerCertificatePolicyOids,
                establishReaderTrust = it.establishReaderTrust,
                signedRicalBase64Url = it.signedRicalBase64Url,
            )
        },
    )

    private fun PersistedSettings.toPublic(): MobileWalletProximityReaderTrustSettings {
        require(version == SettingsVersion) { "Unsupported reader trust settings version" }
        return MobileWalletProximityReaderTrustSettings(
            readerPolicy = when (readerPolicy) {
                PersistedReaderPolicy.AllowAnonymousOrUntrusted ->
                    MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted
                PersistedReaderPolicy.RequireTrusted -> MobileWalletProximityReaderPolicy.RequireTrusted
            },
            trustAnchors = readerAuthorities.map {
                MobileWalletProximityStoredReaderTrustAnchor(it.certificateDerBase64Url, it.name)
            },
            ricalProviders = ricalProviders.map {
                MobileWalletProximityStoredRicalProvider(
                    providerId = it.providerId,
                    acceptedTypes = it.acceptedTypes,
                    providerTrustAnchorsDerBase64Url = it.providerTrustAnchorsDerBase64Url,
                    acceptedSignerCertificatePolicyOids = it.acceptedSignerCertificatePolicyOids,
                    establishReaderTrust = it.establishReaderTrust,
                    signedRicalBase64Url = it.signedRicalBase64Url,
                )
            },
        )
    }

    private const val SettingsVersion = 1
    private const val BundleVersion = 1
    private const val BundleType = "org.waltid.wallet.reader-trust"
}

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeBase64Url(): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun String.decodeBase64Url(): ByteArray = runCatching {
    require(isNotBlank() && !contains('='))
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(this)
}.getOrElse { throw IllegalArgumentException("Expected unpadded Base64URL data", it) }
