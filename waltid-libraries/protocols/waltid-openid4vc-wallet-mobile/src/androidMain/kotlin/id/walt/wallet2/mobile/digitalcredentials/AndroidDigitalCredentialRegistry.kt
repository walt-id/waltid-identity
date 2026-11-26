package id.walt.wallet2.mobile.digitalcredentials

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.registry.digitalcredentials.mdoc.MdocEntry
import androidx.credentials.registry.digitalcredentials.mdoc.MdocField
import androidx.credentials.registry.digitalcredentials.openid4vp.OpenId4VpRegistry
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtClaim
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtEntry
import androidx.credentials.registry.provider.RegisterCreationOptionsException
import androidx.credentials.registry.provider.RegisterCreationOptionsRequest
import androidx.credentials.registry.provider.RegisterCreationOptionsResponse
import androidx.credentials.registry.provider.RegisterCredentialsException
import androidx.credentials.registry.provider.RegisterCredentialsRequest
import androidx.credentials.registry.provider.RegisterCredentialsResponse
import androidx.credentials.registry.provider.RegistryManager
import androidx.credentials.registry.provider.digitalcredentials.DigitalCredentialEntry
import androidx.credentials.registry.provider.digitalcredentials.DigitalCredentialRegistry
import androidx.credentials.registry.provider.digitalcredentials.VerificationEntryDisplayProperties
import androidx.credentials.registry.provider.digitalcredentials.VerificationFieldDisplayProperties
import id.walt.cose.coseCompliantCbor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Android Credential Manager metadata registry adapter.
 *
 * Credential Manager owns the registered metadata: a registry published here survives wallet process
 * death and device reboot, and is served to a caller without the wallet ever being launched, so the
 * wallet keeps no copy to replay at startup. Verified on API 37 with Google Play services 26.29.32.
 *
 * @property capabilities Current Android platform and registry availability.
 */
public class AndroidDigitalCredentialRegistry(
    context: Context,
) : CredentialRegistry {
    override suspend fun replace(
        registryId: String,
        records: List<RegistryEntry>,
    ): CredentialRegistrationResult = replacementMutex.withLock {
        currentCoroutineContext().ensureActive()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            registrationAvailable = false
            creationRegistrationAvailable = false
            return@withLock CredentialRegistrationResult.Unavailable("Credential Manager requires API 23")
        }
        val immediate = records.map { it.withLocalThumbnail() }
        val result = registerRecords(registryId, immediate)
        if (!result.available || records.none { it.display.iconPng == null && it.display.cardArt.imageUris.isNotEmpty() }) {
            return@withLock result
        }
        val refreshed = withTimeoutOrNull(RegistryIconFetchTimeoutMs) {
            coroutineScope {
                records.map { record -> async { record.withResolvedThumbnail() } }.awaitAll()
            }
        } ?: return@withLock result
        val changed = refreshed.indices.any { index ->
            !refreshed[index].display.iconPng.contentEquals(immediate[index].display.iconPng)
        }
        if (changed) {
            val refreshResult = registerRecords(registryId, refreshed)
            registrationAvailable = registrationAvailableAfterRefresh(
                initialSucceeded = result.available,
                refreshSucceeded = refreshResult.available,
            )
        }
        return@withLock result
    }

    /**
     * Advertises OpenID4VCI issuance capability to Credential Manager.
     *
     * Independent of [replace]: creation options describe what the wallet can receive, not which
     * credentials it currently holds.
     */
    @OptIn(ExperimentalDigitalCredentialApi::class)
    override suspend fun registerCreationOptions(): CredentialRegistrationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            creationRegistrationAvailable = false
            return CredentialRegistrationResult.Unavailable("Credential Manager requires API 23")
        }
        return runCatching {
            val matcher = applicationContext.assets.open(OPENID4VCI_MATCHER_ASSET).use { it.readBytes() }
            registerCreationOptions(
                object : RegisterCreationOptionsRequest(
                    creationOptions = encodeOpenId4VciCreationOptions(
                        entryId = OPENID4VCI_CREATION_REGISTRY_ID,
                        applicationName = applicationDisplayName(),
                        subtitle = "Save a credential to this wallet",
                        explainer = "Save a credential to this wallet.",
                        icon = applicationIconPng,
                    ),
                    matcher = matcher,
                    type = DigitalCredential.TYPE_DIGITAL_CREDENTIAL,
                    id = OPENID4VCI_CREATION_REGISTRY_ID,
                ) {},
            )
            creationRegistrationAvailable = true
            CredentialRegistrationResult.Applied(1)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            creationRegistrationAvailable = false
            CredentialRegistrationResult.Unavailable(error.message ?: error::class.simpleName ?: "Creation-option registration failed")
        }
    }

    override val capabilities: DigitalCredentialCapabilities
        get() {
            val platformAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            val runtimeAvailable = platformAvailable && registrationAvailable
            val creationAvailable = platformAvailable && creationRegistrationAvailable
            val unavailableReason = when {
                !platformAvailable -> "Credential Manager requires Android 6 (API 23)"
                !registrationAvailable -> "Credential registration has not completed successfully"
                else -> null
            }
            val creationUnavailableReason = when {
                !platformAvailable -> "Credential Manager requires Android 6 (API 23)"
                !creationRegistrationAvailable -> "Credential creation registration has not completed successfully"
                else -> null
            }
            return DigitalCredentialCapabilities(
                platform = "Android Credential Manager",
                platformAvailable = platformAvailable,
                minimumOsVersion = "Android 6 (API 23)",
                registrationAvailable = runtimeAvailable,
                capabilities = listOf(
                    DigitalCredentialCapability(
                        protocol = DigitalCredentialProtocols.OPENID4VP_UNSIGNED,
                        credentialFormats = listOf(
                            DigitalCredentialFormat.MDOC,
                            DigitalCredentialFormat.SD_JWT_VC,
                        ),
                        requestProtection = listOf(DigitalCredentialRequestProtection.UNSIGNED),
                        // response_mode=dc_api and dc_api.jwt respectively.
                        responseProtection = listOf(
                            DigitalCredentialResponseProtection.UNENCRYPTED,
                            DigitalCredentialResponseProtection.JWE,
                        ),
                        supported = runtimeAvailable,
                        unsupportedReason = unavailableReason,
                    ),
                    DigitalCredentialCapability(
                        protocol = DigitalCredentialProtocols.OPENID4VP_SIGNED,
                        credentialFormats = listOf(
                            DigitalCredentialFormat.MDOC,
                            DigitalCredentialFormat.SD_JWT_VC,
                        ),
                        requestProtection = listOf(DigitalCredentialRequestProtection.SIGNED),
                        responseProtection = listOf(
                            DigitalCredentialResponseProtection.UNENCRYPTED,
                            DigitalCredentialResponseProtection.JWE,
                        ),
                        supported = runtimeAvailable,
                        unsupportedReason = unavailableReason,
                    ),
                    DigitalCredentialCapability(
                        protocol = DigitalCredentialProtocols.OPENID4VP_MULTISIGNED,
                        credentialFormats = listOf(
                            DigitalCredentialFormat.MDOC,
                            DigitalCredentialFormat.SD_JWT_VC,
                        ),
                        requestProtection = listOf(DigitalCredentialRequestProtection.MULTISIGNED),
                        responseProtection = listOf(DigitalCredentialResponseProtection.UNENCRYPTED),
                        supported = false,
                        unsupportedReason = MULTISIGNED_UNSUPPORTED_REASON,
                    ),
                    DigitalCredentialCapability(
                        protocol = DigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
                        credentialFormats = listOf(DigitalCredentialFormat.MDOC),
                        requestProtection = listOf(DigitalCredentialRequestProtection.READER_AUTHENTICATED),
                        responseProtection = listOf(DigitalCredentialResponseProtection.HPKE),
                        supported = runtimeAvailable,
                        unsupportedReason = unavailableReason,
                    ),
                    DigitalCredentialCapability(
                        protocol = DigitalCredentialProtocols.OPENID4VCI_V1,
                        credentialFormats = listOf(
                            DigitalCredentialFormat.MDOC,
                            DigitalCredentialFormat.SD_JWT_VC,
                        ),
                        requestProtection = listOf(DigitalCredentialRequestProtection.UNSIGNED),
                        responseProtection = listOf(DigitalCredentialResponseProtection.UNENCRYPTED),
                        supported = creationAvailable,
                        unsupportedReason = creationUnavailableReason,
                    ),
                ),
            )
        }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun encodeAnnexCCredentialDatabase(
        records: List<RegistryEntry>,
    ): ByteArray = coseCompliantCbor.encodeToByteArray(
        AndroidAnnexCCredentialDatabase(
            protocols = listOf(DigitalCredentialProtocols.ISO_MDOC_ANNEX_C),
            credentials = records
                .filter { it.format == DigitalCredentialFormat.MDOC }
                .map { record ->
                    AndroidAnnexCCredential(
                        title = record.display.name,
                        subtitle = record.display.subtitle,
                        bitmap = record.entryIconPng(),
                        mdoc = AndroidAnnexCMdoc(
                            documentId = record.registryEntryId,
                            docType = record.type,
                            namespaces = record.fields
                                .onEach { require(it.path.size == 2) { "mdoc registry fields require namespace and element paths" } }
                                .groupBy { it.path[0] }
                                .mapValues { (_, fields) ->
                                    fields.associate { field ->
                                        val value = field.valueJson.registryScalar()
                                        val rawValue = value?.content.orEmpty()
                                        field.path[1] to listOf(
                                            field.path[1],
                                            value?.registryDisplayValue().orEmpty(),
                                            rawValue.takeIf { it.length < MAX_MATCHER_VALUE_LENGTH }.orEmpty(),
                                        )
                                    }
                                },
                        ),
                    )
                },
        )
    )
    /**
     * Binary creation-options database understood by Google's OpenID4VCI issuance matcher.
     *
     * Layout matches AndroidX's OpenId4VciRegistry: little-endian JSON offset, then optional icon
     * bytes, then JSON metadata whose package-info icon offsets point into that blob.
     */
    internal fun encodeOpenId4VciCreationOptions(
        entryId: String,
        applicationName: String,
        subtitle: String?,
        explainer: String?,
        icon: ByteArray,
    ): ByteArray {
        val jsonOffset = 4 + icon.size
        val json = buildJsonObject {
            put("entry_id", entryId)
            putJsonArray("entries") {
                add(buildJsonObject {
                    if (subtitle != null) put("subtitle", subtitle)
                    if (explainer != null) {
                        putJsonObject("explainer") {
                            put("default", explainer)
                        }
                    }
                })
            }
            putJsonObject("filter") {
                putJsonObject("Pass") {}
            }
            putJsonArray("preferred_protocols") {
                OPENID4VCI_CREATE_PROTOCOLS.forEach { protocol ->
                    add(JsonPrimitive(protocol))
                }
            }
            putJsonObject("package_info") {
                put("name", applicationName)
                if (icon.isNotEmpty()) {
                    putJsonArray("icon") {
                        add(JsonPrimitive(4))
                        add(JsonPrimitive(4 + icon.size))
                    }
                }
            }
        }.toString().encodeToByteArray()
        return ByteArrayOutputStream(jsonOffset + json.size).use { out ->
            val offsetBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(jsonOffset).array()
            out.write(offsetBytes)
            out.write(icon)
            out.write(json)
            out.toByteArray()
        }
    }
    internal fun RegistryEntry.toAndroidEntry(): DigitalCredentialEntry {
        val display = setOf(
            VerificationEntryDisplayProperties(
                this.display.name,
                this.display.subtitle,
                entryIconBitmap(),
                null,
                null,
            )
        )
        return when (format) {
            DigitalCredentialFormat.MDOC -> MdocEntry(
                docType = type,
                fields = fields.map { field ->
                    require(field.path.size == 2) { "mdoc registry fields require namespace and element paths" }
                    val value = field.valueJson.registryScalar()
                    MdocField(
                        namespace = field.path[0],
                        identifier = field.path[1],
                        fieldValue = value?.toPlatformValue(),
                        fieldDisplayPropertySet = setOf(
                            VerificationFieldDisplayProperties(field.path[1], value?.registryDisplayValue())
                        ),
                    )
                },
                entryDisplayPropertySet = display,
                id = registryEntryId,
            )

            DigitalCredentialFormat.SD_JWT_VC -> SdJwtEntry(
                verifiableCredentialType = type,
                claims = fields.map { field ->
                    val value = field.valueJson.registryScalar()
                    SdJwtClaim(
                        path = field.path,
                        value = value?.toPlatformValue(),
                        fieldDisplayPropertySet = setOf(
                            VerificationFieldDisplayProperties(field.path.last(), value?.registryDisplayValue())
                        ),
                        isSelectivelyDisclosable = field.selectivelyDisclosable,
                    )
                },
                entryDisplayPropertySet = display,
                id = registryEntryId,
            )
        }
    }
    internal fun advertisedOpenId4VpProtocols(): List<String> = listOf(
        OpenId4VpRegistry.PROTOCOL_OPENID4VP_1_0_SIGNED,
        OpenId4VpRegistry.PROTOCOL_OPENID4VP_1_0_UNSIGNED,
    )

    private val applicationContext: Context = context.applicationContext

    private val registryManager: RegistryManager = RegistryManager.create(applicationContext)

    /** Host app icon shown in the Credential Manager wallet / credential picker. */
    private val applicationIcon: Bitmap = loadApplicationIcon(applicationContext)

    private val applicationIconPng: ByteArray = applicationIcon.toPngBytes()

    private var registrationAvailable: Boolean = false

    private var creationRegistrationAvailable: Boolean = false

    private suspend fun registerRecords(
        registryId: String,
        records: List<RegistryEntry>,
    ): CredentialRegistrationResult {
        val entries = records.map { it.toAndroidEntry() }
        return runCatching {
            // Signed is always advertised. Unsigned is opt-in so Credential Manager does not route
            // unauthenticated request objects the hosting application has not permitted. Multisigned
            // stays off the list so JWS JSON Serialization is ignored rather than routed here to fail.
            val openId4Vp = OpenId4VpRegistry(
                credentialEntries = entries,
                id = registryId,
                supportedProtocols = advertisedOpenId4VpProtocols(),
            )
            // Same registry bytes, different matcher. See OPENID4VP-MATCHER.md for why AndroidX's
            // embedded matcher cannot serve a transaction data request alongside a second credential.
            registerCredentials(
                AndroidOpenId4VpRegistry(
                    id = registryId,
                    credentials = openId4Vp.credentials,
                    matcher = applicationContext.assets.open(OPENID4VP_MATCHER_ASSET).use { it.readBytes() },
                )
            )
            registerCredentials(
                AndroidAnnexCRegistry(
                    id = "$registryId-annex-c",
                    credentials = encodeAnnexCCredentialDatabase(records),
                    matcher = applicationContext.assets.open(ANNEX_C_MATCHER_ASSET).use { it.readBytes() },
                )
            )
            registrationAvailable = true
            CredentialRegistrationResult.Applied(records.map { it.registryEntryId }.distinct().size)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            registrationAvailable = false
            CredentialRegistrationResult.Unavailable(error.message ?: error::class.simpleName ?: "Credential registration failed")
        }
    }

    private suspend fun registerCredentials(request: RegisterCredentialsRequest) {
        publication.publish<RegisterCredentialsResponse, RegisterCredentialsException> { callback ->
            registryManager.registerCredentialsAsync(request, null, Runnable::run, callback)
        }
    }

    private suspend fun registerCreationOptions(request: RegisterCreationOptionsRequest) {
        publication.publish<RegisterCreationOptionsResponse, RegisterCreationOptionsException> { callback ->
            registryManager.registerCreationOptionsAsync(request, null, Runnable::run, callback)
        }
    }

    private fun applicationDisplayName(): String =
        applicationContext.packageManager
            .getApplicationLabel(applicationContext.applicationInfo)
            .toString()
            .ifBlank { applicationContext.packageName }

    private fun RegistryEntry.withLocalThumbnail(): RegistryEntry {
        if (display.iconPng != null) return this
        val png = display.cardArt.fallbackPng?.takeIf(::isImageBytes)
            ?: solidColorPng(parseCssRgb(display.cardArt.backgroundColor) ?: RegistryIcons.DefaultCardBlueRgb)
        return copy(display = display.withIcon(png))
    }

    private suspend fun RegistryEntry.withResolvedThumbnail(): RegistryEntry {
        if (display.cardArt.imageUris.isEmpty()) return withLocalThumbnail()
        val png = RegistryIcons.resolveIconPng(
            art = display.cardArt,
            fetchHttps = ::fetchRegistryIconBytes,
        )
        return copy(display = display.withIcon(png))
    }

    private fun RegistryEntry.entryIconBitmap(): Bitmap =
        this.display.iconPng?.decodeRegistryIcon() ?: applicationIcon

    private fun RegistryEntry.entryIconPng(): ByteArray {
        val recordIcon = this.display.iconPng
        return recordIcon?.decodeRegistryIcon()?.toPngBytes()
            ?: recordIcon
            ?: applicationIconPng
    }

    private companion object {
        // A process can have multiple wallet handles (main UI and Credential Manager activities).
        val replacementMutex = Mutex()
        val publication = AndroidRegistryPublication()
        // Vendored, not a dependency; package-qualified so it cannot collide with another library's
        // copy in the application asset merge. See ANNEX-C-MATCHER.md.
        private const val ANNEX_C_MATCHER_ASSET = "id/walt/wallet2/mobile/identitycredentialmatcher.wasm"
        // Vendored OpenID4VCI creation matcher. See OPENID4VCI-MATCHER.md.
        private const val OPENID4VCI_MATCHER_ASSET = "id/walt/wallet2/mobile/issuance.wasm"
        private const val OPENID4VCI_CREATION_REGISTRY_ID = "openid4vci"

        // Vendored, not a dependency. See OPENID4VP-MATCHER.md.
        internal const val OPENID4VP_MATCHER_ASSET = "id/walt/wallet2/mobile/openid4vpmatcher.wasm"





        private const val MULTISIGNED_UNSUPPORTED_REASON =
            "The wallet does not support JWS JSON Serialization request objects"
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class AndroidAnnexCCredential(
    val title: String,
    val subtitle: String,
    @ByteString val bitmap: ByteArray,
    val mdoc: AndroidAnnexCMdoc,
)

/** A failed best-effort art refresh must not clear a successful local registration. */
internal fun registrationAvailableAfterRefresh(
    initialSucceeded: Boolean,
    refreshSucceeded: Boolean,
): Boolean = initialSucceeded || refreshSucceeded

@Serializable
internal data class AndroidAnnexCCredentialDatabase(
    val protocols: List<String>,
    val credentials: List<AndroidAnnexCCredential>,
)

@Serializable
internal data class AndroidAnnexCMdoc(
    val documentId: String,
    val docType: String,
    val namespaces: Map<String, Map<String, List<String>>>,
)

/** Raw registry request because AndroidX does not yet ship an Annex C registry builder. */
private class AndroidAnnexCRegistry(
    id: String,
    credentials: ByteArray,
    matcher: ByteArray,
) : DigitalCredentialRegistry(id = id, credentials = credentials, matcher = matcher)

/**
 * AndroidX's OpenID4VP registry bytes with a different matcher. Keeps `OpenId4VpRegistry` as the sole
 * serializer of the registry format, so only the matcher binary is ours. See OPENID4VP-MATCHER.md.
 */
private class AndroidOpenId4VpRegistry(
    id: String,
    credentials: ByteArray,
    matcher: ByteArray,
) : DigitalCredentialRegistry(id = id, credentials = credentials, matcher = matcher)
