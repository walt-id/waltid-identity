@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.digitalcredentials

import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.utils.ShaUtils
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.resolvedSdJwtVcTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Registration adapters select their projection at platform factory wiring; presentation uses full records. */
internal enum class MobileWalletRegistryProjection { Full, MdocIdentity }

internal suspend fun Wallet.registryRecords(
    preferredLocales: List<String>,
    projection: MobileWalletRegistryProjection = MobileWalletRegistryProjection.Full,
): List<RegistryEntry> =
    streamAllCredentials().mapNotNull { stored ->
        if (receiptTrust?.permitsRegistration(stored) == false) return@mapNotNull null
        val credential = stored.credential
        if (projection == MobileWalletRegistryProjection.MdocIdentity && credential !is MdocsCredential) {
            return@mapNotNull null
        }
        val registryEntryId = "dc-${ShaUtils.calculateSha256Base64Url("${id}\u0000${stored.id}").take(32)}"
        if (projection == MobileWalletRegistryProjection.MdocIdentity) {
            // IdentityDocumentServices indexes only identity and type. Do not read metadata,
            // serialize claims, or decode images that the native adapter would discard.
            return@mapNotNull listOf(RegistryEntry(
                registryEntryId = registryEntryId,
                credentialId = stored.id,
                format = DigitalCredentialFormat.MDOC,
                type = (credential as MdocsCredential).docType,
                fields = emptyList(),
                display = RegistryEntryDisplay(""),
            ))
        }
        val metadata = stored.toMetadata()
        val record = when (credential) {
            is MdocsCredential -> {
                val display = MobileWalletRegistryDisplay.resolve(
                    format = DigitalCredentialFormat.MDOC,
                    type = credential.docType,
                    credentialData = credential.credentialData,
                    storedLabel = metadata.label,
                    metadata = stored.metadata,
                    preferredLocales = preferredLocales,
                )
                val cardArt = MobileWalletRegistryIcons.extractCardArt(
                    metadata = stored.metadata,
                    credentialData = credential.credentialData,
                )
                RegistryEntry(
                    registryEntryId = registryEntryId,
                    credentialId = stored.id,
                    format = DigitalCredentialFormat.MDOC,
                    type = credential.docType,
                    fields = credential.credentialData
                        .filterKeys { it != "docType" }
                        .flatMap { (namespace, value) ->
                            value.jsonObject.map { (element, elementValue) ->
                                RegistryField(
                                    path = listOf(namespace, element),
                                    valueJson = Json.encodeToString(JsonElement.serializer(), elementValue),
                                    selectivelyDisclosable = true,
                                )
                            }
                        },
                    display = RegistryEntryDisplay(display.title, display.subtitle, cardArt = cardArt),
                )
            }
            else -> if (metadata.format in setOf("vc+sd-jwt", "dc+sd-jwt", "sd-jwt-vc")) {
                val data = credential.credentialData
                val type = data["vct"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val selectivelyDisclosablePaths = (credential as? SelectivelyDisclosableVerifiableCredential)
                    ?.disclosures
                    .orEmpty()
                    .mapNotNull { disclosure -> disclosure.location?.toRegistryFieldPath() }
                    .toSet()
                val display = MobileWalletRegistryDisplay.resolve(
                    format = DigitalCredentialFormat.SD_JWT_VC,
                    type = type,
                    credentialData = data,
                    storedLabel = metadata.label,
                    metadata = stored.metadata,
                    preferredLocales = preferredLocales,
                )
                val cardArt = MobileWalletRegistryIcons.extractCardArt(
                    metadata = stored.metadata,
                    credentialData = data,
                )
                RegistryEntry(
                    registryEntryId = registryEntryId,
                    credentialId = stored.id,
                    format = DigitalCredentialFormat.SD_JWT_VC,
                    type = type,
                    fields = data
                        .filterKeys { it !in SD_JWT_INFRASTRUCTURE_CLAIMS }
                        .flatMap { (name, value) ->
                            value.flattenRegistryFields(
                                path = listOf(name),
                                selectivelyDisclosablePaths = selectivelyDisclosablePaths,
                            )
                        },
                    display = RegistryEntryDisplay(display.title, display.subtitle, cardArt = cardArt),
                )
            } else null
        }
        record?.let { entry ->
            val types = if (entry.format == DigitalCredentialFormat.SD_JWT_VC) {
                try {
                    stored.resolvedSdJwtVcTypes().ifEmpty { listOf(entry.type) }
                } catch (cause: CancellationException) {
                    throw cause
                } catch (_: IllegalArgumentException) {
                    return@mapNotNull null
                }
            } else listOf(entry.type)
            types.map { entry.copy(type = it) }
        }
    }.toList().flatten()

/**
 * Maps an SD-JWT VC claim path onto the object-key path used by registry fields.
 *
 * [flattenRegistryFields] emits an array as one leaf at the path of its containing key, so a
 * disclosure addressing an array index or wildcard is truncated at the first non-key component
 * to name that same leaf. Dropping such a component instead would shorten the path and silently
 * mark a disclosable claim as non-disclosable.
 */
private fun List<JsonElement>.toRegistryFieldPath(): List<String> =
    takeWhile { it is JsonPrimitive && it.isString }.map { it.jsonPrimitive.content }

private fun JsonElement.flattenRegistryFields(
    path: List<String>,
    selectivelyDisclosablePaths: Set<List<String>>,
): List<RegistryField> = when (this) {
    is JsonObject -> entries.flatMap { (name, value) ->
        value.flattenRegistryFields(path + name, selectivelyDisclosablePaths)
    }
    else -> listOf(
        RegistryField(
            path = path,
            valueJson = Json.encodeToString(JsonElement.serializer(), this),
            selectivelyDisclosable = path in selectivelyDisclosablePaths,
        )
    )
}

/**
 * SD-JWT VC claims that carry no matchable user data and must never reach a platform registry.
 *
 * `sub` is deliberately absent: it is a subject identifier a verifier may query. `_sd` is
 * already removed while resolving disclosures, but the sibling `_sd_alg` hash-algorithm
 * declaration is not, so it has to be excluded here.
 */
private val SD_JWT_INFRASTRUCTURE_CLAIMS = setOf(
    "vct", "vct#integrity", "iss", "iat", "nbf", "exp", "cnf", "status", "_sd", "_sd_alg",
)
