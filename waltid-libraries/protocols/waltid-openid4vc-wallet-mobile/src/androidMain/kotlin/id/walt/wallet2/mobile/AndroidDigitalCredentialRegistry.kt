package id.walt.wallet2.mobile

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.credentials.registry.digitalcredentials.mdoc.MdocEntry
import androidx.credentials.registry.digitalcredentials.mdoc.MdocField
import androidx.credentials.registry.digitalcredentials.openid4vp.OpenId4VpRegistry
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtClaim
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtEntry
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
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Android Credential Manager metadata registry adapter.
 *
 * @property capabilities Current Android platform and registry availability.
 */
public class AndroidDigitalCredentialRegistry(
    context: Context,
) : MobileWalletCredentialRegistry {
    private val applicationContext: Context = context.applicationContext
    private val registryManager: RegistryManager = RegistryManager.create(applicationContext)
    private val projectionStore = AndroidCredentialRegistryProjectionStore(applicationContext)
    private val icon: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var registrationAvailable: Boolean = false

    override val capabilities: MobileWalletDigitalCredentialCapabilities
        get() {
            val platformAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            val runtimeAvailable = platformAvailable && registrationAvailable
            val unavailableReason = when {
                !platformAvailable -> "Credential Manager requires Android 6 (API 23)"
                !registrationAvailable -> "Credential registration has not completed successfully"
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
                        responseProtection = listOf(MobileWalletDigitalCredentialResponseProtection.UNENCRYPTED),
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
                ),
            )
        }

    override suspend fun replace(
        registryId: String,
        records: List<MobileWalletCredentialRegistryRecord>,
    ): MobileWalletCredentialRegistrationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            registrationAvailable = false
            return MobileWalletCredentialRegistrationResult(false, 0, "Credential Manager requires API 23")
        }
        val projectionRecords = records.map { it.toProjectionRecord() }
        return replaceProjection(registryId, projectionRecords, persistProjection = true)
    }

    private suspend fun replaceProjection(
        registryId: String,
        records: List<AndroidCredentialRegistryProjectionRecord>,
        persistProjection: Boolean,
    ): MobileWalletCredentialRegistrationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            registrationAvailable = false
            return MobileWalletCredentialRegistrationResult(false, 0, "Credential Manager requires API 23")
        }
        if (persistProjection) {
            runCatching { projectionStore.replace(registryId, records) }.onFailure { error ->
                registrationAvailable = false
                return MobileWalletCredentialRegistrationResult(
                    available = false,
                    registeredEntryCount = 0,
                    reason = error.message ?: "Credential registry projection could not be persisted",
                )
            }
        }
        val entries = records.map { it.toAndroidEntry() }
        return runCatching {
            // Registering only the unsigned protocol makes Credential Manager ignore signed and
            // multisigned requests rather than route them here to be rejected. The library default
            // advertises all three.
            registryManager.registerCredentials(
                OpenId4VpRegistry(
                    credentialEntries = entries,
                    id = registryId,
                    supportedProtocols = listOf(OpenId4VpRegistry.PROTOCOL_OPENID4VP_1_0_UNSIGNED),
                )
            )
            registryManager.registerCredentials(
                AndroidAnnexCRegistry(
                    id = "$registryId-annex-c",
                    credentials = encodeAnnexCProjectionDatabase(records),
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
     * Replays encrypted desired state after a process start without opening wallet credentials.
     * Android hosts should invoke this from application startup, before presenting the wallet UI.
     */
    public suspend fun restorePersistedRegistrations(): List<MobileWalletCredentialRegistrationResult> {
        val projections = runCatching { projectionStore.readAll() }.getOrElse { error ->
            registrationAvailable = false
            return listOf(
                MobileWalletCredentialRegistrationResult(
                    available = false,
                    registeredEntryCount = 0,
                    reason = error.message ?: "Credential registry projection could not be restored",
                )
            )
        }
        return projections.map { projection ->
            replaceProjection(
                registryId = projection.registryId,
                records = projection.records,
                persistProjection = false,
            )
        }
    }

    private fun MobileWalletCredentialRegistryRecord.toProjectionRecord(): AndroidCredentialRegistryProjectionRecord =
        AndroidCredentialRegistryProjectionRecord(
            registryEntryId = registryEntryId,
            format = format,
            type = type,
            fields = fields.map { field ->
                AndroidCredentialRegistryProjectionField(
                    path = field.path,
                    valueJson = field.valueJson,
                    selectivelyDisclosable = field.selectivelyDisclosable,
                )
            },
            displayName = displayName,
        )

    internal fun MobileWalletCredentialRegistryRecord.toAndroidEntry(): DigitalCredentialEntry =
        toProjectionRecord().toAndroidEntry()

    internal fun AndroidCredentialRegistryProjectionRecord.toAndroidEntry(): DigitalCredentialEntry {
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
    internal fun encodeAnnexCProjectionDatabase(
        records: List<AndroidCredentialRegistryProjectionRecord>,
    ): ByteArray = coseCompliantCbor.encodeToByteArray(
        AndroidAnnexCCredentialDatabase(
            protocols = listOf(MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C),
            credentials = records
                .filter { it.format == MobileWalletDigitalCredentialFormat.MDOC }
                .map { record ->
                    AndroidAnnexCCredential(
                        title = record.displayName,
                        subtitle = record.type,
                        bitmap = byteArrayOf(),
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

    internal fun encodeAnnexCCredentialDatabase(
        records: List<MobileWalletCredentialRegistryRecord>,
    ): ByteArray = encodeAnnexCProjectionDatabase(records.map { it.toProjectionRecord() })

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
        private const val ANNEX_C_MATCHER_ASSET = "identitycredentialmatcher.wasm"
        private const val MAX_MATCHER_VALUE_LENGTH = 128
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
