package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.MdocsCredential
import id.walt.did.dids.DidService
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.crypto2.keys.KeyUsage
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import id.walt.wallet2.data.*
import id.waltid.openid4vci.wallet.credential.CredentialIssuanceTarget
import id.waltid.openid4vci.wallet.credential.CredentialRequestBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** One requested instance. Key references are resolved by the wallet, never generated implicitly. */
@Serializable
data class CredentialHolderBinding(
    val keyId: String? = null,
    val did: String? = null,
    val key: DirectSerializedKey? = null,
)

/** One configuration/dataset request; holderBindings determines its requested instance count. */
@Serializable
data class WalletCredentialSelection(
    val credentialConfigurationId: String,
    val credentialIdentifier: String? = null,
    val holderBindings: List<CredentialHolderBinding> = listOf(CredentialHolderBinding()),
) {
    init {
        require(credentialConfigurationId.isNotBlank())
        require(credentialIdentifier == null || credentialIdentifier.isNotBlank())
        require(holderBindings.isNotEmpty()) { "At least one holder binding is required" }
    }
}

internal data class ResolvedCredentialHolderBinding(
    val material: WalletKeyStoreEntry,
    val did: String?,
)

internal data class ResolvedWalletCredentialSelection(
    val selection: WalletCredentialSelection,
    val bindings: List<ResolvedCredentialHolderBinding>,
)

internal suspend fun Wallet.resolveCredentialSelections(
    selections: List<WalletCredentialSelection>?,
    offeredConfigurationIds: List<String>,
    metadata: CredentialIssuerMetadata,
    defaultKey: WalletKeyStoreEntry,
    defaultDid: String?,
): List<ResolvedWalletCredentialSelection> {
    val selected = selections ?: offeredConfigurationIds.map { WalletCredentialSelection(it) }
    require(selected.isNotEmpty()) { "At least one credential must be selected" }
    require(selected.map { it.credentialConfigurationId to it.credentialIdentifier }.distinct().size == selected.size) {
        "Select each configuration/dataset once; use holderBindings for multiple instances"
    }
    require(selected.groupBy { it.credentialConfigurationId }.values.none { group ->
        group.size > 1 && group.any { it.credentialIdentifier == null }
    }) { "Do not combine a whole configuration selection with its individual dataset identifiers" }
    return selected.map { selection ->
        require(selection.credentialConfigurationId in offeredConfigurationIds) { "Selected credential is not offered" }
        val configuration = requireNotNull(metadata.credentialConfigurationsSupported[selection.credentialConfigurationId])
        CredentialRequestBuilder.validateBatchSize(metadata, selection.holderBindings.size)
        require(selection.holderBindings.size == 1 || supportedJwtProofAlgorithms(configuration.proofTypesSupported) != null) {
            "Multiple instances require an advertised JWT proof type"
        }
        val bindings = selection.holderBindings.map { binding ->
            val material = binding.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
                ?: binding.keyId?.let { requireNotNull(resolveKeyMaterial(it, setOf(KeyUsage.SIGN))) { "Holder key '$it' is unavailable" } }
                ?: defaultKey
            supportedJwtProofAlgorithms(configuration.proofTypesSupported)?.let { algorithms ->
                if (material.crypto2Key != null) material.crypto2Key.selectJwsAlgorithm(algorithms)
                else require(requireNotNull(material.legacyKey).keyType.jwsAlg in algorithms) {
                    "Issuer does not support the selected holder key's proof algorithm"
                }
            }
            ResolvedCredentialHolderBinding(material, resolveProofDid(
                binding.did ?: defaultDid.takeIf { material.keyId == defaultKey.keyId }, material, configuration))
        }
        ResolvedWalletCredentialSelection(selection, bindings)
    }
}

internal fun grantedCredentialSelections(
    metadata: CredentialIssuerMetadata,
    selections: List<ResolvedWalletCredentialSelection>,
    authorizationDetails: List<AuthorizationDetail>?,
    scope: String?,
): List<Pair<CredentialIssuanceTarget, ResolvedWalletCredentialSelection>> {
    val targets = CredentialRequestBuilder.resolveTargets(metadata,
        selections.map { it.selection.credentialConfigurationId }.distinct(), authorizationDetails, scope)
    return selections.flatMap { selected ->
        val matches = targets.filter {
            it.credentialConfigurationId == selected.selection.credentialConfigurationId &&
                (selected.selection.credentialIdentifier == null || it.credentialIdentifier == selected.selection.credentialIdentifier)
        }
        require(selected.selection.credentialIdentifier == null || matches.isNotEmpty()) { "Selected credential identifier was not granted" }
        matches.map { it to selected }
    }.also { require(it.isNotEmpty()) { "Token grants none of the selected credentials" } }
}

/**
 * Validate the entire response before the first write. Issuers may return fewer instances or reorder
 * them. Match by cryptographic holder binding, not by array position, then persist each independently.
 */
internal suspend fun Wallet.prepareIssuedCredentials(
    rawCredentials: List<String>,
    bindings: List<ResolvedCredentialHolderBinding>,
    label: String?,
    metadata: JsonObject?,
    proofRequired: Boolean,
    holderBindingKnown: Boolean = true,
): List<StoredCredential> {
    require(rawCredentials.isNotEmpty()) { "Credential response contained no credentials" }
    require(rawCredentials.size <= bindings.size.coerceAtLeast(1)) { "Issuer returned more credentials than requested" }
    require(bindings.isNotEmpty() || !proofRequired) { "Holder keys are required for proof-bound issuance" }
    val remaining = bindings.toMutableList()
    val parsedCredentials = rawCredentials.map { CredentialParser.detectAndParse(it).second }
    require(parsedCredentials.map { it.format }.distinct().size == 1) {
        "Credentials in one response must share the same format"
    }
    return parsedCredentials.map { parsed ->
        require(holderBindingKnown || parsed !is MdocsCredential) {
            "Exact issuance holder-key material is required when storing an mdoc"
        }
        if (bindings.isEmpty()) {
            require(parsed !is MdocsCredential) { "Exact issuance holder-key material is required when storing an mdoc" }
            return@map StoredCredential(Uuid.random().toString(), parsed, label, Clock.System.now(), metadata)
        }
        // A subject DID is not proof of holder binding in a proofless W3C issuance.
        val identities = parsed.holderKeyThumbprints(includeSubjectDid = proofRequired)
        val match = if (identities.isEmpty()) {
            require(!proofRequired) { "Issued credential contains no verifiable holder binding" }
            remaining.first()
        } else {
            remaining.firstOrNull { it.material.publicKeyThumbprint() in identities }
                ?: error("Issued credential is bound to an unrequested holder key")
        }
        remaining.remove(match)
        val stored = StoredCredential(Uuid.random().toString(), parsed, label, Clock.System.now(), metadata)
        if (identities.isEmpty()) stored else withVerifiedIssuanceHolderKeyBinding(stored, match.material)
    }
}

/** Choose a DID only when its verification method belongs to the selected key. */
private suspend fun Wallet.resolveProofDid(did: String?, material: WalletKeyStoreEntry, configuration: CredentialConfiguration): String? {
    if (supportedJwtProofAlgorithms(configuration.proofTypesSupported) == null) return null
    val methods = configuration.cryptographicBindingMethodsSupported.orEmpty()
    val permitsJwk = methods.isEmpty() || methods.any { it is CryptographicBindingMethod.Jwk || it is CryptographicBindingMethod.CoseKey }
    val method = did?.removePrefix("did:")?.substringBefore(':')
    val permitsDid = method != null && methods.any { it is CryptographicBindingMethod.Did && it.method == method }
    if (did != null && permitsDid) {
        val baseDid = did.substringBefore('#')
        val document = didStore?.getDid(baseDid)?.document
        if (document != null) {
            val publicJwk = material.crypto2Key?.let {
                val encoded = it.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(it.spec)
                Json.parseToJsonElement(encoded.data.toByteArray().decodeToString()).jsonObject
            } ?: material.legacyKey!!.getPublicKey().exportJWKObject()
            val fields = when (publicJwk["kty"]?.jsonPrimitive?.content) {
                "EC" -> listOf("kty", "crv", "x", "y")
                "OKP" -> listOf("kty", "crv", "x")
                "RSA" -> listOf("kty", "n", "e")
                else -> emptyList()
            }
            document["verificationMethod"]?.jsonArray?.forEach { entry ->
                val vm = entry.jsonObject
                val id = vm["id"]?.jsonPrimitive?.content ?: return@forEach
                val key = vm["publicKeyJwk"]?.jsonObject ?: return@forEach
                if (('#' !in did || id == did) && fields.isNotEmpty() && fields.all { key[it] != null && key[it] == publicJwk[it] }) return id
            }
        } else if (staticDid?.substringBefore('#') == baseDid && staticKey?.getPublicKey()?.getThumbprint() == material.jwkThumbprint()) {
            return did
        } else {
            val keys = DidService.resolveToKeys(did).getOrNull().orEmpty()
            if (keys.any { it.getPublicKey().getThumbprint() == material.jwkThumbprint() }) {
                return DidService.resolveAuthenticationMethodId(did, material.keyId)
            }
        }
    }
    require(permitsJwk) { "Issuer requires a DID bound to the selected holder key" }
    return null
}
