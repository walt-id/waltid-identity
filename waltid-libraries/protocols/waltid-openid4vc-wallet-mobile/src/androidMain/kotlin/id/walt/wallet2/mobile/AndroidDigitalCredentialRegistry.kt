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

/** Android Credential Manager metadata registry adapter. */
public class AndroidDigitalCredentialRegistry(
    context: Context,
) : MobileWalletCredentialRegistry {
    private val applicationContext: Context = context.applicationContext
    private val registryManager: RegistryManager = RegistryManager.create(applicationContext)
    private val icon: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var registrationAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    override val capabilities: MobileWalletDigitalCredentialCapabilities
        get() = MobileWalletDigitalCredentialCapabilities(
            platform = "Android Credential Manager",
            platformAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M,
            minimumOsVersion = "Android 6 (API 23)",
            registrationAvailable = registrationAvailable,
            capabilities = listOf(
                MobileWalletDigitalCredentialCapability(
                    protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
                    credentialFormats = listOf(
                        MobileWalletDigitalCredentialFormat.MDOC,
                        MobileWalletDigitalCredentialFormat.SD_JWT_VC,
                    ),
                    requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.UNSIGNED),
                    responseProtection = listOf(
                        MobileWalletDigitalCredentialResponseProtection.UNENCRYPTED,
                        MobileWalletDigitalCredentialResponseProtection.JWE,
                    ),
                    supported = true,
                ),
                MobileWalletDigitalCredentialCapability(
                    protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED,
                    credentialFormats = listOf(
                        MobileWalletDigitalCredentialFormat.MDOC,
                        MobileWalletDigitalCredentialFormat.SD_JWT_VC,
                    ),
                    requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.SIGNED),
                    responseProtection = listOf(
                        MobileWalletDigitalCredentialResponseProtection.UNENCRYPTED,
                        MobileWalletDigitalCredentialResponseProtection.JWE,
                    ),
                    supported = true,
                ),
                MobileWalletDigitalCredentialCapability(
                    protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_MULTISIGNED,
                    credentialFormats = listOf(
                        MobileWalletDigitalCredentialFormat.MDOC,
                        MobileWalletDigitalCredentialFormat.SD_JWT_VC,
                    ),
                    requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.MULTISIGNED),
                    responseProtection = listOf(
                        MobileWalletDigitalCredentialResponseProtection.UNENCRYPTED,
                        MobileWalletDigitalCredentialResponseProtection.JWE,
                    ),
                    supported = false,
                    unsupportedReason = "The wallet request-object verifier does not yet support JWS JSON Serialization",
                ),
                MobileWalletDigitalCredentialCapability(
                    protocol = MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
                    credentialFormats = listOf(MobileWalletDigitalCredentialFormat.MDOC),
                    requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.READER_AUTHENTICATED),
                    responseProtection = listOf(MobileWalletDigitalCredentialResponseProtection.HPKE),
                    supported = true,
                ),
            ),
        )

    override suspend fun replace(
        registryId: String,
        records: List<MobileWalletCredentialRegistryRecord>,
    ): MobileWalletCredentialRegistrationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            registrationAvailable = false
            return MobileWalletCredentialRegistrationResult(false, 0, "Credential Manager requires API 23")
        }
        val entries = records.map { it.toAndroidEntry() }
        return runCatching {
            registryManager.registerCredentials(OpenId4VpRegistry(entries, registryId))
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
