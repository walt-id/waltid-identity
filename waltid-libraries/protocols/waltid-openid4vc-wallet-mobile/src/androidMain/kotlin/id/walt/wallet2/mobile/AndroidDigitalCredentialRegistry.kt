package id.walt.wallet2.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.registry.digitalcredentials.mdoc.MdocEntry
import androidx.credentials.registry.digitalcredentials.mdoc.MdocField
import androidx.credentials.registry.digitalcredentials.openid4vp.OpenId4VpRegistry
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtClaim
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtEntry
import androidx.credentials.registry.provider.RegisterCreationOptionsRequest
import androidx.credentials.registry.provider.RegistryManager
import androidx.credentials.registry.provider.digitalcredentials.DigitalCredentialEntry
import androidx.credentials.registry.provider.digitalcredentials.DigitalCredentialRegistry
import androidx.credentials.registry.provider.digitalcredentials.VerificationEntryDisplayProperties
import androidx.credentials.registry.provider.digitalcredentials.VerificationFieldDisplayProperties
import id.walt.cose.coseCompliantCbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
) : MobileWalletCredentialRegistry {
    private val applicationContext: Context = context.applicationContext
    private val registryManager: RegistryManager = RegistryManager.create(applicationContext)
    /** Host app icon shown in the Credential Manager wallet / credential picker. */
    private val icon: Bitmap = loadApplicationIcon(applicationContext)
    private val iconPng: ByteArray = icon.toPngBytes()
    private var registrationAvailable: Boolean = false
    private var creationRegistrationAvailable: Boolean = false

    override val capabilities: MobileWalletDigitalCredentialCapabilities
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
            return MobileWalletDigitalCredentialCapabilities(
                platform = "Android Credential Manager",
                platformAvailable = platformAvailable,
                minimumOsVersion = "Android 6 (API 23)",
                registrationAvailable = runtimeAvailable,
                capabilities = listOf(
                    MobileWalletDigitalCredentialCapability(
                        protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
                        credentialFormats = listOf(
                            MobileWalletDigitalCredentialFormat.MDOC,
                            MobileWalletDigitalCredentialFormat.SD_JWT_VC,
                        ),
                        requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.UNSIGNED),
                        // response_mode=dc_api and dc_api.jwt respectively.
                        responseProtection = listOf(
                            MobileWalletDigitalCredentialResponseProtection.UNENCRYPTED,
                            MobileWalletDigitalCredentialResponseProtection.JWE,
                        ),
                        supported = runtimeAvailable,
                        unsupportedReason = unavailableReason,
                    ),
                    MobileWalletDigitalCredentialCapability(
                        protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED,
                        credentialFormats = listOf(
                            MobileWalletDigitalCredentialFormat.MDOC,
                            MobileWalletDigitalCredentialFormat.SD_JWT_VC,
                        ),
                        requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.SIGNED),
                        responseProtection = listOf(MobileWalletDigitalCredentialResponseProtection.UNENCRYPTED),
                        supported = false,
                        unsupportedReason = SIGNED_UNSUPPORTED_REASON,
                    ),
                    MobileWalletDigitalCredentialCapability(
                        protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_MULTISIGNED,
                        credentialFormats = listOf(
                            MobileWalletDigitalCredentialFormat.MDOC,
                            MobileWalletDigitalCredentialFormat.SD_JWT_VC,
                        ),
                        requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.MULTISIGNED),
                        responseProtection = listOf(MobileWalletDigitalCredentialResponseProtection.UNENCRYPTED),
                        supported = false,
                        unsupportedReason = MULTISIGNED_UNSUPPORTED_REASON,
                    ),
                    MobileWalletDigitalCredentialCapability(
                        protocol = MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
                        credentialFormats = listOf(MobileWalletDigitalCredentialFormat.MDOC),
                        requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.READER_AUTHENTICATED),
                        responseProtection = listOf(MobileWalletDigitalCredentialResponseProtection.HPKE),
                        supported = runtimeAvailable,
                        unsupportedReason = unavailableReason,
                    ),
                    MobileWalletDigitalCredentialCapability(
                        protocol = MobileWalletDigitalCredentialProtocols.OPENID4VCI_V1,
                        credentialFormats = listOf(
                            MobileWalletDigitalCredentialFormat.MDOC,
                            MobileWalletDigitalCredentialFormat.SD_JWT_VC,
                        ),
                        requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.UNSIGNED),
                        responseProtection = listOf(MobileWalletDigitalCredentialResponseProtection.UNENCRYPTED),
                        supported = creationAvailable,
                        unsupportedReason = creationUnavailableReason,
                    ),
                ),
            )
        }

    override suspend fun replace(
        registryId: String,
        records: List<MobileWalletCredentialRegistryRecord>,
    ): MobileWalletCredentialRegistrationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            registrationAvailable = false
            creationRegistrationAvailable = false
            return MobileWalletCredentialRegistrationResult(false, 0, "Credential Manager requires API 23")
        }
        val entries = records.map { it.toAndroidEntry() }
        return runCatching {
            // Registering only the unsigned protocol makes Credential Manager ignore signed and
            // multisigned requests rather than route them here to be rejected.
            val openId4Vp = OpenId4VpRegistry(
                credentialEntries = entries,
                id = registryId,
                supportedProtocols = listOf(OpenId4VpRegistry.PROTOCOL_OPENID4VP_1_0_UNSIGNED),
            )
            // Same registry bytes, different matcher. See OPENID4VP-MATCHER.md for why AndroidX's
            // embedded matcher cannot serve a transaction data request alongside a second credential.
            registryManager.registerCredentials(
                AndroidOpenId4VpRegistry(
                    id = registryId,
                    credentials = openId4Vp.credentials,
                    matcher = applicationContext.assets.open(OPENID4VP_MATCHER_ASSET).use { it.readBytes() },
                )
            )
            registryManager.registerCredentials(
                AndroidAnnexCRegistry(
                    id = "$registryId-annex-c",
                    credentials = encodeAnnexCCredentialDatabase(records),
                    matcher = applicationContext.assets.open(ANNEX_C_MATCHER_ASSET).use { it.readBytes() },
                )
            )
            registrationAvailable = true
            MobileWalletCredentialRegistrationResult(true, entries.size)
        }.getOrElse { error ->
            registrationAvailable = false
            MobileWalletCredentialRegistrationResult(
                available = false,
                registeredEntryCount = 0,
                reason = error.message ?: error::class.simpleName ?: "Credential registration failed",
            )
        }
    }

    /**
     * Advertises OpenID4VCI issuance capability to Credential Manager.
     *
     * Independent of [replace]: creation options describe what the wallet can receive, not which
     * credentials it currently holds.
     */
    @OptIn(ExperimentalDigitalCredentialApi::class)
    override suspend fun registerCreationOptions(): MobileWalletCredentialRegistrationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            creationRegistrationAvailable = false
            return MobileWalletCredentialRegistrationResult(
                available = false,
                registeredEntryCount = 0,
                reason = "Credential Manager requires API 23",
            )
        }
        return runCatching {
            val matcher = applicationContext.assets.open(OPENID4VCI_MATCHER_ASSET).use { it.readBytes() }
            registryManager.registerCreationOptions(
                object : RegisterCreationOptionsRequest(
                    creationOptions = encodeOpenId4VciCreationOptions(
                        entryId = OPENID4VCI_CREATION_REGISTRY_ID,
                        applicationName = applicationDisplayName(),
                        subtitle = "Save a credential to this wallet",
                        explainer = "Save a credential to this wallet.",
                        icon = iconPng,
                    ),
                    matcher = matcher,
                    type = DigitalCredential.TYPE_DIGITAL_CREDENTIAL,
                    id = OPENID4VCI_CREATION_REGISTRY_ID,
                ) {},
            )
            creationRegistrationAvailable = true
            MobileWalletCredentialRegistrationResult(available = true, registeredEntryCount = 1)
        }.getOrElse { error ->
            creationRegistrationAvailable = false
            MobileWalletCredentialRegistrationResult(
                available = false,
                registeredEntryCount = 0,
                reason = error.message ?: error::class.simpleName ?: "Creation-option registration failed",
            )
        }
    }

    private fun applicationDisplayName(): String =
        applicationContext.packageManager
            .getApplicationLabel(applicationContext.applicationInfo)
            .toString()
            .ifBlank { applicationContext.packageName }

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
                add(JsonPrimitive(MobileWalletDigitalCredentialProtocols.OPENID4VCI_V1))
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

    private fun Bitmap.toPngBytes(): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

    private fun loadApplicationIcon(context: Context): Bitmap {
        val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
        return drawable.toBitmap(maxEdgePx = REGISTRY_ICON_MAX_EDGE_PX)
    }

    private fun Drawable.toBitmap(maxEdgePx: Int): Bitmap {
        val source = when {
            this is BitmapDrawable && bitmap != null && !bitmap.isRecycled -> bitmap
            else -> {
                val width = intrinsicWidth.takeIf { it > 0 } ?: maxEdgePx
                val height = intrinsicHeight.takeIf { it > 0 } ?: maxEdgePx
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    setBounds(0, 0, canvas.width, canvas.height)
                    draw(canvas)
                }
            }
        }
        val longestEdge = maxOf(source.width, source.height)
        if (longestEdge <= maxEdgePx) return source
        val scale = maxEdgePx.toFloat() / longestEdge.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    internal fun MobileWalletCredentialRegistryRecord.toAndroidEntry(): DigitalCredentialEntry {
        val display = setOf(
            VerificationEntryDisplayProperties(
                displayName,
                type,
                icon,
                null,
                null,
            )
        )
        return when (format) {
            MobileWalletDigitalCredentialFormat.MDOC -> MdocEntry(
                docType = type,
                fields = fields.map { field ->
                    require(field.path.size == 2) { "mdoc registry fields require namespace and element paths" }
                    MdocField(
                        namespace = field.path[0],
                        identifier = field.path[1],
                        fieldValue = field.valueJson.toPlatformValue(),
                        fieldDisplayPropertySet = setOf(
                            VerificationFieldDisplayProperties(field.path[1], field.valueJson.displayValue())
                        ),
                    )
                },
                entryDisplayPropertySet = display,
                id = registryEntryId,
            )

            MobileWalletDigitalCredentialFormat.SD_JWT_VC -> SdJwtEntry(
                verifiableCredentialType = type,
                claims = fields.map { field ->
                    SdJwtClaim(
                        path = field.path,
                        value = field.valueJson.toPlatformValue(),
                        fieldDisplayPropertySet = setOf(
                            VerificationFieldDisplayProperties(field.path.last(), field.valueJson.displayValue())
                        ),
                        isSelectivelyDisclosable = field.selectivelyDisclosable,
                    )
                },
                entryDisplayPropertySet = display,
                id = registryEntryId,
            )
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal fun encodeAnnexCCredentialDatabase(
        records: List<MobileWalletCredentialRegistryRecord>,
    ): ByteArray = coseCompliantCbor.encodeToByteArray(
        AndroidAnnexCCredentialDatabase(
            protocols = listOf(MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C),
            credentials = records
                .filter { it.format == MobileWalletDigitalCredentialFormat.MDOC }
                .map { record ->
                    AndroidAnnexCCredential(
                        title = record.displayName,
                        subtitle = record.type,
                        bitmap = iconPng,
                        mdoc = AndroidAnnexCMdoc(
                            documentId = record.registryEntryId,
                            docType = record.type,
                            namespaces = record.fields
                                .onEach { require(it.path.size == 2) { "mdoc registry fields require namespace and element paths" } }
                                .groupBy { it.path[0] }
                                .mapValues { (_, fields) ->
                                    fields.associate { field ->
                                        val rawValue = field.valueJson.matcherValue()
                                        field.path[1] to listOf(
                                            field.path[1],
                                            field.valueJson.displayValue(),
                                            rawValue.takeIf { it.length < MAX_MATCHER_VALUE_LENGTH }.orEmpty(),
                                        )
                                    }
                                },
                        ),
                    )
                },
        )
    )

    private fun String.toPlatformValue(): Any = Json.parseToJsonElement(this).toPlatformValue()

    private fun JsonElement.toPlatformValue(): Any = when (this) {
        JsonNull -> ""
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> requireNotNull(booleanOrNull)
            longOrNull != null -> requireNotNull(longOrNull)
            doubleOrNull != null -> requireNotNull(doubleOrNull)
            else -> content
        }
        is JsonArray -> map { it.toPlatformValue() }
        is JsonObject -> mapValues { it.value.toPlatformValue() }
    }

    private fun String.displayValue(): String =
        (Json.parseToJsonElement(this) as? JsonPrimitive)?.content ?: this

    private fun String.matcherValue(): String = when (val value = Json.parseToJsonElement(this)) {
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

    private companion object {
        // Vendored, not a dependency; package-qualified so it cannot collide with another library's
        // copy in the application asset merge. See ANNEX-C-MATCHER.md.
        private const val ANNEX_C_MATCHER_ASSET = "id/walt/wallet2/mobile/identitycredentialmatcher.wasm"
        // Vendored OpenID4VCI creation matcher. See OPENID4VCI-MATCHER.md.
        private const val OPENID4VCI_MATCHER_ASSET = "id/walt/wallet2/mobile/issuance.wasm"
        private const val OPENID4VCI_CREATION_REGISTRY_ID = "openid4vci"

        // Vendored, not a dependency. See OPENID4VP-MATCHER.md.
        internal const val OPENID4VP_MATCHER_ASSET = "id/walt/wallet2/mobile/openid4vpmatcher.wasm"
        private const val MAX_MATCHER_VALUE_LENGTH = 128
        /** Credential Manager selector icons are small; keep registry PNG payloads modest. */
        private const val REGISTRY_ICON_MAX_EDGE_PX = 128
        private const val SIGNED_UNSUPPORTED_REASON =
            "The wallet accepts only the unsigned OpenID4VP Digital Credentials protocol"
        private const val MULTISIGNED_UNSUPPORTED_REASON =
            "The wallet accepts only the unsigned OpenID4VP Digital Credentials protocol, " +
                "and does not support JWS JSON Serialization request objects"
    }
}

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

@Serializable
internal data class AndroidAnnexCCredentialDatabase(
    val protocols: List<String>,
    val credentials: List<AndroidAnnexCCredential>,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class AndroidAnnexCCredential(
    val title: String,
    val subtitle: String,
    @ByteString val bitmap: ByteArray,
    val mdoc: AndroidAnnexCMdoc,
)

@Serializable
internal data class AndroidAnnexCMdoc(
    val documentId: String,
    val docType: String,
    val namespaces: Map<String, Map<String, List<String>>>,
)
